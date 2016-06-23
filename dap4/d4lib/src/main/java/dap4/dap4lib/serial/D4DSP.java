/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib.serial;

import dap4.core.data.DataCompiler;
import dap4.core.data.DataDataset;
import dap4.core.dmr.DMRFactory;
import dap4.core.dmr.DapDataset;
import dap4.core.util.DapException;
import dap4.dap4lib.AbstractDSP;

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
    // Instance variables

    protected ByteBuffer databuffer = null; // local copy of AbstractDSP.getSource

    //////////////////////////////////////////////////
    // Constructor(s)

    public D4DSP()
    {
        super();
    }

    //////////////////////////////////////////////////

    protected DMRFactory getFactory() {return new D4DMRFactory();}

    //////////////////////////////////////////////////
    // DSP API
    // Most is left to be subclass defined; 

    //////////////////////////////////////////////////
    // (Other) Accessors

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
        setDMR(dmr);
        // "Compile" the databuffer section of the server response
        setSource(ByteBuffer.wrap(serialdata).order(order));
        this.databuffer = (ByteBuffer)getSource();
        DataCompiler compiler = new D4DataCompiler(this, checksummode, this.databuffer, new D4DataFactory());
        compiler.compile();
    }

}
