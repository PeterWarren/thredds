/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/


package dap4.dap4lib.netcdf;

import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.DapException;
import dap4.core.util.DapSort;

import java.util.List;

import static dap4.dap4lib.netcdf.Nc4Notes.Notes;
import static dap4.dap4lib.netcdf.Nc4Data.*;

public class Nc4DataCompiler implements DataCompiler
{
    static public boolean DEBUG = false;

    //////////////////////////////////////////////////
    // Constants

    static final public int COUNTSIZE = 8; // databuffer as specified by the DAP4 spec

    static String LBRACE = "{";
    static String RBRACE = "}";

    //////////////////////////////////////////////////
    // Instance variables

    protected DataDataset dataset = null;

    protected DapDataset dmr = null;

    protected DapNetcdf nc4 = null;

    protected DSP dsp = null;

    protected DAPDataFactory factory = null;

    //////////////////////////////////////////////////
    //Constructor(s)

    /**
     * Constructor
     *
     * @param dsp     the Nc4DSP
     * @param nc4     the source of data
     * @param factory for producing data nodes
     */

    public Nc4DataCompiler(DSP dsp, DapNetcdf nc4, DAPDataFactory factory)
            throws DapException
    {
        this.dsp = dsp;
        this.dmr = this.dsp.getDMR();
        this.nc4 = nc4;
        this.factory = factory;
    }

    //////////////////////////////////////////////////
    // DataCompiler API

    /**
     * The goal here is to process the netcdf-4 file via the JNA api
     * For each DAP4 variable, Nc4 objects are created and linked together.
     */
    @Override
    public void
    compile()
            throws DataException
    {
        assert (this.dmr != null && this.nc4 != null);
        this.dataset = factory.newDataset(this.dsp, this.dmr, nc4);
        this.dsp.setDataset(this.dataset);

        // iterate over the top-level variables
        for(DapVariable vv : this.dmr.getTopVariables()) {
            DataVariable array = compileVar(vv, null);
            this.dataset.addVariable(array);
        }
    }

    protected DataVariable
    compileVar(DapVariable dapvar, DapStructure parent)
            throws DataException
    {
        DataVariable array = null;
        boolean isscalar = dapvar.getRank() == 0;
        if(dapvar.getSort() == DapSort.ATOMICVARIABLE) {
            array = compileAtomicVar((DapAtomicVariable) dapvar);
        } else if(dapvar.getSort() == DapSort.STRUCTURE) {
            if(isscalar)
                array = compileStructure((DapStructure) dapvar, null, 0);
            else
                array = compileStructureArray(dapvar);
        } else if(dapvar.getSort() == DapSort.SEQUENCE) {
            if(isscalar)
                array = compileSequence((DapSequence) dapvar, null, 0);
            else
                array = compileSequenceArray(dapvar);
        }
        return array;
    }

    protected DataAtomic
    compileAtomicVar(DapAtomicVariable atomvar)
            throws DataException
    {
        DapType daptype = atomvar.getBaseType();
        Notes notes = (Notes)atomvar.annotation(); // Get Notes for this node
        Nc4Data.Nc4DataAtomic data = (Nc4Data.Nc4DataAtomic) factory.newAtomicVariable(this.dsp, atomvar, notes);
        return data;
    }

    /**
     * Compile a structure array.
     *
     * @param dapvar the template
     * @return A DataCompoundArray for the databuffer for this struct.
     * @throws DataException
     */
    DataCompoundArray
    compileStructureArray(DapVariable dapvar)
            throws DataException
    {
        Notes notes = (Notes)dapvar.annotation(); // Get Notes for this node
        Nc4DataCompoundArray structarray = (Nc4DataCompoundArray) factory.newCompoundArray(this.dsp, dapvar, notes);
        DapStructure struct = (DapStructure) dapvar;
        long dimproduct = structarray.getCount();
        for(int i = 0; i < dimproduct; i++) {
            DataStructure instance = compileStructure(struct, structarray, i);
            structarray.addElement(instance);
        }
        return structarray;
    }

    /**
     * Compile a structure instance.
     *
     * @param dapstruct The template
     * @return A DataStructure for the databuffer for this struct.
     * @throws DataException
     */
    DataStructure
    compileStructure(DapStructure parent, DataCompoundArray array, int index)
            throws DataException
    {
        DataStructure Nc4ds = factory.newStructure(dsp, parent, array, index);
        List<DapVariable> dfields = parent.getFields();
        for(int m = 0; m < dfields.size(); m++) {
            DapVariable dfield = dfields.get(m);
            DataVariable dvfield = compileVar(dfield, parent);
            Nc4ds.addField(m, dvfield);
        }
        return Nc4ds;
    }

    /**
     * Compile a vlen into a sequence array.
     *
     * @param dapvar the template
     * @return A DataCompoundArray for the databuffer for this sequence.
     * @throws DataException
     */
    DataCompoundArray
    compileSequenceArray(DapVariable dapvar)
            throws DataException
    {
        Notes notes = (Notes)dapvar.annotation(); // Get Notes for this node
        DapSequence dapseq = (DapSequence) dapvar;
        Nc4DataCompoundArray seqarray = (Nc4DataCompoundArray) factory.newCompoundArray(this.dsp, dapseq, notes);
        long dimproduct = seqarray.getCount();
        for(int i = 0; i < dimproduct; i++) {
            DataSequence dseq = compileSequence(dapseq, seqarray, i);
            seqarray.addElement(dseq);
        }
        return seqarray;
    }

    /**
     * Compile a vlen into a set of records.
     *
     * @param dapseq The template for this sequence
     * @param array  the parent compound array
     * @param index  within the parent compound array
     * @return A DataSequence for the records for this sequence.
     * @throws DataException
     */
    DataSequence
    compileSequence(DapSequence dapseq, DataCompoundArray array, int index)
            throws DataException
    {
        List<DapVariable> dfields = dapseq.getFields();
        // Get the count of the number of records
        long nrecs = getCount(array, index);
        DataSequence seq = factory.newSequence(this.dsp, dapseq, array, index);
        for(int r = 0; r < nrecs; r++) {
            DataRecord rec = factory.newRecord(this.dsp, dapseq, seq, r);
            for(int m = 0; m < dfields.size(); m++) {
                DapVariable dfield = dfields.get(m);
                DataVariable dvfield = compileVar(dfield,dapseq);
                rec.addField(m, dvfield);
            }
            seq.addRecord(rec);
        }
        return seq;
    }

    //////////////////////////////////////////////////
    // Utilities

    protected int
    getCount(DataCompoundArray array, int index)
            throws DataException
    {
        int ret;
        Notes notes = (Notes)array.annotation(); // Get Notes for this node
        SizeTByReference indexp = new SizeTByReference(new SizeT(index));
        DapNetcdf.Vlen_t[] vlenp = new DapNetcdf.Vlen_t[1];
        readcheck(nc4,ret = this.nc4.nc_get_var1(notes.gid, notes.id, indexp, vlenp));
        return vlenp[0].len;
    }

}
