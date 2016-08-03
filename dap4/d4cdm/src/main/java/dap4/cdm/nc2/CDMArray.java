/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm.nc2;

import dap4.core.dmr.*;
import dap4.core.data.DSP;

/**
It is convenient to be able to create 
a common "parent" interface for all
the CDM array classes
*/

/*package*/ interface CDMArray
{
    public DSP getDSP();
    public DapVariable getTemplate();
    public long getSizeBytes(); // In bytes
    public DapType getBaseType();

    //////////////////////////////////////////////////
    // Utilities

    static dap4.core.util.Index
    cdmIndexToIndex(ucar.ma2.Index cdmidx)
    {
	int rank = cdmidx.getRank();
	int[] shape = cdmidx.getShape();
	long[] indices = new long[shape.length];
	for(int i=0;i<rank;i++) indices[i] = shape[i];
	dap4.core.util.Index dapidx = new dap4.core.util.Index(indices,indices);
	return dapidx;
    }
}
