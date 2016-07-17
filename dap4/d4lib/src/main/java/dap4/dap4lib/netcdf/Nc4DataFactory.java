/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib.netcdf;

import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.dap4lib.AbstractData;

import static dap4.dap4lib.netcdf.Nc4Notes.*;
import static dap4.dap4lib.netcdf.Nc4Data.*;


public class Nc4DataFactory implements DAPDataFactory
{
    //////////////////////////////////////////////////
    // Constants

    //////////////////////////////////////////////////
    // Constructor(s)

    public Nc4DataFactory()
    {
    }

    /**
     * Transfer Notes annotation
     */

    protected AbstractData
    transfer(AbstractData datanode)
    {
        DapNode dapnode = datanode.getTemplate();
        Notes info = (Notes) dapnode.annotation();
        datanode.annotate(info);
        return datanode;
    }

    //////////////////////////////////////////////////
    // DAPDataFactory API

    @Override
    public DataDataset
    newDataset(DSP dsp, DapDataset template, Object src) throws DataException
    {
        return (DataDataset) transfer(new Nc4Data.Nc4DataDataset(dsp,template,src));
    }

    @Override
    public DataAtomic
    newAtomicVariable(DSP dsp, DapAtomicVariable template, Object source)
            throws DataException
    {
        return (DataAtomic) transfer(new Nc4DataAtomic((NetcdfDSP) dsp, template, source));
    }

    @Override
    public DataSequence
    newSequence(DSP dsp, DapSequence template, DataCompoundArray parent, Object source)
            throws DataException
    {
        return (DataSequence) transfer(new Nc4DataSequence((NetcdfDSP) dsp, template, (DataCompoundArray) parent, (Integer) source));
    }

    @Override
    public DataRecord
    newRecord(DSP dsp, DapSequence template, DataSequence parent, Object source)
            throws DataException
    {
        return (DataRecord) transfer(new Nc4DataRecord((NetcdfDSP) dsp, template, (DataSequence) parent, (Integer) source));
    }

    @Override
    public DataStructure
    newStructure(DSP dsp, DapStructure dap, DataCompoundArray parent, Object source)
            throws DataException
    {
        return (DataStructure) transfer(new Nc4DataStructure((NetcdfDSP) dsp, dap, (DataCompoundArray) parent, (Integer) source));
    }

    @Override
    public DataCompoundArray
    newCompoundArray(DSP dsp, DapVariable dapvar, Object src)
            throws DataException
    {
        return (DataCompoundArray) transfer(new Nc4DataCompoundArray((NetcdfDSP) dsp, dapvar, src));
    }

}
