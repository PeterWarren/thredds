/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.ce.CEConstraint;
import dap4.core.data.*;
import dap4.core.dmr.DapNode;
import dap4.core.dmr.DapVariable;

abstract public class AbstractDataDataset extends AbstractData
                                      implements DataDataset
{
    //////////////////////////////////////////////////
    // Instance variables

    protected CEConstraint ce = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    protected AbstractDataDataset(DapNode template, DSP dsp, Object src)
        throws DataException
    {
	super(template,dsp,src);
    }

    //////////////////////////////////////////////////
    // Accessors

    @Override
    public CEConstraint getConstraint() {return this.ce;}

    @Override
    public void setConstraint(CEConstraint ce) {this.ce = ce;}
}
