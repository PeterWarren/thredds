/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm.dsp;

import dap4.cdm.CDMUtil;
import dap4.core.data.DSP;
import dap4.core.data.DataAtomic;
import dap4.core.data.DataException;
import dap4.core.dmr.DapAtomicVariable;
import dap4.core.dmr.DapType;
import dap4.core.dmr.DapVariable;
import dap4.core.dmr.TypeSort;
import dap4.core.util.*;
import dap4.dap4lib.AbstractDataVariable;
import dap4.dap4lib.Dap4Util;
import ucar.ma2.Array;
import ucar.ma2.DataType;

import java.util.List;


public class CDMDataAtomic extends AbstractDataVariable implements CDMDataVariable, DataAtomic
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
        super(template,dsp,array);
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
        return Dap4Util.daptypeSize(this.atomtype);
    }

    @Override
    public long
    getSizeBytes()
    {
        return ((Array)getSource()).getSizeBytes();
    }

    @Override
    public Object
    read(List<Slice> slices)
        //read(long start, long count, Object data, long offset)
            throws DataException
    {
        Array array = ((Array)getSource());
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
            Object[] data = new Object[(int)odom.totalSize()];
            while(odom.hasNext()) {
                Index index = odom.next();
                long offset = index.index();
                System.arraycopy(content, (int) offset, data, (int)offset, 1);
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
        Array content = ((Array)getSource());
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
        Array content = ((Array)getSource());
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
