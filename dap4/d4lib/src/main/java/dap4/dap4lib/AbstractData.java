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
    protected DSP dsp = null;
    protected Object source = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    protected AbstractData(DapNode template, DSP dsp, Object src)
        throws DataException
    {
        this.template = template;
        this.sort = computesort(this);
        this.dsp = dsp;
        this.source = src;
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

    public Object getSource() {return this.source;}

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
