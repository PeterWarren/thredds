/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/


package dap4.core.data;

import dap4.core.util.DapException;

public interface DataCompiler
{
    /**
     * The goal here is to process the serialized
     * databuffer and locate variable-specific positions
     * in the serialized databuffer. For each DAP4 variable,
     * Data objects are created and linked together.
     */
    public void compile() throws DapException;


}
