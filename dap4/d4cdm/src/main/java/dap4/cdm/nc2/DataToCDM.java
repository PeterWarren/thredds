/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/


package dap4.cdm.nc2;

import dap4.cdm.NodeMap;
import dap4.core.data.DSP;
import dap4.core.data.DataCursor;
import dap4.core.dmr.DapDataset;
import dap4.core.dmr.DapVariable;
import dap4.core.util.DapException;
import dap4.core.util.DapUtil;
import dap4.core.util.Escape;
import ucar.ma2.Array;
import ucar.nc2.Attribute;
import ucar.nc2.Group;
import ucar.nc2.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create a set of CDM ucar.ma2.array objects that wrap a DSP.
 */

public class DataToCDM
{
    static public boolean DEBUG = false;

    //////////////////////////////////////////////////
    // Constants

    static protected final int COUNTSIZE = 8; // databuffer as specified by the DAP4 spec

    static protected final String LBRACE = "{";
    static protected final String RBRACE = "}";

    //////////////////////////////////////////////////
    // Instance variables

    protected DapNetcdfFile ncfile = null;
    protected DSP dsp = null;
    protected DapDataset dmr = null;
    protected Group cdmroot = null;
    protected Map<Variable, Array> arraymap = null;
    protected NodeMap nodemap = null;

    //////////////////////////////////////////////////
    //Constructor(s)

    /**
     * Constructor
     *
     * @param ncfile the target NetcdfDataset
     * @param dsp    the compiled D4 databuffer
     */

    public DataToCDM(DapNetcdfFile ncfile, DSP dsp, NodeMap nodemap)
            throws DapException
    {
        this.ncfile = ncfile;
        this.dsp = dsp;
        this.dmr = dsp.getDMR();
        this.nodemap = nodemap;
        arraymap = new HashMap<Variable, Array>();
    }

    //////////////////////////////////////////////////
    // Compile DataCursor objects to ucar.ma2.Array objects

    /* package access */
    Map<Variable, Array>
    create()
            throws DapException
    {
        // iterate over the variables represented in the DSP
        List<DapVariable> topvars = this.dmr.getTopVariables();
        for(DapVariable var : topvars) {
            DataCursor cursor = this.dsp.getVariableData(var);
            Array array = createVar(var, cursor);
            Variable cdmvar = (Variable) nodemap.get(var);
            arraymap.put(cdmvar, array);
        }
        return this.arraymap;
    }

    protected Array
    createVar(DapVariable d4var, DataCursor data)
            throws DapException
    {
        Array array = null;
        switch (d4var.getSort()) {
        case ATOMICVARIABLE:
            array = createAtomicVar(data);
            break;
        case SEQUENCE:
            array = createSequence(data);
            break;
        case STRUCTURE:
            array = createStructure(data);
            break;
        default:
            assert false : "Unexpected databuffer sort: " + d4var.getSort();
        }
        if(d4var.isTopLevel()) {
            // transfer the checksum attribute
            byte[] csum = d4var.getChecksum();
            String scsum = Escape.bytes2hex(csum);
            Variable cdmvar = (Variable) nodemap.get(d4var);
            Attribute acsum = new Attribute(DapUtil.CHECKSUMATTRNAME, scsum);
            cdmvar.addAttribute(acsum);
        }
        return array;
    }

    /**
     * Create an Atomic Valued variable.
     *
     * @return An Array object wrapping d4var.
     * @throws DapException
     */
    protected Array
    createAtomicVar(DataCursor data)
            throws DapException
    {
        CDMArrayAtomic array = new CDMArrayAtomic(data);
        return array;
    }

    /**
     * Create an array of structures. WARNING: the underlying CDM code
     * (esp. NetcdfDataset) apparently does not support nested
     * structure arrays; so this code may throw an exception.
     *
     * @return A CDMArrayStructure for the databuffer for this struct.
     * @throws DapException
     */
    protected Array
    createStructure(DataCursor data)
            throws DapException
    {
        CDMArrayStructure arraystruct;
        arraystruct = new CDMArrayStructure(this.cdmroot, data);
        arraystruct.finish();
        return arraystruct;
    }

    /**
     * Create a sequence. WARNING: the underlying CDM code
     * (esp. NetcdfDataset) apparently does not support nested
     * sequence arrays.
     *
     * @param data the data underlying this sequence instance
     * @return A CDMArraySequence for this instance
     * @throws DapException
     */

    protected CDMArraySequence
    createSequence(DataCursor data)
            throws DapException
    {
        CDMArraySequence arrayseq;
        arrayseq = new CDMArraySequence(this.cdmroot, data);
        arrayseq.finish();
        return arrayseq;
    }

}
