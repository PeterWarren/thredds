/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm.dsp;

import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.dap4lib.AbstractData;

import java.util.*;

public class CDMDataDataset extends AbstractData implements DataDataset
{

    //////////////////////////////////////////////////
    // Instance variables

    protected CDMDSP dsp = null;
    protected Map<DapVariable,DataVariable> variables = new HashMap<DapVariable,DataVariable>();

    //////////////////////////////////////////////////
    // Constructors

    public CDMDataDataset(CDMDSP dsp, DapDataset dataset)
        throws DataException
    {
        super(dataset,dsp);
        this.dsp = dsp;
        dsp.setDataDataset(this);
    }

    //////////////////////////////////////////////////
    // DataDataset API

    @Override
    public List<DataVariable>  getTopVariables()
    {
         return new ArrayList<DataVariable>(variables.values());
    }

    @Override
    public void
    addVariable(DataVariable dv)
    {
        assert(dv.getTemplate() != null);
        variables.put((DapVariable)dv.getTemplate(),dv);
    }

    //////////////////////////////////////////////////
    // DataDataset Interface

    public DataVariable
    getVariableData(DapVariable var)
        throws DataException
    {
        return variables.get(var);
    }

}
