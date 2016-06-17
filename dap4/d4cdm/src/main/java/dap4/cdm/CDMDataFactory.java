/* Copyright 2012-2016, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm;

import dap4.cdm.dsp.*;
import dap4.core.data.*;
import dap4.core.dmr.*;
import ucar.ma2.StructureData;

public class CDMDataFactory implements DAPDataFactory
{
    //////////////////////////////////////////////////
    // Constants

    //////////////////////////////////////////////////
    // Constructor(s)

    public CDMDataFactory()
    {
    }

    //////////////////////////////////////////////////
    // DAPDataFactory API

    @Override
    public DataDataset
    newDataset(DSP dsp, DapDataset template, Object src)
            throws DataException
    {
        return new CDMDataDataset((CDMDSP) dsp, template, src);
    }

    @Override
    public DataAtomic
    newAtomicVariable(DSP dsp, DapAtomicVariable template, Object source)
            throws DataException
    {
        return new CDMDataAtomic(dsp, template, source);
    }

    @Override
    public DataSequence
    newSequence(DSP dsp, DapSequence template, DataCompoundArray parent, Object source)
            throws DataException
    {
	throw new UnsupportedOperationException();
    }

    @Override
    public DataRecord
    newRecord(DSP dsp, DapSequence template, DataSequence parent, Object source)
            throws DataException
    {
	throw new UnsupportedOperationException();
    }

    @Override
    public DataStructure
    newStructure(DSP dsp, DapStructure dap, DataCompoundArray parent, Object source)
            throws DataException
    {
        return new CDMDataStructure((CDMDSP) dsp, dap, (CDMDataCompoundArray)parent, (StructureData)source);
    }

    @Override
    public DataCompoundArray
    newCompoundArray(DSP dsp, DapVariable dapvar, Object src)
            throws DataException
    {
        return new CDMDataCompoundArray((CDMDSP) dsp, dapvar, src);
    }

}
