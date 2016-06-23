/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm.dsp;

import dap4.cdm.CDMTypeFcns;
import dap4.cdm.CDMUtil;
import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.*;
import dap4.core.util.Index;
import dap4.dap4lib.AbstractData;
import dap4.dap4lib.AbstractDataVariable;
import dap4.dap4lib.Dap4Util;
import dap4.dap4lib.LibTypeFcns;
import ucar.ma2.*;
import ucar.nc2.CDMNode;
import ucar.nc2.Variable;

import java.nio.ByteBuffer;
import java.util.*;

public class CDMData
{

    static public class CDMDataAtomic extends AbstractDataVariable implements CDMDataVariable, DataAtomic
    {
        //////////////////////////////////////////////////
        // Instance variables

        protected long product = 0; // dimension cross product; 0 => undefined; scalar=>1

        protected DapType basetype = null;
        protected TypeSort atomtype = null;

        //////////////////////////////////////////////////
        // Constructors

        public CDMDataAtomic(DSP dsp, DapAtomicVariable template, Object array)
                throws DataException
        {
            super(template, dsp, array);
            this.basetype = ((DapVariable) template).getBaseType();
            this.atomtype = this.basetype.getTypeSort();
            this.product = DapUtil.dimProduct(template.getDimensions());
            this.dsp = dsp;
        }

        //////////////////////////////////////////////////
        // CDMDataAvariable Interface

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
            return LibTypeFcns.size(this.basetype);
        }

        @Override
        public long
        getSizeBytes()
        {
            return ((Array) getSource()).getSizeBytes();
        }

        @Override
        public Object
        read(List<Slice> slices)
            //read(long start, long count, Object data, long offset)
                throws DataException
        {
            Array array = ((Array) getSource());
            // If content.getDataType returns object, then we
            // really do not know its true datatype. So, as a rule,
            // we will rely on this.basetype.
            DataType datatype = CDMTypeFcns.daptype2cdmtype(this.basetype);
            if(datatype == null)
                throw new DataException("Unknown basetype: " + this.basetype);
            Object content = array.get1DJavaArray(datatype); // not very efficient; should do conversion
            try {
                Odometer odom = Odometer.factory(slices, ((DapVariable) this.getTemplate()).getDimensions(), false);
                Object data = CDMTypeFcns.createVector(this.basetype, odom.totalSize());
                for(int dstoffset=0; odom.hasNext();dstoffset++) {
                    Index index = odom.next();
                    long srcoffset = index.index();
                    CDMTypeFcns.vectorcopy(this.basetype, content, data, srcoffset, dstoffset);
                }
                return data;
            } catch (DapException de) {
                throw new DataException(de);
            }
        }

        @Override
        public Object
        read(Index index)
                throws DataException
        {
            Object result;
            int i = (int) index.index();
            Array content = ((Array) getSource());
            DataType datatype = content.getDataType();
            long tmp = 0;
            switch (datatype) {
            case BOOLEAN:
                result = (Boolean) content.getBoolean(i);
                break;
            case BYTE:
                result = (Byte) content.getByte(i);
                break;
            case CHAR:
                result = (Character) content.getChar(i);
                break;
            case SHORT:
                result = (Short) content.getShort(i);
                break;
            case INT:
                result = (Integer) content.getInt(i);
                break;
            case LONG:
                result = (Long) content.getLong(i);
                break;
            case FLOAT:
                result = (Float) content.getFloat(i);
                break;
            case DOUBLE:
                result = (Double) content.getDouble(i);
                break;
            case STRING:
                result = content.getObject(i).toString();
                break;
            case OBJECT:
                result = content.getObject(i);
                break;
            case UBYTE:
                tmp = content.getByte(i) & 0xFF;
                result = (Byte) (byte) tmp;
                break;
            case USHORT:
                tmp = content.getShort(i) & 0xFFFF;
                result = (Short) (short) tmp;
                break;
            case UINT:
                tmp = content.getInt(i) & 0xFFFFFFFF;
                result = (Integer) (int) tmp;
                break;
            case ULONG:
                result = (Long) content.getLong(i);
                break;
            case OPAQUE:
                result = content.getObject(i);
                break;
            case STRUCTURE:
            case SEQUENCE:
            default:
                throw new DataException("Attempt to read non-atomic value of type: " + datatype);
            }
            return result;
        }

        //////////////////////////////////////////////////
        // Utilities

        protected DapSort
        computesort(Array array)
                throws DataException
        {
            DapSort sort = null;
            Array content = ((Array) getSource());
            switch (content.getDataType()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
            case LONG:
            case UBYTE:
            case USHORT:
            case UINT:
            case ULONG:
            case FLOAT:
            case DOUBLE:
            case STRING:
            case OBJECT:
                return DapSort.ATOMICVARIABLE;
            case STRUCTURE:
                return DapSort.STRUCTURE;
            case SEQUENCE:
                return DapSort.SEQUENCE;
            default:
                break; // sequence is not supported
            }
            throw new DataException("Unsupported datatype: " + content.getDataType());
        }
    }

    /**
     * Provide DSP support for an
     * array of Structure or Sequence
     * instances. There is no corresponding
     * CDMDataAtomicArray because we merge that
     * functionality into one class: CDMDataAtomic.
     */

    static public class CDMDataCompoundArray extends AbstractDataVariable implements DataCompoundArray
    {
        //////////////////////////////////////////////////
        // Instance variables

        protected CDMDSP dsp = null;
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        protected Variable cdmvar = null;
        protected int[] shape = null;
        protected DataCompound[] instances = null;
        protected long defined = 0; // Current defined length of instances

        //////////////////////////////////////////////////
        // Constructors

        public CDMDataCompoundArray(CDMDSP dsp, DapVariable dv)
                throws DataException
        {
            this(dsp, dv, null);
        }

        public CDMDataCompoundArray(CDMDSP dsp, DapVariable dv, Object src)
                throws DataException
        {
            super(dv, dsp, src);
            this.dsp = dsp;
            this.template = dv;
            this.cdmvar = (Variable) dsp.getCDMNode(dv);
            ArrayStructure array = (ArrayStructure) src;
            this.shape = array.getShape();
            if(this.shape.length == 0) this.shape = null; // uniform scalar mark
            // compute shape cross product
            long len = 1;
            if(this.shape != null) {
                for(int i = 0; i < this.shape.length; i++) {
                    len *= this.shape[i];
                }
            }
            instances = new DataCompound[(int) len];
            Arrays.fill(instances, null);
        }

        //////////////////////////////////////////////////
        // DataCompoundArray Interface

        @Override
        public DataSort
        getElementSort()
        {
            if(getTemplate().getSort() == DapSort.SEQUENCE)
                return DataSort.SEQUENCE;
            else
                return DataSort.STRUCTURE;
        }

        @Override
        public void
        addElement(DataCompound instance)
        {
            if(this.defined >= this.instances.length)
                throw new IllegalStateException("too many elements");
            this.instances[(int) this.defined++] = instance;
        }

        @Override
        public long
        getCount() // dimension cross-product
        {
            return this.instances.length;
        }

        // Provide a read of a single value at a given offset in a dimensioned variable.
        @Override
        public DataCompound
        getElement(Index index)
                throws DataException
        {
            int i = (int) index.index();
            ArrayStructure array = (ArrayStructure) getSource();
            if(instances[i] == null)
                instances[i] = new CDMDataStructure(this.dsp, (DapStructure) this.getTemplate(), this, array.getStructureData(i));
            return instances[i];
        }

        /**
         * For this method, the data will be a list of CDMDataStructure
         * or (eventually) CDMDataSequence objects.
         */
        @Override
        public List<DataCompound>
        getElements(List<Slice> slices)
                throws DataException
        {
            // Cannot use array.section on ArrayStructure: not implemented.
            // So we need to simulate it
            long count = DapUtil.sliceProduct(slices);
            List<DataCompound> result = new ArrayList<>((int) count);
            Odometer odom;
            try {
                odom = Odometer.factory(slices, ((DapVariable) this.getTemplate()).getDimensions(), false);
            } catch (DapException de) {
                throw new DataException(de);
            }
            int i;
            ArrayStructure array = (ArrayStructure) getSource();
            for(i = 0; odom.hasNext(); i++) {
                Index index = odom.next();
                int ioffset = (int) index.index();
                if(instances[ioffset] == null) {
                    StructureData data = (StructureData) array.getStructureData(ioffset);
                    instances[ioffset] = new CDMDataStructure(this.dsp, (DapStructure) this.getTemplate(), this, data);
                }
                result.add(instances[ioffset]);
            }
            return result;
        }

        //////////////////////////////////////////////////
        // Utilities

        /**
         * Dynamically create a CDMData{Structure,Sequence} object
         * and cache it
         */

    /*
        protected DapSort
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
        }
    */
    }


    static public class CDMDataDataset extends AbstractData implements DataDataset
    {

        //////////////////////////////////////////////////
        // Instance variables

        protected CDMDSP dsp = null;
        protected Map<DapVariable, DataVariable> variables = new HashMap<DapVariable, DataVariable>();

        //////////////////////////////////////////////////
        // Constructors

        public CDMDataDataset(CDMDSP dsp, DapDataset dataset, Object src)
                throws DataException
        {
            super(dataset, dsp, src);
            this.dsp = dsp;
            dsp.setDataset(this);
        }

        //////////////////////////////////////////////////
        // DataDataset API

        @Override
        public List<DataVariable> getTopVariables()
        {
            return new ArrayList<DataVariable>(variables.values());
        }

        @Override
        public void
        addVariable(DataVariable dv)
        {
            assert (dv.getTemplate() != null);
            variables.put((DapVariable) dv.getTemplate(), dv);
        }

        //////////////////////////////////////////////////
        // DataDataset Interface

        public DataVariable
        getVariableData(DapVariable var)
                throws DataException
        {
            return variables.get(var);
        }

    }


    /**
     * DataRecord represents a record from a sequence.
     * It is effectively equivalent to a Structure instance.
     */

    static public class CDMDataRecord extends CDMDataStructure
    {

        //////////////////////////////////////////////////
        // Constructors

        public CDMDataRecord(CDMDSP dsp, DapSequence dap, CDMDataCompoundArray cdv, StructureData data)
                throws DataException
        {
            super(dsp, dap, cdv, data);
        }
    }


    /**
     * Wrap DSP to project the CDM Meta-Data API.
     * CDMDataset is the top level, root
     * object that manages the whole
     * databuffer part of a CDM wrap of a dap4 response.
     * It is never seen by the client
     * and it is not related to ucar.nc2.Array.
     * The other components of a CDMDataset are:
     * CDMDataCompoundArray
     * CDMDataRecord
     * CDMDataDataset
     * CDMDataset
     * CDMDataStructure
     * CDMDataVariable
     * Note that the data part (equivalent to ucar.ma2.Array)
     * is a separate set of classes (see CDMArray for details).
     */

    static public class CDMDataset
    {
        /////////////////////////////////////////////////////
        // Constants

        /////////////////////////////////////////////////////

        protected Map<CDMNode, Array> arraymap = new HashMap<CDMNode, Array>();

        //////////////////////////////////////////////////
        // Constructor(s)

        /**
         * Constructor
         */
        CDMDataset()
        {

        }

        //////////////////////////////////////////////////
        // Accessors

        public Map<CDMNode, Array> getArrayMap()
        {
            return arraymap;
        }

        public void putArray(CDMNode node, Array array)
        {
            arraymap.put(node, array);
        }

        public Array getArray(CDMNode node)
        {
            return arraymap.get(node);
        }

        //////////////////////////////////////////////////
        // toString

        public String toString()
        {
            StringBuilder buf = new StringBuilder();
            buf.append("Dataset {\n");
            /*if(annotations != null) {
                View.Iterator iter = annotations.getIterator();
                while(iter.hasNext()) {
                    DapVariable dapvar = iter.next().getVariable();
                    if(!dapvar.isTopLevel()) continue;
                    CDMNode cdmnode = nodemap.get(dapvar);
                    Dap4Array array = (Dap4Array) arraymap.get(cdmnode);
                    if(array != null)
                        buf.append(array.toString() + "\n");
                }
            } */
            buf.append("}");
            return buf.toString();
        }

    }

    /**
     * Define DSP support
     * for a single structure instance.
     */

    static public class CDMDataStructure extends AbstractDataVariable implements DataStructure
    {
        //////////////////////////////////////////////////
        // Instance Variables

        protected CDMDSP dsp = null;
        protected CDMDataCompoundArray parent;
        protected byte[] checksum = null;
        protected DapStructure dapstruct = null;
        StructureData cdmdata = null;
        DataVariable[] fieldcache = null;
        List<StructureMembers.Member> members = null;

        //////////////////////////////////////////////////
        // Constructors

        public CDMDataStructure(CDMDSP dsp, DapStructure dap, CDMDataCompoundArray parent, StructureData data)
                throws DataException
        {
            super(dap, dsp, data);
            this.dsp = dsp;
            this.parent = parent;
            this.dapstruct = dap;
            // Locate our Structuredata from our parent
            this.cdmdata = data;
            this.members = cdmdata.getMembers();
            this.fieldcache = new DataVariable[members.size()];
            Arrays.fill(this.fieldcache, null);
        }

        //////////////////////////////////////////////////
        // DataStructure Interface

        // Read named field
        @Override
        public DataVariable getfield(String name) throws DataException
        {
            StructureMembers.Member member = cdmdata.findMember(name);
            return getfield(member);
        }

        public void
        addField(int fieldno, DataVariable dvfield)
        {

        }

        // Read ith field
        @Override
        public DataVariable getfield(int i)
                throws DataException
        {
            if(i < 0 || i >= this.members.size())
                throw new DataException("readfield: index out of bounds: " + i);
            return getfield(this.members.get(i));
        }

        protected DataVariable
        getfield(StructureMembers.Member member)
                throws DataException
        {
            int index = this.members.indexOf(member);
            DapVariable field = this.dapstruct.getField(index);
            if(fieldcache[index] == null) {
                Array array = cdmdata.getArray(member);
                switch (array.getDataType()) {
                case SEQUENCE:
                case STRUCTURE:
                    fieldcache[index] = new CDMDataCompoundArray(dsp, field, (ArrayStructure) array);
                    break;
                default:
                    fieldcache[index] = new CDMDataAtomic(dsp, (DapAtomicVariable) field, array);
                    break;
                }
            }
            return fieldcache[index];
        }

        public DataCompoundArray asCompoundArray()
                throws DataException
        {
            CDMDataCompoundArray dda = new CDMDataCompoundArray((CDMDSP) getDSP(), getVariable());
            dda.addElement(this);
            return dda;
        }

    }

    public interface CDMDataVariable extends DataVariable
    {
    }

}
