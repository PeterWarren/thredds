package dap4.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.unidata.util.test.category.NeedsExternalResource;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Test at the NetcdfDataset level
 */
public class TestConstraints extends DapTestCommon
{
    static final boolean DEBUG = false;
    static final boolean DMRPARSEDEBUG = false;
    static final boolean CEPARSEDEBUG = false;

    static final boolean NCDUMP = true; // Use NcDumpW instead of D4Print

    static final String EXTENSION = (NCDUMP ? "ncdump" : "dmp");

    //////////////////////////////////////////////////
    // Constants

    static final String TESTDATADIR = "TestCDMClient";
    static final String BASELINEDIR = TESTDATADIR + "/baseline";
    static final String TESTINPUTDIR = TESTDATADIR + "/testinput";

    static final String alpha = "abcdefghijklmnopqrstuvwxyz"
            + "abcdefghijklmnopqrstuvwxyz".toUpperCase();

    static class ClientTest
    {
        static String root = null;
        static String server = null;

        String title;
        String dataset;
        String testinputpath;
        String baselinepath;
        String constraint;
        int id;

        ClientTest(int id, String dataset, String constraint)
        {
            if(constraint != null && constraint.length() == 0)
                constraint = null;
            this.title = dataset + (constraint == null ? "" : "?" + constraint);
            this.dataset = dataset;
            this.id = id;
            this.testinputpath
                    = root + "/" + TESTINPUTDIR + "/" + dataset;
            this.baselinepath
                    = root + "/" + BASELINEDIR + "/" + dataset + "." + String.valueOf(this.id) + ".ncdump";
            this.constraint = constraint;
        }

        String makeurl()
        {
            String url = url = server + "/" + dataset;
            if(constraint != null) url += "?" + CONSTRAINTTAG + "=" + constraint;
            return url;
        }

        public String toString()
        {
            StringBuilder buf = new StringBuilder();
            buf.append(dataset);
            buf.append("{");
            if(constraint != null)
                buf.append("?" + CONSTRAINTTAG + "=" + constraint);
            return buf.toString();
        }
    }

    //////////////////////////////////////////////////
    // Instance variables

    // Test cases

    List<ClientTest> alltestcases = new ArrayList<ClientTest>();
    List<ClientTest> chosentests = new ArrayList<ClientTest>();

    String resourceroot = null;
    String datasetpath = null;

    String sourceurl = null;

    //////////////////////////////////////////////////

    @Before
    public void setup() throws Exception {
        this.resourceroot = getResourceRoot();
        // Check for windows path
        if(alpha.indexOf(this.resourceroot.charAt(0)) >= 0 && this.resourceroot.charAt(1) == ':') {
        } else if(this.resourceroot.charAt(0) != '/')
            this.resourceroot = "/" + this.resourceroot;
        this.datasetpath = this.resourceroot + "/" + TESTINPUTDIR;
        findServer(this.datasetpath);
        this.sourceurl = this.d4tsserver;
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
            chosentests.add(locate1(8));
            prop_visual = true;
        } else {
            for(ClientTest tc : alltestcases) {
                chosentests.add(tc);
            }
        }
    }

    void
    defineAllTestcases(String root, String server)
    {
        ClientTest.root = root;
        ClientTest.server = server;
        //alltestcases.add(new ClientTest("test_one_vararray.nc", null));
        alltestcases.add(new ClientTest(1, "test_one_vararray.nc", "t"));
        alltestcases.add(new ClientTest(2, "test_one_vararray.nc", "t[1]"));
        // alltestcases.add(new ClientTest(3,"test_enum_array.nc", null));
        alltestcases.add(new ClientTest(4, "test_enum_array.nc", "primary_cloud[1:2:4]"));
        //alltestcases.add(new ClientTest(5,"test_atomic_array.nc", null));
        alltestcases.add(new ClientTest(6, "test_atomic_array.nc", "vu8[1][0:2:2];vd[1];vs[1][0];vo[0][1]"));
        //alltestcases.add(new ClientTest(7,"test_struct_array.nc", null));
        alltestcases.add(new ClientTest(8, "test_struct_array.nc", "s[0:2:3][0:1]"));
    }

    //////////////////////////////////////////////////
    // Junit test method
    @Category(NeedsExternalResource.class)
    @Test
    public void testConstraints()
            throws Exception
    {
        for(ClientTest testcase : chosentests) {
            doOneTest(testcase);
        }
    }

    //////////////////////////////////////////////////
    // Primary test method
    void
    doOneTest(ClientTest testcase)
            throws Exception
    {
        int testcounter = 0;

        System.out.println("Testcase: " + testcase.testinputpath);
        System.out.println("Baseline: "+testcase.baselinepath);

        String url = testcase.makeurl();
        NetcdfDataset ncfile = null;
        try {
            ncfile = openDataset(url);
        } catch (Exception e) {
            throw e;
        }

        String metadata = (NCDUMP ? ncdumpmetadata(ncfile) : null);
        String data = (NCDUMP ? ncdumpdata(ncfile) : null);

        if(prop_visual) {
            visual("DMR: " + url, metadata);
            visual("DAP: " + url, data);
        }

        String testoutput = (NCDUMP ? data : metadata + data);

        if(prop_baseline)
            writefile(testcase.baselinepath, testoutput);

        if(prop_diff) { //compare with baseline
            // Read the baseline file(s)
            String baselinecontent = readfile(testcase.baselinepath);
            System.out.println("Comparison:");
            Assert.assertTrue("***Fail",same(getTitle(),baselinecontent, testoutput));
        }
    }

    //////////////////////////////////////////////////
    // Dump methods

    String ncdumpmetadata(NetcdfDataset ncfile)
    {
        boolean ok = false;
        String metadata = null;
        StringWriter sw = new StringWriter();

        // Print the meta-databuffer using these args to NcdumpW
        ok = false;
        try {
            ok = ucar.nc2.NCdumpW.print(ncfile, "-unsigned", sw, null);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            ok = false;
        }
        try {
            sw.close();
        } catch (IOException e) {
        }
        ;
        if(!ok) {
            System.err.println("NcdumpW failed");
        }
        return sw.toString();
    }

    String ncdumpdata(NetcdfDataset ncfile)
    {
        boolean ok = false;
        StringWriter sw = new StringWriter();

        // Dump the databuffer
        sw = new StringWriter();
        ok = false;
        try {
            ok = ucar.nc2.NCdumpW.print(ncfile, "-vall -unsigned", sw, null);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            ok = false;
        }
        try {
            sw.close();
        } catch (IOException e) {
        }
        ;
        if(!ok) {
            System.err.println("NcdumpW failed");
        }
        return sw.toString();
    }

    //////////////////////////////////////////////////
    // Utility methods

    // Locate the test cases with given index
    ClientTest
    locate1(int index)
    {
        List<ClientTest> results = new ArrayList<ClientTest>();
        for(ClientTest ct : this.alltestcases) {
            if(ct.id == index)
                return ct;
        }
        return null;
    }

    // Locate the test cases with given prefix
    ClientTest
    locate1(String prefix)
    {
        List<ClientTest> tests = locate(prefix);
        assert tests.size() > 0;
        return tests.get(0);
    }

    //Locate the test cases with given prefix and optional constraint
    List<ClientTest>
    locate(String prefix)
    {
        List<ClientTest> results = new ArrayList<ClientTest>();
        for(ClientTest ct : this.alltestcases) {
            if(!ct.title.equals(prefix))
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

} // class TestConstraints
