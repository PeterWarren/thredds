/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.data;

import dap4.core.dmr.*;
import dap4.core.util.DapException;
import dap4.core.util.DapSort;

public interface DapDataFactory
{
    DataDataset newDataset(DSP dsp, DapDataset template);
    DataAtomic newAtomic(DSP dsp, DapAtomicVariable template, Object source);
    DataRecord newRecord(DSP dsp, DapSequence template, Object source);
    DataSequence newSequence(DSP dsp, DapSequence template, Object source, int index);
    DataRecord newRecord(DSP dsp, DapSequence template, DataSequence seq, int index);
    DataStructure newStructure(DSP dsp, DapStructure dap, Object source, int index);
    DataVariable newVariable(DSP dsp, DapVariable template, Object source);
    DataCompoundArray newCompoundArray(DSP dsp, DapVariable dapvar);
}
