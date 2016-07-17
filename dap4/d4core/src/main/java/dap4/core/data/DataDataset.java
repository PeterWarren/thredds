/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import dap4.core.ce.CEConstraint;
import dap4.core.dmr.DapVariable;

import java.util.Iterator;
import java.util.List;

/**
DataDataset represents the whole dataset
and is the entry point for walking the data.
*/

public interface DataDataset extends Data
{
    // Get the data associated with a top-level variable, by variable
    public DataVariable getVariableData(DapVariable var) throws DataException;

    public void addVariable(DataVariable dv);

    public List<DataVariable>  getTopVariables();

    public CEConstraint getConstraint();

    public void setConstraint(CEConstraint ce);
}
