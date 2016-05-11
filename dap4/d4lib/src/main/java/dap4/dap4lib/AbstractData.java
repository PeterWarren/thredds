/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.data.*;
import dap4.core.dmr.DapNode;
import dap4.core.dmr.DapVariable;

abstract public class AbstractData implements Data
{

    //////////////////////////////////////////////////
    // Instance variables

    protected DataSort sort = null;
    protected DapNode template = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    protected AbstractData(DapNode template)
        throws DataException
    {
        this.template = template;
        this.sort = computesort(this);
    }

    //////////////////////////////////////////////////
    // Data Interface

    @Override
    public DataSort
    getSort()
    {
        return sort;
    }

    @Override
    public DapNode
    getTemplate()
    {
        return template;
    }

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
}
