/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib;

import dap4.core.data.*;
import dap4.core.dmr.*;

public class DefaultDataFactory implements DapDataFactory
{
    //////////////////////////////////////////////////
    // Constants

    //////////////////////////////////////////////////
    // Constructor(s)

    public DefaultDataFactory()
    {
    }

    //////////////////////////////////////////////////
    // DapDataFactory API

    @Override
    public DataDataset
    newDataset(DSP dsp, DapDataset template)
            throws DataException
    {
        return new D4DataDataset((D4DSP)dsp, template);
    }

    @Override
    public DataAtomic
    newAtomicVariable(DSP dsp, DapAtomicVariable template, Object source)
            throws DataException
    {
        return new D4DataAtomic((D4DSP) dsp, template, (Integer)source);
    }

    @Override
    public DataSequence
    newSequence(DSP dsp, DapSequence template, DataCompoundArray parent, Object source)
            throws DataException
    {
        return new D4DataSequence((D4DSP) dsp, template, (D4DataCompoundArray)parent, (Integer)source);
    }

    @Override
    public DataRecord
    newRecord(DSP dsp, DapSequence template, DataSequence parent, Object source)
            throws DataException
    {
        return new D4DataRecord((D4DSP) dsp, template, (D4DataSequence)parent, (Integer)source);
    }

    @Override
    public DataStructure
    newStructure(DSP dsp, DapStructure dap, DataCompoundArray parent, Object source)
            throws DataException
    {
        return new D4DataStructure((D4DSP) dsp, dap, (D4DataCompoundArray)parent, (Integer)source);
    }

    @Override
    public DataCompoundArray
    newCompoundArray(DSP dsp, DapVariable dapvar)
            throws DataException
    {
        return new D4DataCompoundArray((D4DSP) dsp, dapvar);
    }

}
