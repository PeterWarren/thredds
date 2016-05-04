/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4shared;

import dap4.core.data.*;
import dap4.core.dmr.DapNode;
import dap4.core.dmr.DapVariable;

public interface AbstractData extends Data
{

    //////////////////////////////////////////////////
    // Instance variables

//    protected DataSort sort = null;
//    protected DapNode template = null;

    //////////////////////////////////////////////////
    // Constructor(s)

/*
    protected AbstractData(DapNode dv)
        throws DataException
    {
        this.template = dv;
        this.sort = computesort();
    }
*/
    //////////////////////////////////////////////////
    // Data Interface

    public DataSort
    getSort();
//        return sort;

    public DapNode
    getTemplate();
//        return template;

    //////////////////////////////////////////////////
    // Utilities

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
}
