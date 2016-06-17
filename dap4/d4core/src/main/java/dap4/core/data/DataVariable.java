/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import dap4.core.dmr.DapDimension;
import dap4.core.dmr.DapVariable;
import dap4.core.util.DapException;
import dap4.core.util.Index;
import dap4.core.util.Odometer;
import dap4.core.util.Slice;

import java.util.ArrayList;
import java.util.List;

/**
 * DataVariable is common element for all kinds of variables:
 * -DataAtomic
 * -DataCompoundArray
 * -DataStructure
 * -DataSequence
 */

public interface DataVariable extends Data
{
    public DapVariable getVariable();
}