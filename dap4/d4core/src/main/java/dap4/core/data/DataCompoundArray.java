/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import dap4.core.dmr.DapType;
import dap4.core.util.DapException;
import dap4.core.util.Index;
import dap4.core.util.Odometer;
import dap4.core.util.Slice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
DataCompoundArray represents an array of
either DataStructure or DataSequence instances.
Note that is is NOT used for SCALARS.
*/

public interface DataCompoundArray extends DataVariable
{
    /**
     * Get the element sort; currently returns only DataSort.{SEQUENCE,STRUCTURE}
      * @return sort
     */
    public DataSort getElementSort();

    /**
     * Get the total number of elements in the variable array.
     * A scalar is treated as a one element array.
     *
     * @return 1 if the variable is scalar, else the product
     *         of the dimensions of the variable.
     */
    public long getCount();

    public void addElement(DataCompound instance);

    public DataCompound getElement(Index indices) throws DataException;
    public DataCompound getElement(long offset) throws DataException;

    /**
        * Default for getElements(Slices)
        *
        * @param slices
        * @return  List of selected elements
        * @throws DataException
        */
       default public List<DataCompound>
       getElements(List<Slice> slices)
               throws DataException
       {
           if(slices == null || slices.size() == 0) { //scalar
               slices = new ArrayList<Slice>();
               Slice slice = new Slice(0, 1, 1);
               slices.add(slice);
           }
           try {
               Odometer odom = Odometer.factory(slices,
                       getVariable().getDimensions(),
                       false);
               List<DataCompound> data = new ArrayList<>((int) odom.totalSize());
               while(odom.hasNext()) {
                   odom.next();
                   Index index = odom.indices();
                   DataCompound o = getElement(index);
                   data.add(o);
               }
               return data;
           } catch (DapException de) {
               throw new DataException(de);
           }
       }
}
