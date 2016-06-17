/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import dap4.core.dmr.DapDimension;
import dap4.core.dmr.DapType;
import dap4.core.dmr.DapVariable;
import dap4.core.util.DapException;
import dap4.core.util.Index;
import dap4.core.util.Odometer;
import dap4.core.util.Slice;

import java.util.ArrayList;
import java.util.List;

/**
 * DataAtomic represents a non-container object.
 * It includes array info
 * Note that this is the only kind of object
 * that can actually be read; all other objects
 * can be "walked" to eventually reach a DataAtomic instance.
 */

public interface DataAtomic extends DataVariable
{
    /**
     * Get the type of this atomic variable
     *
     * @return the type
     */
    public DapType getType();

    /**
     * Get the total number of elements in the atomic array.
     * A scalar is treated as a one element array.
     *
     * @return 1 if the variable is scalar, else the product
     * of the dimensions of the variable.
     */
    public long getCount(); // dimension product

    /**
     * Get the size of a single element in bytes; 0 => undefined/variable
     *
     * @return size
     */
    public long getElementSize();

    /**
     * Get the size in bytes of this whole object;
     * Normally count*element-size, except for variable size elements.
     *
     * @return size
     */
    public long getSizeBytes();


    default DataSort
    computesort()
    {   // order is important
        if(this instanceof DataAtomic) return DataSort.ATOMIC;
        if(this instanceof DataRecord) return DataSort.RECORD;
        if(this instanceof DataSequence) return DataSort.SEQUENCE;
        if(this instanceof DataStructure) return DataSort.STRUCTURE;
        if(this instanceof DataDataset) return DataSort.DATASET;
        if(this instanceof DataCompoundArray) return DataSort.COMPOUNDARRAY;
        assert false : "Cannot compute sort";
        return null;
    }

    /* Both of the two read functions must be implemented by
     * classes implementing this interface ; the simplest way
     * is to implement one and use the helper function for the other
     */

    /**
     * Read multiple values at once.
     * The returned value (in parameter "data") is some form of java array (e.g. int[]).
     * The type depends on the value of getType().
     * Note that implementations of this interface are free to provide
     * alternate read methods that return values in e.g. a java.nio.Buffer.
     * Note that unsigned types (e.g. UInt64) are returned as a signed version
     * (e.g. Int64), and will have the proper bit pattern for the unsigned value.
     * If the size of the "data" array is not the correct size, then an error
     * will be returned.
     * For opaque data, the result is ByteBuffer[].
     */
    public Object read(List<Slice> slices) throws DataException;

    /**
     * Provide a read of a single value at a given offset in a (possibly dimensioned)
     * atomic valued variable. As mentioned above, unsigned types are returned as a signed version.
     * The type of the returned value is the obvious one (e.g. int->Integer, opaque->ByteBuffer, etc.).
     *
     * @param indices indicate value to read
     * @return the (single) value
     * @throws DataException
     */
    public Object read(Index indices) throws DataException;

    //////////////////////////////////////////////////

    /**
     * Helper function for reading
     */

    /**
     *
     * @param slices
     * @return
     * @throws DataException
     */
    default public Object[]
    readHelper(List<Slice> slices)
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
            Object[] data = new Object[(int) odom.totalSize()];
            while(odom.hasNext()) {
                odom.next();
                Index index = odom.indices();
                Object o = read(index);
                data[(int) index.index()] = o;
            }
            return data;
        } catch (DapException de) {
            throw new DataException(de);
        }
    }

    /**
     * Helper function for read(Index)
     *
     * @param indices
     * @return
     * @throws DataException
     */
    default public Object readHelper(Index indices) throws DataException
    {
        DapVariable var = getVariable();
        List<DapDimension> dims = var.getDimensions();
        List<Slice> slices = Slice.indexToSlices(indices);
        Object[] data = (Object[]) read(slices);
        return data[0];
    }

}
