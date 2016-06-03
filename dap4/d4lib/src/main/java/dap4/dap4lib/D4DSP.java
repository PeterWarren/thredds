/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.data.DataCompiler;
import dap4.core.data.DataDataset;
import dap4.core.dmr.DapDataset;
import dap4.core.util.DapException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * DAP4 Serial to DSP interface
 * This code should be completely independent of thredds.
 * Its goal is to provide a DSP interface to
 * a sequence of bytes representing serialized data, possibly
 * including a leading DMR.
 */

abstract public class D4DSP extends AbstractDSP
{

    //////////////////////////////////////////////////
    // Constants

    static public boolean DEBUG = false;

    static protected final String DAPVERSION = "4.0";
    static protected final String DMRVERSION = "1.0";

    //////////////////////////////////////////////////
    // Constructor(s)

    public D4DSP()
    {
        super();
    }

    //////////////////////////////////////////////////
    // DSP API
    // Most is left to be subclass defined; 

    @Override
    public DataDataset
    getDataDataset()
    {
        return super.getDataDataset();
    }

    //////////////////////////////////////////////////
    // (Other) Accessors

    public void setDataDataset(D4DataDataset data)
    {
        super.setDataDataset(data);
    }

    protected void
    build(String document, byte[] serialdata, ByteOrder order)
            throws DapException
    {
        build(parseDMR(document), serialdata, order);
    }

    /**
     * Build the data from the incoming serial data
     * Note that some DSP's will not use
     *
     * @param dmr
     * @param serialdata
     * @param order
     * @throws DapException
     */
    protected void
    build(DapDataset dmr, byte[] serialdata, ByteOrder order)
            throws DapException
    {
        this.dmr = dmr;
        // "Compile" the databuffer section of the server response
        this.databuffer = ByteBuffer.wrap(serialdata).order(order);
        DataCompiler compiler = new D4DataCompiler(this, checksummode, this.databuffer, new DefaultDataFactory());
        compiler.compile();
    }

}
