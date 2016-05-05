/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.d4ts;

import dap4.core.util.DapException;
import dap4.core.util.DapUtil;
import dap4.dap4lib.DapLog;
import dap4.dap4lib.FileDSP;
import dap4.servlet.*;
import ucar.httpservices.HTTPUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class D4TSServlet extends DapController
{

    //////////////////////////////////////////////////
    // Constants

    static final boolean DEBUG = false;

    static final boolean PARSEDEBUG = false;

    static final String TESTDATADIR = "/WEB-INF/resources/testfiles"; // relative to resource path

    //////////////////////////////////////////////////
    // Type Decls

    static class D4TSFactory extends DSPFactory
    {

        public D4TSFactory()
        {
            // Register known DSP classes: order is important.
            // Only used in server
            registerDSP(SynDSP.class, true);
            //registerDSP(NetcdfDSP.class, true);
            registerDSP(FileDSP.class, true);
        }

    }

    //////////////////////////////////////////////////

    static {
        DapCache.setFactory(new D4TSFactory());
    }

    //////////////////////////////////////////////////
    // Instance variables

    ServletContext cxt = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public D4TSServlet()
    {
        super("d4ts");
    }

    @Override
    public void initialize()
    {
        DapLog.info("Initializing d4ts servlet");
        cxt = getServletContext();
    }

    //////////////////////////////////////////////////

    @Override
    protected void doGet(HttpServletRequest req,
                         HttpServletResponse resp)
            throws ServletException,
            java.io.IOException
    {
        super.handleRequest(req, resp);
    }

    //////////////////////////////////////////////////////////
    // Capabilities processors

    @Override
    protected void
    doFavicon(DapRequest drq, String icopath)
            throws IOException
    {
        String favfile = getResourcePath(drq, icopath);
        if(favfile != null) {
            try (FileInputStream fav = new FileInputStream(favfile);) {
                byte[] content = DapUtil.readbinaryfile(fav);
                OutputStream out = drq.getOutputStream();
                out.write(content);
            }
        }
    }

    @Override
    protected void
    doCapabilities(DapRequest drq)
            throws IOException
    {
        addCommonHeaders(drq);

        // Figure out the directory containing
        // the files to display.
        String dir = getResourcePath(drq, "");
        if(dir == null)
            throw new DapException("Cannot locate resources directory");

        // Generate the front page
        FrontPage front = new FrontPage(dir, drq);
        String frontpage = front.buildPage();

        if(frontpage == null)
            throw new DapException("Cannot create front page")
                    .setCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        // // Convert to UTF-8 and then to byte[]
        byte[] frontpage8 = DapUtil.extract(DapUtil.UTF8.encode(frontpage));

        OutputStream out = drq.getOutputStream();
        out.write(frontpage8);

    }

    @Override
    public String
    getResourcePath(DapRequest drq, String relativepath)
            throws IOException
    {
        // Using context information, we need to
        // construct a file path to the specified dataset
        String suffix = DapUtil.denullify(HTTPUtil.canonicalpath(relativepath));
        String datasetfilepath = TESTDATADIR + HTTPUtil.abspath(suffix);
        datasetfilepath = cxt.getRealPath(datasetfilepath);

        // See if it really exists and is readable and of proper type
        File dataset = new File(datasetfilepath);
        if(!dataset.exists())
            throw new DapException("Requested file does not exist: " + datasetfilepath)
                    .setCode(HttpServletResponse.SC_NOT_FOUND);

        if(!dataset.canRead())
            throw new DapException("Requested file not readable: " + datasetfilepath)
                    .setCode(HttpServletResponse.SC_FORBIDDEN);
        return datasetfilepath;
    }

    @Override
    public long getBinaryWriteLimit()
    {
        return DEFAULTBINARYWRITELIMIT;
    }

    @Override
    public String
    getServletID()
    {
        return "d4ts";
    }
}
