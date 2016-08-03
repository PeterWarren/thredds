package dap4.test;

import dap4.core.data.DSP;
import dap4.core.util.DapContext;
import dap4.core.util.DapException;
import dap4.core.util.DapUtil;
import dap4.dap4lib.DMRPrinter;
import dap4.dap4lib.DSPPrinter;
import dap4.dap4lib.FileDSP;
import dap4.dap4lib.HttpDSP;
import dap4.dap4lib.netcdf.Nc4DSP;
import dap4.servlet.DapCache;
import dap4.servlet.SynDSP;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import ucar.unidata.util.test.TestDir;
import ucar.unidata.util.test.category.NeedsExternalResource;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Test some of the DSP classes: FileDSP, SynDSP, and HttpDSP.
 * CDMDSP is tested separately in TestServlet.
 */

public class TestDSP extends DapTestCommon
{
    static final boolean DEBUG = false;

    static final String TESTEXTENSION = "txt";

    //////////////////////////////////////////////////
    // Constants

    static final String DATADIR = "src/test/data/"; // relative to dap4 root
    static final String TESTDATADIR = DATADIR + "/resources";
    static final String BASELINEDIR = "TestDSP/baseline";
    static final String TESTDSPINPUT = "TestDSP/testinput";
    static final String TESTCLIENTINPUT = "TestCDMClient/testinput";
    static final String TESTFILESINPUT = "testfiles";

    //////////////////////////////////////////////////
    // Type Declarations

    static class TestCase
    {
        static protected String root = null;

        static void setRoot(String r)
        {
            root = r;
        }

        /////////////////////////

        String title;
        String dataset;
        boolean checksumming;
        String testurl;
        String baselinepath;

        TestCase(String prefix, String path)
        {
            this.title = dataset;
            this.dataset = dataset;
            this.checksumming = false;
            this.testurl = prefix + "/" + path;
            this.baselinepath
                    = root + "/" + BASELINEDIR + "/" + path + "." + TESTEXTENSION;
        }

        public TestCase checksum()
        {
            this.checksumming = true;
            return this;
        }

        public String toString()
        {
            return this.testurl;
        }
    }

    //////////////////////////////////////////////////
    // Static variables and methods

    protected DSP
    dspFor(String surl)
    {
        URL url;
        try {
            url = new URL(surl);
        } catch (MalformedURLException mue) {
            throw new IllegalArgumentException("Malformed url: " + surl);
        }
        String proto = url.getProtocol();
        String path = url.getPath();
        int dot = path.lastIndexOf('.');
        if(dot < 0) dot = path.length();
        String ext = path.substring(dot, path.length());
        try {
            if("file".equals(proto)) {
                // discriminate on the extensions
                if(".raw".equals(ext))
                    return new FileDSP();
                if(".syn".equals(ext))
                    return new SynDSP();
                if(".nc".equals(ext))
                    return new Nc4DSP();
            } else if("http".equals(proto)
                    || "https".equals(url.getProtocol())) {
                return new HttpDSP();
            }
            throw new IllegalArgumentException("Cannot determine DSP class for: " + surl);
        } catch (DapException de) {
            throw new IllegalArgumentException("Cannot create DSP  for: " + surl);
        }
    }
    //////////////////////////////////////////////////
    // Instance variables

    // Test cases

    List<TestCase> alltestcases = new ArrayList<TestCase>();
    List<TestCase> chosentests = new ArrayList<TestCase>();

    String resourceroot = null;

    //////////////////////////////////////////////////

    @Before
    public void setup() throws Exception
    {
        DapCache.flush();
        this.resourceroot = getResourceRoot();
        this.resourceroot = DapUtil.absolutize(this.resourceroot); // handle problem of windows paths
        TestCase.setRoot(resourceroot);
        StringBuilder buf = new StringBuilder();
        buf.setLength(0);

        defineAllTestcases();
        chooseTestcases();
    }

    // convert an extension to a file or url prefix
    String
    prefix(String scheme, String ext)
    {
        if(ext.charAt(0) == '.') ext = ext.substring(1);
        if(scheme.startsWith("http")) {
            return "http://"
                    + TestDir.dap4TestServer
                    + "/d4ts";
        } else if(scheme.equals("file")) {
            if(ext.equals("raw"))
                return "file:/"
                        + this.resourceroot
                        + "/"
                        + TESTCLIENTINPUT;
            if(ext.equals("syn"))
                return "file:/"
                        + this.resourceroot
                        + "/"
                        + TESTDSPINPUT;
            if(ext.equals("nc"))
                return "file:/"
                        + this.resourceroot
                        + "/"
                        + TESTFILESINPUT;
        }
        throw new IllegalArgumentException();
    }

    //////////////////////////////////////////////////
    // Define test cases

    void
    chooseTestcases()
    {
        if(false) {
            chosentests = locate("file:", "test_atomic_array.nc");
            prop_visual = true;
        } else {
            for(TestCase tc : alltestcases) {
                chosentests.add(tc);
            }
        }
    }

    void
    defineAllTestcases()
    {
        if(false) {
            add("file", "test_atomic_types.nc.raw");
            add("file", "test_atomic_array.nc.raw");
            add("file", "test_groups1.nc.raw");
            add("file", "test_one_var.nc.raw");
            add("file", "test_one_vararray.nc.raw");
            add("file", "test_opaque.nc.raw");
            add("file", "test_opaque_array.nc.raw");
            add("file", "test_enum.nc.raw");
            add("file", "test_enum_2.nc.raw");
            add("file", "test_enum_array.nc.raw");
            add("file", "test_utf8.nc.raw");
            add("file", "test_fill.nc.raw");
            add("file", "test_struct_type.nc.raw");
            add("file", "test_anon_dim.syn");
            add("file", "test_atomic_array.syn");
            add("file", "test_atomic_types.syn");
            add("file", "test_struct_array.syn");
            add("file", "test_struct_nested.hdf5.raw");
            add("file", "test_struct_nested3.hdf5.raw");
            add("file", "test_atomic_types.nc");
            add("file", "test_atomic_array.nc");
            add("file", "test_groups1.nc");
            add("file", "test_one_var.nc");
            add("file", "test_one_vararray.nc");
            add("file", "test_opaque.nc");
            add("file", "test_opaque_array.nc");
            add("file", "test_enum.nc");
            add("file", "test_enum_2.nc");
            add("file", "test_enum_array.nc");
            add("file", "test_utf8.nc");
            add("file", "test_fill.nc");
        }
        if(false) {
            add("http", "test_atomic_array.nc");
            add("http", "test_atomic_types.nc");
            add("http", "test_enum.nc");
            add("http", "test_enum_2.nc");
            add("http", "test_enum_array.nc");
            add("http", "test_groups1.nc");
            add("http", "test_misc1.nc");
            add("http", "test_one_var.nc");
            add("http", "test_one_vararray.nc");
            add("http", "test_opaque.nc");
            add("http", "test_opaque_array.nc");
            add("http", "test_test.nc");
            add("http", "test_utf8.nc");
            add("http", "test_anon_dim.syn");
            add("http", "test_struct_array.nc");
            add("http", "test_struct_nested.nc");
            add("http", "test_struct_nested3.nc");
            add("http", "test_struct_type.nc");
            add("http", "test_atomic_array.syn");
            add("http", "test_atomic_types.syn");
            add("http", "test_sequence_1.syn");
            add("http", "test_sequence_2.syn");
            add("http", "test_struct_array.syn");
            add("http", "test_struct_nested.hdf5");
            add("http", "test_struct_nested3.hdf5");
            add("http", "test_test.hdf5");
        }
    }

    protected void
    add(String scheme, String path)
    {
        String ext = path.substring(path.lastIndexOf('.'), path.length());
        String prefix = prefix(scheme, ext);
        TestCase tc = new TestCase(prefix, path);
        for(TestCase t : this.alltestcases) {
            assert !t.testurl.equals(tc.testurl) : "Duplicate TestCases: " + t;
        }
        this.alltestcases.add(tc);
        if(scheme.equals("file")) {
            try {
                URL u = new URL(tc.testurl);
                File f = new File(u.getPath());
                if(!f.canRead()) {
                    System.err.println("Unreadable file test case: " + tc.testurl);
                }
            } catch (MalformedURLException e) {
                System.err.println("Malformed file test case: " + tc.testurl);
            }
        }
    }

    //////////////////////////////////////////////////
    // Junit test method
    @Category(NeedsExternalResource.class)
    @Test
    public void testDSP()
            throws Exception
    {
        for(TestCase testcase : chosentests) {
            doOneTest(testcase);
        }
        System.err.println("*** PASS");
    }

    //////////////////////////////////////////////////
    // Primary test method
    void
    doOneTest(TestCase testcase)
            throws Exception
    {
        System.out.println("Testcase: " + testcase.testurl);
        System.out.println("Baseline: " + testcase.baselinepath);

        DSP dsp = dspFor(testcase.testurl);

        dsp.setContext(new DapContext());
        dsp.open(testcase.testurl);

        String metadata = dumpmetadata(dsp);
        String data = dumpdata(dsp);
        String testoutput = metadata + data;

        if(prop_visual)
            visual(testcase.title + ".dap", testoutput);

        String baselinefile = testcase.baselinepath;

        if(prop_baseline)
            writefile(baselinefile, testoutput);
        else if(prop_diff) { //compare with baseline
            // Read the baseline file(s)
            String baselinecontent = readfile(baselinefile);
            System.out.println("Comparison: vs " + baselinefile);
            Assert.assertTrue("*** FAIL", same(getTitle(), baselinecontent, testoutput));
        }
    }

    String dumpmetadata(DSP dsp)
            throws Exception
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        // Print the meta-databuffer using these args to NcdumpW
        DMRPrinter p = new DMRPrinter(dsp.getDMR(), pw);
        p.print();
        pw.close();
        sw.close();
        return sw.toString();
    }

    String dumpdata(DSP dsp)
            throws Exception
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        // Print the meta-databuffer using these args to NcdumpW
        DSPPrinter p = new DSPPrinter(dsp, pw).flag(DSPPrinter.Flags.CONTROLCHAR);
        p.print();
        pw.close();
        sw.close();
        return sw.toString();
    }

    //////////////////////////////////////////////////
    // Utility methods

    //Locate the test cases with given prefix
    List<TestCase>
    locate(String scheme, String s)
    {
        return locate(scheme, s, null);
    }

    List<TestCase>
    locate(String scheme, String s, List<TestCase> list)
    {
        if(list == null) list = new ArrayList<>();
        int matches = 0;
        for(TestCase ct : this.alltestcases) {
            if(!ct.testurl.startsWith(scheme)) continue;
            if(ct.testurl.endsWith(s)) {
                matches++;
                list.add(ct);
            }
        }
        assert matches > 0 : "No such testcase: " + s;
        return list;
    }

    static boolean
    report(String msg)
    {
        System.err.println(msg);
        return false;
    }


}
