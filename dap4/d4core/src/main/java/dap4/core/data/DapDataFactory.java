/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.data;

import dap4.core.dmr.*;

public interface DAPDataFactory
{
    DataDataset newDataset(DSP dsp, DapDataset template, Object src) throws DataException;
    DataAtomic newAtomicVariable(DSP dsp, DapAtomicVariable template, Object source) throws DataException;
    DataSequence newSequence(DSP dsp, DapSequence template, DataCompoundArray parent, Object source) throws DataException;
    DataRecord newRecord(DSP dsp, DapSequence template, DataSequence seq, Object source) throws DataException;
    DataStructure newStructure(DSP dsp, DapStructure dap, DataCompoundArray parent, Object source) throws DataException;
    DataCompoundArray newCompoundArray(DSP dsp, DapVariable dapvar, Object src) throws DataException;
}
