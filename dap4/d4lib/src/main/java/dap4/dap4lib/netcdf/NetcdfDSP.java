/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

/*
TODO:
1. make sure all nodes areproperly annotated
*/


package dap4.dap4lib.netcdf;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import dap4.core.data.DSP;
import dap4.core.data.DataDataset;
import dap4.core.dmr.DMRFactory;
import dap4.core.util.DapContext;
import dap4.core.util.DapException;
import dap4.core.util.DapUtil;
import dap4.dap4lib.AbstractDSP;
import dap4.dap4lib.DapCodes;
import dap4.dap4lib.XURI;

import java.io.IOException;
import java.net.URISyntaxException;

import static dap4.dap4lib.netcdf.DapNetcdf.NC_NOWRITE;

/**
 * DSP for reading netcdf files through jni interface to netcdf4 library
 */
public class NetcdfDSP extends AbstractDSP
{
    //////////////////////////////////////////////////
    // Constants

    static public final boolean DEBUG = false;

    static String PATHSUFFIX = "/src/data";

    static public String[] EXTENSIONS = new String[]{".nc", ".hdf5"};

    // Define reserved attributes
    static public final String UCARTAGVLEN = "^edu.ucar.isvlen";
    static public final String UCARTAGOPAQUE = "^edu.ucar.opaque.size";
    static public final String UCARTAGUNLIM = "^edu.ucar.isunlim";

    static final Pointer NC_NULL = Pointer.NULL;
    static final int NC_FALSE = 0;
    static final int NC_TRUE = 1;
    // "null" id(s)
    static public final int NC_GRPNULL = 0;
    static public final int NC_IDNULL = -1;
    static public final int NC_NOERR = 0;

    static int NC_INT_BYTES = (java.lang.Integer.SIZE / java.lang.Byte.SIZE);
    static int NC_LONG_BYTES = (Native.LONG_SIZE);
    static int NC_POINTER_BYTES = (Native.POINTER_SIZE);
    static int NC_SIZET_BYTES = (Native.SIZE_T_SIZE);

    //////////////////////////////////////////////////
    // com.sun.jna.Memory control

    static abstract class Mem
    {
        static Memory
        allocate(long size)
        {
            if(size == 0)
                throw new IllegalArgumentException("Attempt to allocate zero bytes");
            Memory m = new Memory(size);
            return m;
        }
    }


    //////////////////////////////////////////////////
    // Static variables

    //////////////////////////////////////////////////
    // Static methods

    /**
     * A path is file if it has no base protocol or is file:
     *
     * @param path
     * @param context Any parameters that may help to decide.
     * @return true if this path appears to be processible by this DSP
     */
    static public boolean dspMatch(String path, DapContext context)
    {
        for(String s : EXTENSIONS) {
            if(path.endsWith(s)) return true;
        }
        return false;
    }

    //////////////////////////////////////////////////
    // Instance Variables

    protected DapNetcdf nc4 = null;

    protected boolean trace = false;
    protected boolean closed = false;

    protected int ncid = -1;        // file id ; also set as DSP.source
    protected int format = 0;       // from nc_inq_format
    protected int mode = 0;
    protected String filepath = null; // real path to the dataset

    protected Nc4DMRFactory dmrfactory = null;
    protected Nc4DataFactory datafactory = null;

    protected DataDataset dataset = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public NetcdfDSP()
            throws DapException
    {
        super();
        if(this.nc4 == null) {
            try {
                this.nc4 = NetcdfLoader.load();
            } catch (IOException ioe) {
                throw new DapException(ioe);
            }
            if(this.nc4 == null)
                throw new DapException("Could not load libnetcdf");
        }
        dmrfactory = new Nc4DMRFactory();
        datafactory = new Nc4DataFactory();
    }

    @Override
    public DSP
    open(String filepath)
            throws DapException
    {
        int ret, mode;
        IntByReference ncidp = new IntByReference();
        try {
            mode = NC_NOWRITE;
            Nc4Data.errcheck(nc4, ret = nc4.nc_open(this.filepath, mode, ncidp));
            this.ncid = ncidp.getValue();
            setSource(this.ncid);
            // Figure out what kind of file
            IntByReference formatp = new IntByReference();
            Nc4Data.errcheck(nc4, ret = nc4.nc_inq_format(ncid, formatp));
            this.format = formatp.getValue();
            if(DEBUG)
                System.out.printf("TestNetcdf: open: %s; ncid=%d; format=%d%n",
                        this.filepath, ncid, this.format);
            // Compile the DMR 
            Nc4DMRCompiler dmrcompiler = new Nc4DMRCompiler(this, ncid, dmrfactory);
            this.dmr = dmrcompiler.compile();
            Nc4DataCompiler datacompiler = new Nc4DataCompiler(this, nc4, datafactory);
            datacompiler.compile(); // returns result via setDataset
            return this;
        } catch (Exception t) {
            t.printStackTrace();
        }
        return null;
    }

    @Override
    public void close()
            throws DapException
    {
        if(this.closed) return;
        if(this.ncid < 0) return;
        int ret = nc4.nc_close(ncid);
        Nc4Data.errcheck(nc4, ret);
        closed = true;
        if(trace)
            System.out.printf("NetcdfDSP: closed: %s%n", this.filepath);
    }

    protected DMRFactory getFactory()
    {
        return new Nc4DMRFactory();
    }

    //////////////////////////////////////////////////
    // Accessors
    //////////////////////////////////////////////////
    // Accessors

    public DapNetcdf getJNI()
    {
        return this.nc4;
    }

    @Override
    public String getLocation()
    {
        return this.filepath;
    }

}
