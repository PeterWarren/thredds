/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm.dsp;

import dap4.core.data.*;
import dap4.core.dmr.DapStructure;
import dap4.core.dmr.DapVariable;
import dap4.core.util.*;
import dap4.core.util.Index;
import dap4.dap4lib.AbstractDataVariable;
import ucar.ma2.*;
import ucar.nc2.Variable;

import java.util.*;

/**
 * Provide DSP support for an
 * array of Structure or Sequence
 * instances. There is no corresponding
 * CDMDataAtomicArray because we merge that
 * functionality into one class: CDMDataAtomic.
 */

public class CDMDataCompoundArray extends AbstractDataVariable implements DataCompoundArray
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
        this(dsp,dv,null);
    }

    public CDMDataCompoundArray(CDMDSP dsp, DapVariable dv, Object src)
        throws DataException
    {
        super(dv,dsp,src);
        this.dsp = dsp;
        this.template = dv;
        this.cdmvar = (Variable) dsp.getCDMNode(dv);
        ArrayStructure array = (ArrayStructure)src;
        this.shape = array.getShape();
        if(this.shape.length == 0) this.shape = null; // uniform scalar mark
        // compute shape cross product
        long len = 1;
        if(this.shape != null) {
            for(int i = 0;i < this.shape.length;i++)
                len *= this.shape[i];
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
        this.instances[(int)this.defined++] = instance;
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
        int i = (int)index.index();
        ArrayStructure array = (ArrayStructure)getSource();
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
        List<DataCompound> result = new ArrayList<>((int)count);
        Odometer odom;
        try {
            odom = Odometer.factory(slices,((DapVariable)this.getTemplate()).getDimensions(),false);
        } catch (DapException de) {
            throw new DataException(de);
        }
        int i;
        ArrayStructure array = (ArrayStructure)getSource();
        for(i=0;odom.hasNext();i++) {
            Index index = odom.next();
            int ioffset = (int)index.index();
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
