/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.cdm;

import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.DapException;
import dap4.core.util.DapSort;

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

    public DataDataset
    newDataset(DSP dsp, DapDataset template)
    {
        throw new UnsupportedOperationException();
    }

    public DataAtomic
    newAtomic(DSP dsp, DapAtomicVariable template, Object source)
    {
        throw new UnsupportedOperationException();
    }

    public DataRecord
    newRecord(DSP dsp, DapSequence template, Object source)
    {
        throw new UnsupportedOperationException();
    }

    public DataSequence
    newSequence(DSP dsp, DapSequence template, Object source, int index)
    {
        throw new UnsupportedOperationException();
    }

    public DataRecord
    newRecord(DSP dsp, DapSequence template, DataSequence seq, int index)
    {
        throw new UnsupportedOperationException();
    }

    public DataStructure
    newStructure(DSP dsp, DapStructure dap, Object source, int index)
    {
        throw new UnsupportedOperationException();
    }

    public DataVariable
    newVariable(DSP dsp, DapVariable template, Object source)
    {
        throw new UnsupportedOperationException();
    }

    public DataCompoundArray
    newCompoundArray(DSP dsp, DapVariable dapvar)
    {
        throw new UnsupportedOperationException();
    }

}
