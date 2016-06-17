/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.data.DSP;
import dap4.core.data.DataException;
import dap4.core.data.DataVariable;
import dap4.core.dmr.DapDimension;
import dap4.core.dmr.DapNode;
import dap4.core.dmr.DapVariable;
import dap4.core.util.Index;
import dap4.core.util.Slice;

import java.util.List;

abstract public class AbstractDataVariable extends AbstractData
        implements DataVariable
{
    //////////////////////////////////////////////////
    // Constructor(s)

    protected AbstractDataVariable(DapNode template, DSP dsp, Object src)
            throws DataException
    {
        super(template, dsp, src);
    }

    //////////////////////////////////////////////////
    // DataVariable Interface

    public DapVariable getVariable()
    {
        return (DapVariable) getTemplate();
    }

}
