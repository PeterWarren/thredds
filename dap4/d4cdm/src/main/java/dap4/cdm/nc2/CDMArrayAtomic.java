/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm.nc2;

import dap4.cdm.CDMUtil;
import dap4.core.data.*;
import dap4.core.dmr.DapType;
import dap4.core.dmr.DapVariable;
import dap4.core.util.*;
import dap4.dap4lib.Dap4Util;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.IndexIterator;
import ucar.nc2.Group;

import java.io.IOException;
import java.util.List;

/**
 * CDMArrayAtomic wraps a DataAtomic object to present
 * the ucar.ma2.Array interface.
 * CDMArrayAtomic manages a single CDM atomic variable:
 * either top-level or for a member.
 */

public class CDMArrayAtomic extends Array implements CDMArray, DataAtomic
{
    /////////////////////////////////////////////////////
    // Constants

    /////////////////////////////////////////////////////
    // Instance variables

    protected DataSort sort = null;
    protected DapVariable template = null;

    // CDMArray variables
    protected DataDataset root = null;
    protected Group cdmroot = null;
    protected DSP dsp = null;
    protected DapType basetype = null;

    protected DataAtomic data = null;
    protected int elementsize = 0;    // of one element
    protected long dimsize = 0;        // # of elements in array; scalar uses value 1
    protected long totalsize = 0;      // elementsize*dimsize except when isbytestring

    //////////////////////////////////////////////////
    // Constructor(s)

    /**
     * Constructor
     *
     * @param dsp  the parent DSP
     * @param data the dap4 databuffer object that provided the actual databuffer
     */
    CDMArrayAtomic(DSP dsp, Group cdmroot, DataAtomic data)
            throws DapException
    {
        super(CDMUtil.daptype2cdmtype(((DapVariable) data.getTemplate()).getBaseType()),
                CDMUtil.computeEffectiveShape(((DapVariable) data.getTemplate()).getDimensions()));
        this.dsp = dsp;
        this.root = dsp.getDataDataset();
        this.data = data;
        this.template = (DapVariable) this.data.getTemplate();
        this.basetype = this.template.getBaseType();
        this.sort = computesort();

        this.dimsize = DapUtil.dimProduct(this.template.getDimensions());
        this.elementsize = this.basetype.getSize();
    }

    // ///////////////////////////////////////////////
    // CDMArray Interface

    @Override
    public DapType
    getBaseType()
    {
        return this.basetype;
    }

    @Override
    public DSP getDSP()
    {
        return this.dsp;
    }

    @Override
    public DataDataset getRoot()
    {
        return this.root;
    }

    @Override
    public DapVariable getTemplate()
    {
        return this.template;
    }

    @Override
    public long getSizeBytes()
    {
        return this.data.getSizeBytes();
    }

    //////////////////////////////////////////////////
    // Accessors

    public DataAtomic getData()
    {
        return data;
    }

    //////////////////////////////////////////////////
    // Utilities

    void
    setup(int index)
    {
        if(index < 0 || index > dimsize)
            throw new IndexOutOfBoundsException("Dap4Array: " + index);
    }

    //////////////////////////////////////////////////
    // Array Interface

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        DapType basetype = getBaseType();
        String sbt = (basetype == null ? "?" : basetype.toString());
        String st = (template == null ? "?" : template.getShortName());
        buf.append(String.format("%s %s[%d]", sbt, st, dimsize));
        return buf.toString();
    }

    //////////////////////////////////////////////////
    // Array API
    // TODO: add index range checks

    public Class
    getElementType()
    {
        DataType dt = CDMUtil.daptype2cdmtype(this.basetype);
        if(dt == null)
            throw new IllegalArgumentException("Unknown datatype: " + this.basetype);
        return CDMUtil.cdmElementClass(dt);
    }

    /**
     * Get the array element at a specific index as a double
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to double if necessary.
     */
    public double getDouble(int index)
    {
        setup(index);
        try {
            Object value = this.data.read(index);
            return Convert.doubleValue(this.basetype, value);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as a float
     * converting as needed.
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to float if necessary.
     */
    public float getFloat(int index)
    {
        setup(index);
        try {
            Object value = this.data.read(index);
            return (float) Convert.doubleValue(this.basetype, value);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as a long
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to long if necessary.
     */
    public long getLong(int index)
    {
        setup(index);
        try {
            Object value = this.data.read(index);
            return Convert.longValue(this.basetype, value);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as a integer
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to integer if necessary.
     */
    public int getInt(int index)
    {
        setup(index);
        try {
            Object value = this.data.read(index);
            return (int) Convert.longValue(this.basetype, value);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as a short
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to short if necessary.
     */
    public short getShort(int index)
    {
        setup(index);
        try {
            Object value = this.data.read(index);
            return (short) Convert.longValue(this.basetype, value);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as a byte
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to byte if necessary.
     */
    public byte getByte(int index)
    {
        setup(index);
        try {
            Object value = this.data.read(index);
            return (byte) (Convert.longValue(basetype, value) & 0xFFL);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as a char
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to char if necessary.
     */
    public char getChar(int index)
    {
        setup(index);
        try {
            Object value = this.read(index);
            return (char) (Convert.longValue(basetype, value) & 0xFFL);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as a boolean
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to char if necessary.
     */
    public boolean getBoolean(int index)
    {
        setup(index);
        try {
            Object value = this.data.read(index);
            return (Convert.longValue(basetype, value) != 0);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    /**
     * Get the array element at a specific index as an Object
     *
     * @param index of element to get
     * @return value at <code>index</code> cast to Object if necessary.
     */
    public Object getObject(int index)
    {
        setup(index);
        try {
            return data.read(index);
        } catch (IOException ioe) {
            throw new IndexOutOfBoundsException(ioe.getMessage());
        }
    }

    // Convert index base to int based
    public double getDouble(Index idx)
    {
        return getDouble((int) (idx.currentElement()));
    }

    public float getFloat(Index idx)
    {
        return getFloat((int) (idx.currentElement()));
    }

    public long getLong(Index idx)
    {
        return getLong((int) (idx.currentElement()));
    }

    public int getInt(Index idx)
    {
        return getInt((int) (idx.currentElement()));
    }

    public short getShort(Index idx)
    {
        return getShort((int) (idx.currentElement()));
    }

    public byte getByte(Index idx)
    {
        return getByte((int) (idx.currentElement()));
    }

    public char getChar(Index idx)
    {
        return getChar((int) (idx.currentElement()));
    }

    public boolean getBoolean(Index idx)
    {
        return getBoolean((int) (idx.currentElement()));
    }

    public Object getObject(Index idx)
    {
        return getObject((int) (idx.currentElement()));
    }

    // Unsupported Methods

    public void setDouble(Index ima, double value)
    {
        throw new UnsupportedOperationException();
    }

    public void setFloat(Index ima, float value)
    {
        throw new UnsupportedOperationException();
    }

    public void setLong(Index ima, long value)
    {
        throw new UnsupportedOperationException();
    }

    public void setInt(Index ima, int value)
    {
        throw new UnsupportedOperationException();
    }

    public void setShort(Index ima, short value)
    {
        throw new UnsupportedOperationException();
    }

    public void setByte(Index ima, byte value)
    {
        throw new UnsupportedOperationException();
    }

    public void setChar(Index ima, char value)
    {
        throw new UnsupportedOperationException();
    }

    public void setBoolean(Index ima, boolean value)
    {
        throw new UnsupportedOperationException();
    }

    public void setObject(Index ima, Object value)
    {
        throw new UnsupportedOperationException();
    }

    public void setDouble(int elem, double value)
    {
        throw new UnsupportedOperationException();
    }

    public void setFloat(int elem, float value)
    {
        throw new UnsupportedOperationException();
    }

    public void setLong(int elem, long value)
    {
        throw new UnsupportedOperationException();
    }

    public void setInt(int elem, int value)
    {
        throw new UnsupportedOperationException();
    }

    public void setShort(int elem, short value)
    {
        throw new UnsupportedOperationException();
    }

    public void setByte(int elem, byte value)
    {
        throw new UnsupportedOperationException();
    }

    public void setChar(int elem, char value)
    {
        throw new UnsupportedOperationException();
    }

    public void setBoolean(int elem, boolean value)
    {
        throw new UnsupportedOperationException();
    }

    public void setObject(int elem, Object value)
    {
        throw new UnsupportedOperationException();
    }

    public Object getStorage()
    {
        throw new UnsupportedOperationException();
    }

    protected void copyTo1DJavaArray(IndexIterator indexIterator, Object o)
    {
        throw new UnsupportedOperationException();
    }

    protected void copyFrom1DJavaArray(IndexIterator iter, Object javaArray)
    {
        throw new UnsupportedOperationException();
    }

    protected Array createView(Index index)
    {
        return this;
    }

    //////////////////////////////////////////////////
    // Extended interface to support array extraction

    /**
     * Extract a java array of
     * this.basetype and
     * convert it to the specified
     * dsttype, and return the resulting
     * array as an Object.
     * <p>
     * param dsttype Return a java array of these.
     * param dimsize Return this many consecutive elements
     *
     * @return a java array corresponding to dsttype
     */
/*
    public Object
    getArray(DapType dsttype, int dimsize)
        throws DataException
    {
        TypeSort srctype = this.basetype.getPrimitiveType();
        TypeSort dstatomtype = dsttype.getPrimitiveType();
        Object array =
            Dap4Util.extractVector(this.data, 0, dimsize);
        if(dstatomtype != srctype) {
            // dst type and src type differ => Convert
            array = CDMUtil.convertVector(dsttype, basetype, array);
        }
        return array;
    }
    */

    //////////////////////////////////////////////////
    // DataAtomic Interface
    public DapVariable getVariable()
    {
        return template;
    }

    public DapType getType()
    {
        return this.basetype;
    }

    @Override
    public long getCount()
    {
        return dimsize;
    }

    @Override
    public DataSort
    getSort()
    {
        return this.sort;
    }

    @Override
    public long getElementSize()
    {
        return Dap4Util.daptypeSize(this.basetype.getTypeSort());
    }

    @Override
    public void
    read(List<Slice> slices, Object data, long offset)
        //read(long start, long count, Object data, long offset)
            throws DataException
    {
        Array array = (Array) this.data;
        // If content.getDataType returns object, then we
        // really do not know its true datatype. So, as a rule,
        // we will rely on this.basetype.
        DataType datatype = CDMUtil.daptype2cdmtype(this.basetype);
        if(datatype == null)
            throw new DataException("Unknown basetype: " + this.basetype);
        Class elementclass = CDMUtil.cdmElementClass(datatype);
        if(elementclass == null)
            throw new DataException("Attempt to read non-atomic value of type: " + datatype);
        Object content = array.get1DJavaArray(elementclass); // not very efficient
        try {
            Odometer odom = Odometer.factory(slices, ((DapVariable) this.getTemplate()).getDimensions(), false);
            while(odom.hasNext()) {
                long index = odom.next();
                System.arraycopy(content, (int) index, data, (int) offset, 1);
                offset++;
            }
        } catch (DapException de) {
            throw new DataException(de);
        }
    /*
        switch (datatype) {
            case BOOLEAN:
    	    boolean[] bovector = (boolean[])vector;

    	    break;
            case BYTE:
    	    byte[] byvector = (byte[])vector;
    	    break;
            case CHAR:
    	    char[] chvector = (char[])vector;
    	    break;
            case SHORT:
    	    short[] shvector = (short[])vector;
    	    break;
            case INT:
    	    int[] invector = (int[])vector;
    	    break;
            case LONG:
    	    long[] lovector = (long[])vector;
    	    break;
            case FLOAT:
    	    float[] flvector = (float[])vector;
    	    break;
            case DOUBLE:
    	    double[] dovector = (double[])vector;
    	    break;
            case STRING:
    	    string[] stvector = (string[])vector;
    	    break;
            case OBJECT:
    	    object[] obvector = (object[])vector;
    	    break;
            case STRUCTURE:
            case SEQUENCE:
            default:
    	    throw new DataException("Attempt to read non-atomic value of type: "+datatype);
            }
    	return result;
    */
    }

    @Override
    public Object
    read(long index)
            throws DataException
    {
        int i = (int) index;
        Array content = (Array) this.data;
        DataType datatype = content.getDataType();
        return read(index, datatype, content);
    }

    protected Object
    read(long index, DataType datatype, Array content)
            throws DataException
    {
        Object result;
        int i = (int) index;
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
        case ENUM1:
            result = read(index, DataType.BYTE, content);
            break;
        case ENUM2:
            result = read(index, DataType.SHORT, content);
            break;
        case ENUM4:
            result = read(index, DataType.INT, content);
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
        Array content = (Array) this.data;
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

