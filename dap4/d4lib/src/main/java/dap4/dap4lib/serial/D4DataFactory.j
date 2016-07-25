/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.serial;

import dap4.core.data.*;
import dap4.core.dmr.*;

import static dap4.dap4lib.serial.D4Data.*;

public class D4DataFactory implements DAPDataFactory
{
    //////////////////////////////////////////////////
    // Constants

    //////////////////////////////////////////////////
    // Constructor(s)

    public D4DataFactory()
    {
    }

    //////////////////////////////////////////////////
    // DAPDataFactory API

    @Override
    public DataDataset
    newDataset(DSP dsp, DapDataset template, Object src)
            throws DataException
    {
        return new D4Data.D4DataDataset((D4DSP)dsp, template, src);
    }

    @Override
    public DataAtomic
    newAtomicVariable(DSP dsp, DapAtomicVariable template, Object source)
            throws DataException
    {
        return new D4DataAtomic(dsp, template, source);
    }

    @Override
    public DataSequence
    newSequence(DSP dsp, DapSequence template, DataCompoundArray parent, Object source)
            throws DataException
    {
        return new D4DataSequence(dsp, template, parent, source);
    }

    @Override
    public DataRecord
    newRecord(DSP dsp, DapSequence template, DataSequence parent, Object source)
            throws DataException
    {
        return new D4DataRecord(dsp, template, (DataSequence)parent, source);
    }

    @Override
    public DataStructure
    newStructure(DSP dsp, DapStructure dap, DataCompoundArray parent, Object source)
            throws DataException
    {
        return new D4DataStructure(dsp, dap, (DataCompoundArray)parent, source);
    }

    @Override
    public DataCompoundArray
    newCompoundArray(DSP dsp, DapVariable dapvar, Object src)
            throws DataException
    {
        return new D4DataCompoundArray(dsp, dapvar, src);
    }


}
