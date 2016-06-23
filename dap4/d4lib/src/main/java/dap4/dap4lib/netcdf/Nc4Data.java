/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib.netcdf;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.*;
import dap4.dap4lib.AbstractData;
import dap4.dap4lib.AbstractDataVariable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dap4.dap4lib.netcdf.Nc4DMR.TypeNotes;
import static dap4.dap4lib.netcdf.Nc4DMR.VarNotes;
import static dap4.dap4lib.netcdf.NetcdfDSP.DEBUG;

public class Nc4Data
{
    //////////////////////////////////////////////////
    // com.sun.jna.Memory control

    static public abstract class Mem
    {
        static Memory
        allocate(long size)
        {
            if(size == 0)
                throw new IllegalArgumentException("Attempt to allocate zero bytes");
            Memory m = new Memory(size);
            return m;
        }
    }

    //////////////////////////////////////////////////

    static abstract public class Nc4DataVariable extends AbstractDataVariable
    {
        //////////////////////////////////////////////////
        // Instance variables

        DapNetcdf nc4 = null;

        //////////////////////////////////////////////////
        // Constructors

        public Nc4DataVariable(DSP dsp, DapVariable dv, Object src)
                throws DataException
        {
            super(dv, dsp, src);
            this.nc4 = ((NetcdfDSP) dsp).getJNI();
        }

    }

    static public class Nc4DataAtomic extends Nc4DataVariable
            implements DataAtomic
    {
        //////////////////////////////////////////////////
        // Instance variables

        protected long product = 0; // dimension cross product; 0 => undefined
        protected DapType basetype = null;
        protected TypeSort atomictype = null;
        protected boolean isscalar = false;

        long[] dimsizes = null;

        protected boolean isbytestring = false;
        protected long totalbytestringsize = 0;  // total space used by the bytestrings


        //////////////////////////////////////////////////
        // Constructors

        public Nc4DataAtomic(DSP dsp, DapAtomicVariable dap, Object src)
                throws DataException
        {
            super(dsp, dap, src);
            this.basetype = dap.getBaseType();
            this.atomictype = this.basetype.getTypeSort();
            List<DapDimension> dims = dap.getDimensions();
            if(dims == null) dims = new ArrayList<DapDimension>(0);
            isscalar = (dims.size() == 0);
            dimsizes = new long[dims.size()];
            for(int i = 0; i < dims.size(); i++) {
                dimsizes[i] = dims.get(i).getSize();
            }
            this.product = DapUtil.dimProduct(dims);
            this.isbytestring = (this.atomictype.isStringType() || this.atomictype.isOpaqueType());
        }

        //////////////////////////////////////////////////
        public long
        computebytestringsize()
        {
            if(!this.isbytestring) return 0;
            if(this.totalbytestringsize > 0) return this.totalbytestringsize;
            /*???*/
            return 0;
        }

        //////////////////////////////////////////////////
        // DataAtomic Interface

        @Override
        public DapType getType()
        {
            return this.basetype;
        }

        @Override
        public long getCount() // dimension cross-product
        {
            return this.product;
        }

        @Override
        public long getElementSize()
        {
            return getElementSize((Nc4DMR.TypeNotes) this.basetype.annotation());
        }

        protected long getElementSize(Nc4DMR.TypeNotes ti)
        {
            DapType type = TypeNotes.find(ti.id).get();
            switch (type.getTypeSort()) {
            case Struct:
            case Seq:
                throw new IllegalArgumentException();
            case String:
            case URL:
                return Pointer.SIZE;
            case Enum:
                return getElementSize(ti);
            case Opaque:
                return ti.opaquelen;
            default:
                return this.basetype.getSize();
            }
        }

        @Override
        public long getSizeBytes()
        {
            return this.getCount() * this.getElementSize();
        }

        @Override
        public Object
        read(Index index)
                throws DataException
        {
            return readHelper(index);
        }

        @Override
        public Object
        read(List<Slice> slices)
                throws DataException
        {
            int ret = DapNetcdf.NC_NOERR;
            if(slices == null) slices = new ArrayList<Slice>(0);
            int rank = slices.size();
            // Convert slices to (start,count,stride);
            SizeT[] startp = new SizeT[rank];
            SizeT[] countp = new SizeT[rank];
            SizeT[] stridep = new SizeT[rank];
            slicesToVars(slices, startp, countp, stridep);
            // Get VarNotes and TypeNotes
            Nc4DMR.VarNotes vi = (VarNotes) this.annotation();
            Nc4DMR.TypeNotes ti = vi.basetype;
            long elemsize = getElementSize();
            long memsize = elemsize * getCount();
            Memory mem = Mem.allocate(memsize);
            readcheck(nc4, ret = nc4.nc_get_vars(vi.gid, vi.id, startp, countp, stridep, mem));
            long count = this.getCount();
            return getdata(ti, count, elemsize, mem);
        }

        protected Object
        getdata(Nc4DMR.TypeNotes ti, long lcount, long elemsize, Memory mem)
        {
            Object result = null;
            TypeSort sort = ti.get().getTypeSort();
            int icount = (int) lcount;
            switch (sort) {
            case Char:
                // need to extract and convert utf8(really ascii) -> utf16
                byte[] bresult = mem.getByteArray(0, icount);
                char[] cresult = new char[bresult.length];
                for(int i = 0; i < icount; i++) {
                    int ascii = bresult[i];
                    ascii = ascii & 0x7F;
                    cresult[i] = (char) ascii;
                }
                result = cresult;
                break;
            case UInt8:
            case Int8:
                result = mem.getByteArray(0, icount);
                break;
            case Int16:
            case UInt16:
                result = mem.getShortArray(0, icount);
                break;
            case Int32:
            case UInt32:
                result = mem.getIntArray(0, icount);
                break;
            case Int64:
            case UInt64:
                result = mem.getLongArray(0, icount);
                break;
            case Float32:
                result = mem.getFloatArray(0, icount);
                break;
            case Float64:
                result = mem.getDoubleArray(0, icount);
                break;
            case String:
            case URL:
                // TODO: properly free underlying strings
                result = mem.getStringArray(0, icount);
                break;
            case Opaque:
                ByteBuffer[] ops = new ByteBuffer[icount];
                result = ops;
                for(int i = 0; i < icount; i++) {
                    ops[i] = mem.getByteBuffer(i * elemsize, elemsize);
                }
                break;
            case Enum:
                DapEnumeration de = (DapEnumeration) basetype;
                result = getdata(ti, lcount, elemsize, mem);
                break;
            }
            return result;
        }
    }

    static public class Nc4DataCompoundArray extends Nc4DataVariable
            implements DataCompoundArray
    {
        //////////////////////////////////////////////////
        // Instance variables

        long position = 0;

        List<DataCompound> instances = new ArrayList<DataCompound>();

        //////////////////////////////////////////////////
        // Constructors

        public Nc4DataCompoundArray(DSP dsp, DapVariable dv, Object src)
                throws DataException
        {
            super(dsp, dv, src);
        }

        //////////////////////////////////////////////////
        // Accessor(s)

        public void addElement(DataCompound di)
        {
            this.instances.add(di);
        }

        //////////////////////////////////////////////////
        // DataVariable Interface

        public DataSort getElementSort()
        {
            if(this.getTemplate().getSort() == DapSort.SEQUENCE)
                return DataSort.SEQUENCE;
            else
                return DataSort.STRUCTURE;
        }

        public long getCount()
        {
            return DapUtil.dimProduct(((DapVariable) getTemplate()).getDimensions());
        }

        @Override
        public DataCompound
        getElement(Index index)
                throws DataException
        {
            long offset = index.index();
            if(offset < 0 || offset >= instances.size())
                throw new DataException("Nc4DataCompoundArray.read(i): index out of range: " + index);
            return instances.get((int) offset);
        }
    }

    static public class Nc4DataDataset extends AbstractData
            implements DataDataset
    {

        //////////////////////////////////////////////////
        // Constants

        static final public long serialVersionUID = 1L;

        //////////////////////////////////////////////////
        // Instance variables

        //Coverity[FB.URF_UNREAD_FIELD]
        protected List<DataVariable> variables = new ArrayList<>();

        //////////////////////////////////////////////////
        // Constructors

        public Nc4DataDataset(DSP dsp, DapDataset dmr, Object src)
                throws DataException
        {
            super(dmr, dsp, src);
        }

        //////////////////////////////////////////////////
        // Accessors

        public void
        addVariable(DataVariable dv)
        {
            variables.add(dv);
        }

        @Override
        public List<DataVariable>
        getTopVariables()
        {
            return variables;
        }

        //////////////////////////////////////////////////
        // DataDataset Interface

        public DataVariable
        getVariableData(DapVariable var)
                throws DataException
        {
            for(DataVariable dv : variables) {
                if(dv.getVariable() == var)
                    return dv;
            }
            return null;
        }

    }


    /**
     * DataRecord represents a record from a sequence.
     * It is effectively equivalent to a Structure instance.
     */

    static public class Nc4DataRecord extends Nc4DataVariable
            implements DataRecord
    {
        //////////////////////////////////////////////////
        // Instance variables

        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected Nc4DataSequence parent = null;
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected int recno = 0;
        protected DataVariable[] fields;

        //////////////////////////////////////////////////
        // Constructors

        public Nc4DataRecord(DSP dsp, DapSequence dap, DataSequence parent, Object src)
                throws DataException
        {
            super(dsp, dap, src);
            this.dsp = dsp;
            this.parent = (Nc4DataSequence) parent;
            this.recno = (Integer) source;
            this.fields = new Nc4DataVariable[dap.getFields().size()];
            Arrays.fill(this.fields, null);
        }

        //////////////////////////////////////////////////
        // Accessors

        @Override
        public void
        addField(int fieldno, DataVariable ddv)
                throws DataException
        {
            if(fieldno < 0 || fieldno >= fields.length)
                throw new DataException("Illegal field index: " + fieldno);
            fields[fieldno] = ddv;
        }

        //////////////////////////////////////////////////
        // DataStructure Interface

        // Read field by index
        @Override
        public DataVariable getfield(int i) throws DataException
        {
            if(i < 0 || i >= fields.length)
                throw new DataException("Illegal field index: " + i);
            return fields[i];
        }

        // Read field by name
        @Override
        public DataVariable getfield(String shortname) throws DataException
        {
            for(int i = 0; i < fields.length; i++) {
                if(fields[i].getTemplate().getShortName().equals(shortname))
                    return fields[i];
            }
            return null;
        }

        public DataCompoundArray asCompoundArray()
                throws DataException
        {
            throw new UnsupportedOperationException();
        }

    }

    static public class Nc4DataSequence extends Nc4DataVariable
            implements DataSequence
    {
        //////////////////////////////////////////////////
        // Instance variables

        protected DataCompoundArray parent = null;
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected int index = 0;
        List<DataRecord> records = new ArrayList<>();

        //////////////////////////////////////////////////
        // Constructor(s)

        /**
         * @param dsp The containing DSP
         * @param dap The template for this sequence
         * @param cdv the parent compound array
         * @param src within the parent compound array
         * @return A D4DataSequence for the records for this sequence.
         * @throws DataException
         */
        public Nc4DataSequence(DSP dsp, DapSequence dap, DataCompoundArray cdv, Object src)
                throws DataException
        {
            super(dsp, dap, src);
            this.dsp = dsp;
            this.parent = cdv;
            this.index = (Integer) src;
        }

        //////////////////////////////////////////////////
        // Accessors

        public void addRecord(DataRecord record)
        {
            records.add(record);
        }

        //////////////////////////////////////////////////
        // DataSequence Interface

        @Override
        public long
        getRecordCount()
        {
            return records.size();
        }

        @Override
        public DataRecord getRecord(long recordno)
                throws DataException
        {
            if(recordno < 0 || recordno >= records.size())
                throw new DataException("Illegal record index: " + recordno);
            return records.get((int) recordno);
        }

    }

    static public class Nc4DataStructure extends Nc4DataVariable
            implements DataStructure
    {
        //////////////////////////////////////////////////
        // Instance variables

        protected DataCompoundArray parent = null;
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected int index = 0;
        protected DataVariable[] fielddata = null;

        //////////////////////////////////////////////////
        // Constructors

        public Nc4DataStructure(DSP dsp, DapStructure dap, DataCompoundArray parent, Object source)
                throws DataException
        {
            super(dsp, dap, source);
            this.parent = (DataCompoundArray) parent;
            this.index = (Integer) source;
            this.fielddata = new DataVariable[dap.getFields().size()];
        }

        //////////////////////////////////////////////////
        // Accessors

        public void
        addField(int mindex, DataVariable ddv)
        {
            if(fielddata[mindex] != null)
                throw new IllegalStateException("duplicate fields");
            fielddata[mindex] = ddv;
        }

        //////////////////////////////////////////////////
        // DataStructure Interface

        @Override
        public DataVariable getfield(String name) throws DataException
        {
            int index = ((DapStructure) this.getTemplate()).indexByName(name);
            return getfield(index);
        }

        @Override
        public DataVariable getfield(int index)
                throws DataException
        {
            DataVariable ddv = fielddata[index];
            return ddv;
        }

        public DataCompoundArray asCompoundArray()
                throws DataException
        {
            Nc4DataCompoundArray dda = new Nc4DataCompoundArray(getDSP(), getVariable(), null);
            dda.addElement(this);
            return dda;
        }

    }

    //////////////////////////////////////////////////
    // Utilities

    static void
    slicesToVars(List<Slice> slices, SizeT[] startp, SizeT[] countp, SizeT[] stridep)
    {
        for(int i = 0; i < slices.size(); i++) {
            Slice slice = slices.get(i);
            startp[i] = new SizeT(slice.getFirst());
            countp[i] = new SizeT(slice.getCount());
            stridep[i] = new SizeT(slice.getStride());
        }
    }

    static public void
    errcheck(DapNetcdf nc4, int ret)
            throws DapException
    {
        if(ret != 0) {
            String msg = String.format("TestNetcdf: errno=%d; %s", ret, nc4.nc_strerror(ret));
            if(DEBUG)
                System.err.println(msg);
            throw new DapException(msg);
        }
    }

    static public void
    readcheck(DapNetcdf nc4, int ret)
            throws DataException
    {
        try {
            errcheck(nc4, ret);
        } catch (DapException de) {
            throw new DataException(de);
        }
    }


}
