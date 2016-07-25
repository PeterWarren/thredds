/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.data.*;
import dap4.core.dmr.DapDimension;
import dap4.core.dmr.DapNode;
import dap4.core.dmr.DapVariable;
import dap4.core.util.DapUtil;
import dap4.core.util.Index;

import java.util.List;

abstract public class AbstractData implements Data
{

    //////////////////////////////////////////////////
    // Instance variables

    protected DapNode template = null;
    protected Object state = null;
    protected DSP dsp = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    protected AbstractData(DapNode template, DSP dsp)
        throws DataException
    {
        this.template = template;
        this.sort = computesort(this);
        this.dsp = dsp;
        this.state = state;
    }

    //////////////////////////////////////////////////
    // AnnotatedNode Interface

    protected Object annotation = null;

    public void annotate(Object value)
    {
        annotation = value;
    }

    public Object annotation()
    {
        return annotation;
    }

    //////////////////////////////////////////////////
    // Data Interface

    @Override
    public DataSort
    getSort()
    {
        return this.sort;
    }

    @Override
    public DapNode
    getTemplate()
    {
        return this.template;
    }

    public DSP getDSP() {return this.dsp;}

    //////////////////////////////////////////////////
    // Utilities

    static protected DataSort
    computesort(Object o)
    {   // order is important
        if(o instanceof DataAtomic) return DataSort.ATOMIC;
        if(o instanceof DataRecord) return DataSort.RECORD;
        if(o instanceof DataSequence) return DataSort.SEQUENCE;
        if(o instanceof DataStructure) return DataSort.STRUCTURE;
        if(o instanceof DataDataset) return DataSort.DATASET;
        if(o instanceof DataCompoundArray) return DataSort.COMPOUNDARRAY;
        assert false : "Cannot compute sort";
        return null;
    }


    //////////////////////////////////////////////////

    /**
     * Helper functions for reading
     */


    /**
     * @param slices
     * @return
     * @throws DataException
     */
    default public Object[]
    readHelper(List<Slice> slices)
            throws DataException
    {
        assert(slices != null);
        if(slices.size() == 0) { //scalar
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
     * Provide a helper function to convert an Index object to
     * a slice list.
     *
     * @param indices indicate value to read
     * @param template variable template
     * @return corresponding List<Slice>
     * @throws DataException
     */

    public List<Slice>
    indexToSlices(Index indices, DapVariable template)
	throws DataException
    {
        List<DapDimension> dims = template.getDimensions();
        List<Slice> slices = Slice.indexToSlices(indices);
	return slices;
    }

    /**
     * Provide a helper function to convert an offset  to
     * a slice list.
     *
     * @param offset
     * @param template variable template
     * @return
     * @throws DataException
     */
    public List<Slice>
    offsetToSlices(long offset, DapVariable template)
	throws DataException
    {
        List<DapDimension> dims = template.getDimensions();
        long[] dimsizes = DapUtil.getDimSizes(dims);
        return indexToSlices(Index.offsetToIndex(offset,dimsizes));
    }
}

