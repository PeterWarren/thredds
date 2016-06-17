/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/


package dap4.dap4lib.serial;

import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.DapDump;
import dap4.core.util.DapException;
import dap4.core.util.DapSort;
import dap4.core.util.DapUtil;
import dap4.dap4lib.ChecksumMode;
import dap4.dap4lib.Dap4Util;
import dap4.dap4lib.RequestMode;

import java.nio.ByteBuffer;
import java.util.List;

public class D4DataCompiler implements DataCompiler
{
    static public boolean DEBUG = false;

    //////////////////////////////////////////////////
    // Constants

    static final public int COUNTSIZE = 8; // databuffer as specified by the DAP4 spec

    static String LBRACE = "{";
    static String RBRACE = "}";


    //////////////////////////////////////////////////
    // Instance variables

    protected DataDataset datadataset = null;

    protected DapDataset dataset = null;

    // Make compile arguments global
    protected ByteBuffer databuffer;

    protected ChecksumMode checksummode = null;

    protected DSP dsp;

    protected DAPDataFactory factory = null;

    //////////////////////////////////////////////////
    //Constructor(s)

    /**
     * Constructor
     *
     * @param dsp          the D4DSP
     * @param checksummode
     * @param databuffer   the source of serialized databuffer
     * @param factory      for producing data nodes
     */

    public D4DataCompiler(DSP dsp, ChecksumMode checksummode,
                        ByteBuffer databuffer, DAPDataFactory factory)
            throws DapException
    {
        this.dsp = dsp;
        this.dataset = this.dsp.getDMR();
        this.databuffer = databuffer;
        this.checksummode = checksummode;
        this.factory = factory;
    }

    //////////////////////////////////////////////////
    // DataCompiler API

    /**
     * The goal here is to process the serialized
     * databuffer and locate variable-specific positions
     * in the serialized databuffer. For each DAP4 variable,
     * D4 objects are created and linked together.
     */
    @Override
    public void
    compile()
            throws DapException
    {
        assert (this.dataset != null && this.databuffer != null);
        if(DEBUG) {
            DapDump.dumpbytes(this.databuffer, false);
        }
        this.datadataset = factory.newDataset(this.dsp, this.dataset, null);
        this.dsp.setDataset(this.datadataset);

        // iterate over the variables represented in the databuffer
        for(DapVariable vv : this.dataset.getTopVariables()) {
            DataVariable array = compileVar(vv);
            this.datadataset.addVariable(array);
        }
    }

    protected DataVariable
    compileVar(DapVariable dapvar)
            throws DapException
    {
        DataVariable array = null;
        boolean isscalar = dapvar.getRank() == 0;
        if(dapvar.getSort() == DapSort.ATOMICVARIABLE) {
            array = compileAtomicVar((DapAtomicVariable)dapvar);
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
        if(dapvar.isTopLevel()) {
            // extract the checksum from databuffer src,
            // attach to the array, and make into an attribute
            byte[] checksum = getChecksum(databuffer);
            dapvar.setChecksum(checksum);
        }
        return array;
    }

    protected DataAtomic
    compileAtomicVar(DapAtomicVariable atomvar)
            throws DapException
    {
        DapType daptype = atomvar.getBaseType();
        D4Data.D4DataAtomic data = (D4Data.D4DataAtomic)factory.newAtomicVariable(this.dsp, atomvar, databuffer.position());
        long total = 0;
        long dimproduct = data.getCount();
        if(!daptype.isEnumType() && !daptype.isFixedSize()) {
            // this is a string, url, or opaque
            int[] positions = new int[(int) dimproduct];
            int savepos = databuffer.position();
            // Walk the bytestring and return the instance count (in databuffer)
            total = walkByteStrings(positions, databuffer);
            databuffer.position(savepos);// leave position unchanged
            data.setByteStringOffsets(total, positions);
        } else {
            total = dimproduct * daptype.getSize();
        }
        skip(databuffer, (int) total);
        return data;
    }

    /**
     * Compile a structure array.
     *
     * @param dapvar the template
     * @return A DataCompoundArray for the databuffer for this struct.
     * @throws DapException
     */
    DataCompoundArray
    compileStructureArray(DapVariable dapvar)
            throws DapException
    {
        DataCompoundArray structarray
                = factory.newCompoundArray(this.dsp, dapvar, null);
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
     * @throws DapException
     */
    DataStructure
    compileStructure(DapStructure dapstruct, DataCompoundArray array, int index)
            throws DapException
    {
        DataStructure d4ds = factory.newStructure(dsp, dapstruct, array, index);
        List<DapVariable> dfields = dapstruct.getFields();
        for(int m = 0; m < dfields.size(); m++) {
            DapVariable dfield = dfields.get(m);
            DataVariable dvfield = compileVar(dfield);
            d4ds.addField(m, dvfield);
        }
        return d4ds;
    }

    /**
     * Compile a sequence array.
     *
     * @param dapvar the template
     * @return A DataCompoundArray for the databuffer for this sequence.
     * @throws DapException
     */
    DataCompoundArray
    compileSequenceArray(DapVariable dapvar)
            throws DapException
    {
        DapSequence dapseq = (DapSequence) dapvar;
        DataCompoundArray seqarray
                = factory.newCompoundArray(this.dsp, dapseq, null);
        long dimproduct = seqarray.getCount();
        for(int i = 0; i < dimproduct; i++) {
            DataSequence dseq = compileSequence(dapseq, seqarray, i);
            seqarray.addElement(dseq);
        }
        return seqarray;
    }

    /**
     * Compile a sequence from a set of records.
     *
     * @param dapseq The template for this sequence
     * @param array  the parent compound array
     * @param index  within the parent compound array
     * @return A DataSequence for the records for this sequence.
     * @throws DapException
     */
    DataSequence
    compileSequence(DapSequence dapseq, DataCompoundArray array, int index)
            throws DapException
    {
        List<DapVariable> dfields = dapseq.getFields();
        // Get the count of the number of records
        long nrecs = getCount(databuffer);
        DataSequence seq = factory.newSequence(this.dsp, dapseq, array, index);
        for(int r = 0; r < nrecs; r++) {
            DataRecord rec = factory.newRecord(this.dsp, dapseq, seq, r);
            for(int m = 0; m < dfields.size(); m++) {
                DapVariable dfield = dfields.get(m);
                DataVariable dvfield = compileVar(dfield);
                rec.addField(m, dvfield);
            }
            seq.addRecord(rec);
        }
        return seq;
    }

    //////////////////////////////////////////////////
    // Utilities

    protected byte[]
    getChecksum(ByteBuffer data)
            throws DapException
    {
        if(!ChecksumMode.enabled(RequestMode.DAP, checksummode)) return null;
        if(data.remaining() < DapUtil.CHECKSUMSIZE)
            throw new DapException("Short serialization: missing checksum");
        byte[] checksum = new byte[DapUtil.CHECKSUMSIZE];
        data.get(checksum);
        return checksum;
    }

    static protected void
    skip(ByteBuffer data, int count)
    {
        data.position(data.position() + count);
    }

    static protected int
    getCount(ByteBuffer data)
    {
        long count = data.getLong();
        count = (count & 0xFFFFFFFF);
        return (int) count;
    }

    /**
     * Compute the size in databuffer of the serialized form
     *
     * @param daptype
     * @return type's serialized form size
     */
    static protected int
    computeTypeSize(DapType daptype)
    {
        TypeSort atype = daptype.getAtomicType();
        return Dap4Util.daptypeSize(atype);
    }

    static protected long
    walkByteStrings(int[] positions, ByteBuffer databuffer)
    {
        int count = positions.length;
        long total = 0;
        int savepos = databuffer.position();
        // Walk each bytestring
        for(int i = 0; i < count; i++) {
            int pos = databuffer.position();
            positions[i] = pos;
            int size = getCount(databuffer);
            total += COUNTSIZE;
            total += size;
            skip(databuffer, size);
        }
        databuffer.position(savepos);// leave position unchanged
        return total;
    }

}
