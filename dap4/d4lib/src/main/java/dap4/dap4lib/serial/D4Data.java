/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib.serial;

import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.*;
import dap4.dap4lib.AbstractData;
import dap4.dap4lib.AbstractDataVariable;
import dap4.dap4lib.Dap4Util;
import dap4.dap4lib.LibTypeFcns;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class D4Data
{
    static abstract public class D4DataVariable extends AbstractDataVariable
    {
        //////////////////////////////////////////////////
        // Instance variables

        //////////////////////////////////////////////////
        // Constructors

        public D4DataVariable(DSP dsp, DapVariable dv, Object src)
                throws DataException
        {
            super(dv, dsp, src);
        }
    }

    static public class D4DataAtomic extends D4DataVariable
            implements DataAtomic
    {
        //////////////////////////////////////////////////
        // Instance variables

        protected long product = 0; // dimension cross product; 0 => undefined
        protected DapType basetype = null;
        protected TypeSort atomictype = null;
        protected boolean isscalar = false;
        protected long varoffset = -1; // absolute offset of the start in bytebyffer.
        protected long varelementsize = 0;
        protected boolean isbytestring = false;
        // Following two fields only defined if isbytestring is true
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected long totalbytestringsize = 0;  // total space used by the bytestrings
        protected int[] bytestrings = null; // List of the absolute start offsets of
        // an array of e.g. opaque,  or string atomictypes.
        // The value is the offset of object's count.

        //////////////////////////////////////////////////
        // Constructors

        public D4DataAtomic(DSP dsp, DapAtomicVariable dap, Object src)
                throws DataException
        {
            super(dsp, dap, src);
            this.varoffset = (Integer) src;
            this.basetype = dap.getBaseType();
            this.atomictype = this.basetype.getTypeSort();
            this.product = DapUtil.dimProduct(dap.getDimensions());
            this.varelementsize = LibTypeFcns.size(this.basetype);
            this.isbytestring = (this.atomictype.isStringType() || this.atomictype.isOpaqueType());
        }

        //////////////////////////////////////////////////
        // D4DataAtomic Specific API (should all be package accessible only)

        /* Computed by the DataCompiler */
        public void
        setByteStringOffsets(long totalsize, int[] offsets)
        {
            this.totalbytestringsize = totalsize;
            this.bytestrings = offsets;
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
            return this.basetype.isFixedSize() ? this.basetype.getSize() : 0;
        }

        @Override
        public long getSizeBytes()
        {
            if(this.basetype.isFixedSize())
                return this.getCount() * this.getElementSize();
            else
                return this.totalbytestringsize;
        }

        @Override
        public Object
        read(List<Slice> slices)
                throws DataException
        {
            ByteBuffer src = (ByteBuffer) getDSP().getDataset().getSource();
            if(slices == null || slices.size() == 0) { // scalar
                Object dst = LibTypeFcns.newVector(this.basetype,1);
                extractObjectVector(this.basetype, src, dst, Index.SCALAR, 0, 1);
                return dst;
            } else {// dimensioned
                boolean contig = DapUtil.isContiguous(slices);
                Odometer odom;
                DapVariable var = (DapVariable) this.getTemplate();
                try {
                    odom = Odometer.factory(slices,
                            var.getDimensions(),
                            contig);
                } catch (DapException de) {
                    throw new DataException(de);
                }
                Object dst = LibTypeFcns.newVector(var.getBaseType(), odom.totalSize());
                if(odom.isContiguous()) {
                    List<Slice> pieces = odom.getContiguous();
                    assert pieces.size() == 1;  // temporary
                    Slice lastslice = pieces.get(0);
                    assert lastslice.getStride() == 1;
                    long first = lastslice.getFirst();
                    long extent = lastslice.getCount();
                    Object data = LibTypeFcns.newVector(this.basetype,extent);
                    for(int i=0;odom.hasNext();) {
                        Index index = odom.next();
                        index.indices[index.getRank()-1] += first;
                        extractObjectVector(this.basetype, src, data, index, 0, extent);
                    }
                } else { // read one by one
                    for(int i=0;odom.hasNext();i++) {
                        Index index = odom.next();
                        extractObjectVector(this.basetype, src, dst, index, i, 1);
                    }
                }
                return dst;
            }
        }

        @Override
        public Object
        read(Index index)
                throws DataException
        {
            return readHelper(index);
        }

        //////////////////////////////////////////////////
        // Utilities

        protected void
        moveto(long index, ByteBuffer databuffer)
                throws DataException
        {
            if(index < 0 || index > this.product)
                throw new IndexOutOfBoundsException("D4DataAtomic: " + index);
            if(isbytestring) {
                databuffer.position(bytestrings[(int) index]);
            } else
                databuffer.position((int) (this.varoffset + (this.varelementsize * index)));
        }

        /**
         * Extract, as an object, value from a (presumably)
         * atomic typed array of values; dataset position
         * is presumed correct.
         *
         * @param basetype type of object to extract
         * @param dataset  ByteBuffer containing databuffer; position assumed correct
         * @return resulting value as an Object; value does not necessarily conform
         * to Convert.ValueClass.
         */

        protected Object
        extractObject(DapType basetype, ByteBuffer dataset, long index)
                throws DataException
        {
            Object result = null;
            long lvalue = 0;
            TypeSort atomtype = basetype.getTypeSort();
            moveto(index,dataset);
            switch (atomtype) {
            case Char:
                lvalue = dataset.get();
                lvalue &= 0xFFL; // make unsigned
                result = new Character((char) lvalue);
                break;
            case UInt8:
            case Int8:
                result = new Byte(dataset.get());
                break;
            case Int16:
            case UInt16:
                result = new Short(dataset.getShort());
                break;
            case Int32:
            case UInt32:
                result = new Integer(dataset.getInt());
                break;
            case Int64:
            case UInt64:
                result = new Long(dataset.getLong());
                break;
            case Float32:
                result = new Float(dataset.getFloat());
                break;
            case Float64:
                result = new Double(dataset.getDouble());
                break;
            case String:
            case URL:
                long count = dataset.getLong();
                byte[] bytes = new byte[(int) count];
                dataset.get(bytes);
                result = new String(bytes, DapUtil.UTF8);
                break;
            case Opaque:
                count = dataset.getLong();
                bytes = new byte[(int) count];
                dataset.get(bytes);
                result = ByteBuffer.wrap(bytes); // order is irrelevant
                break;
            case Enum:
                // recast as enum's basetype
                result = extractObject(((DapEnumeration) basetype).getBaseType(), dataset, index);
                break;
            }
            return result;
        }

        /**
         * Vector version of extractObject().
         * Extract a vector of objects from a (presumably)
         * atomic typed array of values; dataset position
         * is presumed correct.
         *
         * @param basetype - type of objects being extracted
         * @param dataset  -serialized data
         * @param dst  - place to store data
         * @param indices    - index of first object
         * @param count    - of # of contiguous objects
         * @throws DataException
         */
        protected void
        extractObjectVector(DapType basetype, ByteBuffer dataset, Object dst, Index indices, long offset, long count)
                throws DataException
        {
            long xindex = indices.index();
            TypeSort atomtype = basetype.getTypeSort();
            moveto(xindex,dataset);
            long elemsize = this.varelementsize;
            int ioffset = (int)offset;
            switch (atomtype) {
            case Char:
                // need to extract and convert utf8(really ascii) -> utf16
                char[] cresult = (char[])dst;
                for(int i = 0; i < count; i++) {
                    int ascii = dataset.get();
                    ascii = ascii & 0x7F;
                    cresult[i+ioffset] = (char) ascii;
                }
                break;
            case UInt8:
            case Int8:
                byte[] bresult = (byte[])dst;
                dataset.get(bresult, ioffset, (int) count);
                break;
            case Int16:
            case UInt16:
                short[] shresult = (short[])dst;
                dataset.asShortBuffer().get(shresult, ioffset, (int) count);
                break;
            case Int32:
            case UInt32:
                int[] iresult = (int[])dst;
                dataset.asIntBuffer().get(iresult, ioffset, (int) count);
                break;
            case Int64:
            case UInt64:
                long[] lresult = (long[])dst;
                dataset.asLongBuffer().get(lresult, ioffset, (int) count);
                break;
            case Float32:
                float[] fresult = (float[])dst;
                dataset.asFloatBuffer().get(fresult, ioffset, (int) count);
                break;
            case Float64:
                double[] dresult = (double[])dst;
                dataset.asDoubleBuffer().get(dresult, ioffset, (int) count);
                break;
            case String:
            case URL:
                String[] sresult = (String[])dst;
                for(int i = 0; i < count; i++) {
                    dataset.position(bytestrings[(int) xindex + i]);
                    long scount = dataset.getLong();
                    byte[] bytes = new byte[(int) scount];
                    dataset.get(bytes);
                    sresult[i+ioffset] = new String(bytes, DapUtil.UTF8);
                }
                break;
            case Opaque:
                ByteBuffer[] oresult = (ByteBuffer[])dst;
                for(int i = 0; i < count; i++) {
                    dataset.position(bytestrings[(int) xindex + i]);
                    long scount = dataset.getLong();
                    byte[] bytes = new byte[(int) scount];
                    dataset.get(bytes);
                    oresult[i+ioffset] = ByteBuffer.wrap(bytes);
                }
                break;
            case Enum:
                // recast as enum's basetype
                extractObjectVector(((DapEnumeration) basetype).getBaseType(), dataset, dst, indices, offset, count);
                break;
            }
        }

    }

    static public class D4DataCompoundArray extends D4DataVariable
            implements DataCompoundArray
    {
        //////////////////////////////////////////////////
        // Instance variables

        long position = 0;

        List<DataCompound> instances = new ArrayList<DataCompound>();

        //////////////////////////////////////////////////
        // Constructors

        public D4DataCompoundArray(DSP dsp, DapVariable dv, Object src)
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

        // Provide a read of a single value at a given offset in
        // a dimensioned variable.
        public DataCompound
        getElement(Index index)
                throws DataException
        {
            long offset = index.index();
            if(offset < 0 || offset >= instances.size())
                throw new DataException("D4DataCompoundArray.read(i): index out of range: " + index);
            return instances.get((int) offset);
        }

        //////////////////////////////////////////////////
        // Utilities

        /*protected DapSort
        computesort(Array array)
            throws DataException
        {
            DapSort sort = null;
            switch (array.getDataType()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case STRING:
            case OBJECT:
                return DapSort.ATOMICVARIABLE;
            case STRUCTURE:
                return DapSort.COMPOUND;
            default:
                break; // sequence is not supported
            }
            throw new DataException("Unsupported datatype: " + array.getDataType());
        }*/
    }


    static public class D4DataDataset extends AbstractData
            implements DataDataset
    {

        //////////////////////////////////////////////////
        // Constants

        static final public long serialVersionUID = 1L;

        //////////////////////////////////////////////////
        // Instance variables

        //Coverity[FB.URF_UNREAD_FIELD]
        protected D4DSP dsp = null;
        protected List<DataVariable> variables = new ArrayList<>();

        //////////////////////////////////////////////////
        // Constructors

        public D4DataDataset(D4DSP dsp, DapDataset dmr, Object src)
                throws DataException
        {
            super(dmr, dsp, src);
            this.dsp = dsp;
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

    static public class D4DataRecord extends D4DataVariable
            implements DataRecord
    {

        //////////////////////////////////////////////////
        // Instance variables

        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected D4DataSequence parent = null;
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected int recno = 0;
        protected DataVariable[] fields;

        //////////////////////////////////////////////////
        // Constructors

        public D4DataRecord(DSP dsp, DapSequence dap, DataSequence parent, Object src)
                throws DataException
        {
            super(dsp, dap, src);
            this.dsp = dsp;
            this.parent = (D4DataSequence) parent;
            this.recno = (Integer) source;
            this.fields = new D4DataVariable[dap.getFields().size()];
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


    static public class D4DataSequence extends D4DataVariable
            implements DataSequence
    {
        //////////////////////////////////////////////////
        // Type Decls

        /*
        static class Field
        {
            DapVariable field;
            SequenceMembers.Member member;
            int index;

            public Field(DapVariable field, int index, SequenceMembers.Member member)
            {
                this.field = field;
                this.member = member;
                this.index = index;
            }
        }
        */

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
        public D4DataSequence(DSP dsp, DapSequence dap, DataCompoundArray cdv, Object src)
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

    static public class D4DataStructure extends D4DataVariable
            implements DataStructure
    {
        //////////////////////////////////////////////////
        // Instance variables

        protected D4DataCompoundArray parent = null;
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected int index = 0;
        protected DataVariable[] fielddata = null;

        //////////////////////////////////////////////////
        // Constructors

        public D4DataStructure(DSP dsp, DapStructure dap, DataCompoundArray parent, Object source)
                throws DataException
        {
            super(dsp, dap, source);
            this.parent = (D4DataCompoundArray) parent;
            this.index = (Integer) source;
            this.fielddata = new D4DataVariable[dap.getFields().size()];
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
            D4DataCompoundArray dda = new D4DataCompoundArray(getDSP(), getVariable(), null);
            dda.addElement(this);
            return dda;
        }

    }


}

