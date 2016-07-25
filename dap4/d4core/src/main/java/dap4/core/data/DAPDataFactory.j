/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.data;

import dap4.core.dmr.*;

public interface DAPDataFactory
{
    DataDataset newDataset(DSP dsp, DapDataset template) throws DataException;
    DataAtomic newAtomicVariable(DSP dsp, DapAtomicVariable template) throws DataException;
    DataSequence newSequence(DSP dsp, DapSequence template, DataCompoundArray parent) throws DataException;
    DataRecord newRecord(DSP dsp, DapSequence template, DataSequence seq) throws DataException;
    DataStructure newStructure(DSP dsp, DapStructure dap, DataCompoundArray parent) throws DataException;
    DataCompoundArray newCompoundArray(DSP dsp, DapVariable dapvar) throws DataException;
    DataField newField(DSP dsp, DapAtomicVariable template) throws DataException;
}
