package dap4.test;

import dap4.core.data.DSP;
import dap4.core.util.DapUtil;
import dap4.servlet.DapCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.unidata.util.test.TestDir;
import ucar.unidata.util.test.category.NeedsExternalResource;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Test at the NetcdfDataset level
 */
public class TestCDMClient extends DapTestCommon
{
    static final boolean DEBUG = false;

    static final String EXTENSION = "ncdump";

    static final String TESTEXTENSION = "raw";

    //////////////////////////////////////////////////
    // Constants

    static final String DATADIR = "src/test/data"; // relative to dap4 root
    static final String TESTDATADIR = DATADIR + "/resources/TestCDMClient";
    static final String BASELINEDIR = TESTDATADIR + "/baseline";
    static final String TESTINPUTDIR = TESTDATADIR + "/testinput";

    //Define the names of the xfail tests
    static final String[] XFAIL_TESTS = {
            "test_struct_array.nc",
    };

    static boolean isXfailTest(String t)
    {
        for(String s : XFAIL_TESTS) {
            if(s.equals(t)) return true;
        }
        return false;
    }

    //Define the names of tests that are dmr only
    static final String[] DISABLED = {
            "test_sequence_2.syn",
    };

    static boolean isDisabledTest(String t)
    {
        for(String s : DISABLED) {
            if(s.equals(t)) return true;
        }
        return false;
    }

    //////////////////////////////////////////////////
    // Type Declarations

    static class TestCase
    {
        static String root = null;
        static String server = null;

        String title;
        String dataset;
        boolean checksumming;
        boolean xfail;
        String testinputpath;
        String baselinepath;

        TestCase(String dataset, boolean checksumming, boolean xfail)
        {
            this.title = dataset;
            this.dataset = dataset;
            this.checksumming = checksumming;
            this.xfail = xfail;
            this.testinputpath
                    = root + "/" + TESTINPUTDIR + "/" + dataset;
            this.baselinepath
                    = root + "/" + BASELINEDIR + "/" + dataset;
        }

        String makeurl()
        {
            String url = server;
            if(server.startsWith(FILESERVER)) {
                url = url + "/" + dataset + ".raw";
            } else {
                url = url + "/" + dataset;
            }
            return url;
        }

        public String toString()
        {
            return dataset;
        }
    }

    static class TestFilter implements FileFilter
    {
        boolean debug;

        public TestFilter(boolean debug)
        {
            this.debug = debug;
        }

        public boolean accept(File file)
        {
            boolean ok = false;
            if(file.isFile() && file.canRead()) {
                // Check for proper extension
                String name = file.getName();
                if(name != null && name.endsWith(TESTEXTENSION))
                    ok = true;
            }
            if(!ok && debug) {
                report("Ignoring: " + file.toString());
            }
            return ok;
        }
    }

    //////////////////////////////////////////////////
    // Instance variables

    // Test cases

    List<TestCase> alltestcases = new ArrayList<TestCase>();
    List<TestCase> chosentests = new ArrayList<TestCase>();

    String resourceroot = null;
    String datasetpath = null;

    String sourceurl = null;

    //////////////////////////////////////////////////

    @Before
    public void setup() throws Exception
    {
        DapCache.flush();
        this.resourceroot = getResourceRoot();
        this.resourceroot = DapUtil.absolutize(this.resourceroot); // handle problem of windows paths
        this.datasetpath = this.resourceroot + "/" + TESTINPUTDIR;
        //findServer(this.datasetpath);
        //this.sourceurl = this.d4tsserver;
        this.sourceurl = TestDir.dap4TestServer;
        System.out.println("Using source url " + this.sourceurl);
        defineAllTestcases(this.resourceroot, this.sourceurl);
        chooseTestcases();
    }

    //////////////////////////////////////////////////
    // Define test cases

    void
    chooseTestcases()
    {
        if(false) {
            chosentests = locate("test_opaque.nc");
            prop_visual = true;
        } else {
            for(TestCase tc : alltestcases) {
                chosentests.add(tc);
            }
        }
    }

    boolean
    defineAllTestcases(String root, String server)
    {
        TestCase.root = root;
        TestCase.server = server;
        File testpath = new File(root + "/" + TESTINPUTDIR);
        File basepath = new File(root + "/" + BASELINEDIR);
        if(!basepath.exists() || !basepath.isDirectory() || !basepath.canRead())
            return report("Base directory not readable: " + basepath.toString());
        if(!testpath.exists() || !testpath.isDirectory() || !testpath.canRead())
            return report("Test directory not readable: " + testpath.toString());
        // Generate the testcases from the located .dmr files
        // This assumes that the set of files and the set of
        // files thru the sourceurl are the same
        File[] filelist = testpath.listFiles(new TestFilter(DEBUG));
        for(int i = 0; i < filelist.length; i++) {
            File file = filelist[i];
            // remove the extension
            String name = file.getName();
            if(name == null) continue;
            int index = name.lastIndexOf("." + TESTEXTENSION);
            assert (index > 0);
            name = name.substring(0, index);
            if(!isDisabledTest(name)) {
                TestCase ct = new TestCase(name, true, isXfailTest(name));
                this.alltestcases.add(ct);
            }
        }

        return true;
    }

    //////////////////////////////////////////////////
    // Junit test method
    @Category(NeedsExternalResource.class)
    @Test
    public void testCDMClient()
            throws Exception
    {
        boolean pass = true;
        for(TestCase testcase : chosentests) {
            if(!doOneTest(testcase)) pass = false;
        }
        Assert.assertTrue("*** Fail: TestCDMClient", pass);
    }

    //////////////////////////////////////////////////
    // Primary test method
    boolean
    doOneTest(TestCase testcase)
            throws Exception
    {
        boolean pass = true;
        System.out.println("Testcase: " + testcase.testinputpath);
        System.out.println("Baseline: " + testcase.baselinepath+EXTENSION);

        String url = testcase.makeurl();
        NetcdfDataset ncfile = null;
        try {
            ncfile = openDataset(url);
        } catch (Exception e) {
            System.err.println(testcase.xfail ? "XFail" : "Fail");
            e.printStackTrace();
            return testcase.xfail;
        }

        String metadata = null;
        String data = null;

        metadata = ncdumpmetadata(ncfile);
        if(prop_visual) {
            visual(testcase.title + ".dmr", metadata);
        }
        data = ncdumpdata(ncfile);
        if(prop_visual)
            visual(testcase.title + ".dap", data);


        String testoutput = data;
        String baselinefile = testcase.baselinepath + "." + EXTENSION;

        if(prop_baseline)
            writefile(baselinefile, testoutput);

        if(prop_diff) { //compare with baseline
            // Read the baseline file(s)
            String baselinecontent = readfile(baselinefile);
            System.out.println("Comparison: vs " + baselinefile);
            pass = pass && same(getTitle(),baselinecontent, testoutput);
            System.out.println(pass ? "Pass" : "Fail");
        }
        return pass;
    }


    String ncdumpmetadata(NetcdfDataset ncfile)
            throws Exception
    {
        StringWriter sw = new StringWriter();
        // Print the meta-databuffer using these args to NcdumpW
        try {
            if(!ucar.nc2.NCdumpW.print(ncfile, "-unsigned", sw, null))
                throw new Exception("NcdumpW failed");
        } catch (IOException ioe) {
            throw new Exception("NcdumpW failed", ioe);
        }
        sw.close();
        return sw.toString();
    }

    String ncdumpdata(NetcdfDataset ncfile)
            throws Exception
    {
        StringWriter sw = new StringWriter();
        // Dump the databuffer
        sw = new StringWriter();
        try {
            if(!ucar.nc2.NCdumpW.print(ncfile, "-vall -unsigned", sw, null))
                throw new Exception("NCdumpW failed");
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new Exception("NCdumpW failed", ioe);
        }
        sw.close();
        return sw.toString();
    }

    //////////////////////////////////////////////////
    // Utility methods

    //Locate the test cases with given prefix
    List<TestCase>
    locate(String prefix)
    {
        List<TestCase> results = new ArrayList<TestCase>();
        for(TestCase ct : this.alltestcases) {
            if(!ct.dataset.startsWith(prefix))
                continue;
            results.add(ct);
        }
        return results;
    }

    static boolean
    report(String msg)
    {
        System.err.println(msg);
        return false;
    }


} // class TestCDMClient                                                             b

