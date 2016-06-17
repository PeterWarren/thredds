/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/


package dap4.cdm.nc2;

import dap4.cdm.NodeMap;
import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.*;
import ucar.ma2.Array;
import ucar.nc2.Attribute;
import ucar.nc2.Group;
import ucar.nc2.Variable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create a set of CDM ucar.ma2.array objects that wrap the
 * DataDataset object.
 */

public class DataToCDM
{
    static public boolean DEBUG = false;

    //////////////////////////////////////////////////
    // Constants

    static final int COUNTSIZE = 8; // databuffer as specified by the DAP4 spec

    static String LBRACE = "{";
    static String RBRACE = "}";

    //////////////////////////////////////////////////
    // Instance variables

    DapNetcdfFile ncfile = null;
    DSP dsp = null;
    DapDataset dmr = null;
    DataDataset d4root = null;
    Group cdmroot = null;
    Map<Variable, Array> arraymap = null;
    NodeMap nodemap = null;

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
        this.d4root = (DataDataset) dsp.getDataset();
        this.dmr = dsp.getDMR();
        this.nodemap = nodemap;
        arraymap = new HashMap<Variable, Array>();
    }

    //////////////////////////////////////////////////
    // Compile Data objects to ucar.ma2.Array objects

    /* package access */
    Map<Variable, Array>
    create()
            throws DapException
    {
        assert d4root.getSort() == DataSort.DATASET;
        // iterate over the variables represented in the databuffer
        List<DataVariable> vars = this.d4root.getTopVariables();
        for(DataVariable var : vars) {
            Variable cdmvar = (Variable) nodemap.get(var.getTemplate());
            Array array = createVar(var);
            arraymap.put(cdmvar, array);
        }
        return this.arraymap;
    }

    protected Array
    createVar(DataVariable d4var)
            throws DapException
    {
        Array array = null;
        DapVariable dapvar = (DapVariable) d4var.getTemplate();
        switch (d4var.getSort()) {
        case ATOMIC:
            array = createAtomicVar(d4var);
            break;
        case SEQUENCE:
            array = createSequenceArray((DataVariable) d4var);
            break;
        case STRUCTURE:
            array = createStructureArray((DataVariable) d4var);
            break;
        case COMPOUNDARRAY:
            if(dapvar.getSort() == DapSort.STRUCTURE)
                array = createStructureArray((DataVariable) d4var);
            else if(dapvar.getSort() == DapSort.SEQUENCE)
                array = createSequenceArray((DataVariable) d4var);
            break;
        default:
            assert false : "Unexpected databuffer sort: " + d4var.getSort();
        }
        if(dapvar.isTopLevel()) {
            // transfer the checksum attribute
            byte[] csum = dapvar.getChecksum();
            String scsum = Escape.bytes2hex(csum);
            Variable cdmvar = (Variable) nodemap.get(dapvar);
            Attribute acsum = new Attribute(DapUtil.CHECKSUMATTRNAME, scsum);
            cdmvar.addAttribute(acsum);
        }
        return array;
    }

    /**
     * Create an Atomic Valued variable.
     *
     * @param d4var The D4 datvariable being wrapped
     * @return An Array object wrapping d4var.
     * @throws DapException
     */
    protected Array
    createAtomicVar(DataVariable d4var)
            throws DapException
    {
        CDMArrayAtomic array = new CDMArrayAtomic(this.dsp, this.cdmroot, (DataAtomic) d4var);
        return array;
    }

    /**
     * Create a single structure instance. WARNING: the underlying CDM code
     * (esp. NetcdfDataset) apparently does not support nested
     * structure arrays; so this code may throw an exception.
     *
     * @param d4var     the data underlying this structure instance
     * @param pos       the linear index in the parent compound array.
     * @param container the parent CDMArrayStructure
     * @return An Array for this instance
     * @throws DapException
     */
    protected CDMArray
    createStructure(DataStructure d4var, long pos, CDMArrayStructure container)
            throws DapException
    {
        assert (d4var.getSort() == DataSort.STRUCTURE);
        DapStructure dapstruct = (DapStructure) d4var.getTemplate();
        int nmembers = dapstruct.getFields().size();
        List<DapVariable> dfields = dapstruct.getFields();
        assert nmembers == dfields.size();
        for(int m = 0; m < nmembers; m++) {
            DataVariable dfield = d4var.getfield(m);
            Array afield = createVar(dfield);
            container.addField(pos, m, afield);
        }
        return container;
    }

    /**
     * Create an array of structures. WARNING: the underlying CDM code
     * (esp. NetcdfDataset) apparently does not support nested
     * structure arrays; so this code may throw an exception.
     *
     * @param d4var The D4 databuffer wrapper
     * @return A CDMArrayStructure for the databuffer for this struct.
     * @throws DapException
     */
    protected Array
    createStructureArray(DataVariable d4var)
            throws DapException
    {
        DapStructure dapstruct = (DapStructure) d4var.getTemplate();
        List<DapDimension> dimset = dapstruct.getDimensions();
        CDMArrayStructure arraystruct;
        DataCompoundArray d4array;
        boolean isscalar = (dimset == null || dimset.size() == 0);
        try {
            if(isscalar) {
                assert (d4var.getSort() == DataSort.STRUCTURE);
                d4array = ((DataStructure) d4var).asCompoundArray();
            } else {
                assert (d4var.getSort() == DataSort.COMPOUNDARRAY);
                d4array = (DataCompoundArray) d4var;
            }
            arraystruct = new CDMArrayStructure(this.dsp, this.cdmroot, d4array);
            Odometer odom = isscalar ? Odometer.factoryScalar()
                    : Odometer.factory(null, dimset, false);
            while(odom.hasNext()) {
                DataStructure dds = (DataStructure) d4array.getElement(odom.indices());
                createStructure(dds, odom.index(), arraystruct);
            }
            arraystruct.finish();
            return arraystruct;
        } catch (IOException ioe) {
            throw new DapException(ioe);
        }

    }

    /**
     * Create a sequence. WARNING: the underlying CDM code
     * (esp. NetcdfDataset) apparently does not support nested
     * sequence arrays.
     *
     * @param d4var the data underlying this sequence instance
     * @return A CDMArraySequence for this instance
     * @throws DapException
     */

    protected CDMArraySequence
    createSequence(DataSequence d4var)
            throws DapException
    {
        assert (d4var.getSort() == DataSort.SEQUENCE);
        DapSequence dapseq = (DapSequence) d4var.getTemplate();
        CDMArraySequence container = new CDMArraySequence(this.dsp, this.cdmroot, dapseq, d4var);
        long nrecs = d4var.getRecordCount();
        // Fill in the record fields
        for(int recno = 0; recno < nrecs; recno++) {
            DataRecord rec = (DataRecord) d4var.getRecord(recno);
            for(int fieldno = 0; fieldno < dapseq.getFields().size(); fieldno++) {
                DataVariable field = (DataVariable) rec.getfield(fieldno);
                // create the field to get an array
                container.addField(recno, fieldno, createVar(field));
            }
        }
        return container;
    }

    /**
     * Create an array of sequences. WARNING: the underlying CDM code
     * (esp. NetcdfDataset) apparently does not support sequences
     * with rank > 0 (ignoring the vlen dimension)
     * so this code may throw an exception.
     *
     * @param d4var The D4 databuffer wrapper
     * @return A CDMArraySequence for the databuffer for this seq.
     * @throws DapException
     * @see CDMArraySequence
     * to see how a dimensioned Sequence is represented.
     */

    protected Array
    createSequenceArray(DataVariable d4var)
            throws DapException
    {
        Array array = null;

        if(d4var.getSort() == DataSort.SEQUENCE) {// scalar
            array = createSequence((DataSequence) d4var);
        } else if(d4var.getSort() == DataSort.COMPOUNDARRAY) {
            throw new DapException("Only Sequence{...}(*) supported");
        } else
            throw new DapException("CDMCreater: unexpected data variable type: " + d4var.getSort());
        return array;
    }

    static void
    skip(ByteBuffer data, int count)
    {
        data.position(data.position() + count);
    }

    static int
    getCount(ByteBuffer data)
    {
        long count = data.getLong();
        return (int) (count & 0xFFFFFFFF);
    }

    /**
     * Compute the size in databuffer of the serialized form
     * <p>
     * param daptype
     *
     * @return type's serialized form size
     */
/*    static int
    computeTypeSize(DapType daptype)
    {
        TypeSort atype = daptype.getTypeSort();
        if(atype == TypeSort.Enum) {
            DapEnumeration dapenum = (DapEnumeration) daptype;
            atype = dapenum.getBaseType().getTypeSort();
        }
        return Dap4Util.daptypeSize(atype);
    }
    */
    static long
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

    /*
    Array
    createAtomicVLEN(ViewVariable annotation)
        throws DapException
    {
        DapAtomicVariable atomvar = (DapAtomicVariable) annotation.getVariable();
        DapType daptype = atomvar.getBaseType();
        List<DapDimension> dimset = atomvar.getDimensions();

        // For the VLEN case, we need to build a simple Array whose storage
        // is Object. Each element of the storage will contain
        // a Dap4AtomicVLENArray pointing to one of the vlen instances.

        // Compute rank upto the VLEN
        int prefixrank = dimset.size() - 1;

        // Compute product size up to the VLEN
        int dimproduct = 1;
        for(int i = 0;i < prefixrank;i++)
            dimproduct *= dimset.get(i).getSize();

        // Collect the vlen's databuffer arrays
        Object[] databuffer = new Object[dimproduct];
        List<Slice> slices = new ArrayList<Slice>(); // reusable
        for(int i = 0;i < dimproduct;i++) {
            int savepos = databuffer.position();  // mark the start of this instance
            // Get the number of elements in this vlen instance
            int count = getCount(databuffer);
            slices.clear();
            slices.add(new Slice(0, count - 1, 1)); // create synthetic slice to cover the vlen count
            Dap4AtomicVLENArray vlenarray
                = new Dap4AtomicVLENArray(this.d4dataset, atomvar, slices, databuffer.position());
            databuffer[i] = vlenarray;
            vlenarray.setSize(count, databuffer.position());
            if(!daptype.isEnumType() && !daptype.isFixedSize()) {
                // this is a string, url, or opaque
                int[] positions = new int[count];
                long total = walkByteStrings(positions, databuffer);
                vlenarray.setByteStrings(positions, total);
            }
            vlenarray.computeTotalSize();
            databuffer.position(savepos);
            skip(databuffer, (int) vlenarray.getTotalSize());
        }

        // Construct the return array; code taken from Nc4Iosp
        if(prefixrank == 0) // if scalar, return just the len Array
            return (Array) databuffer[0];
        //if(prefixrank == 1)
        //    return (Array) new ArrayObject(databuffer[0].getClass(), new int[]{dimproduct}, databuffer);

        // Otherwise create and fill in an n-dimensional Array Of Arrays
        int[] shape = new int[prefixrank];
        for(int i = 0;i < prefixrank;i++)
            shape[i] = (int) dimset.get(i).getSize(); //todo: or do we use the annotation
        Array ndimarray = Array.factory(Array.class, shape);
        // Transfer the elements of databuffer into the n-dim arrays
        IndexIterator iter = ndimarray.getIndexIterator();
        for(int i = 0;iter.hasNext();i++) {
            iter.setObjectNext(databuffer[i]);
        }
        return ndimarray;
    }
    */
}
