package dap4.test;

import dap4.core.data.DSPRegistry;
import dap4.core.util.DapDump;
import dap4.core.util.Escape;
import dap4.dap4lib.AbstractDSP;
import dap4.dap4lib.ChunkInputStream;
import dap4.dap4lib.FileDSP;
import dap4.dap4lib.RequestMode;
import dap4.servlet.DapCache;
import dap4.servlet.DapController;
import dap4.servlet.Generator;
import dap4.servlet.SynDSP;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import thredds.server.dap4.Dap4Controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TestServlet test server side
 * constraint processing.
 */


public class TestServletConstraints extends DapTestCommon
{
    static final boolean DEBUG = false;

    //////////////////////////////////////////////////
    // Constants

    static protected final String RESOURCEPATH = "/src/test/data/resources"; // wrt getTestInputFilesDir
    static protected final String TESTINPUTDIR = "/testfiles";
    static protected String BASELINEDIR = "/TestServletConstraints/baseline";
    static protected String GENERATEDIR = "/TestCDMClient/testinput";

    // constants for Fake Request
    static String FAKEURLPREFIX = "/dap4";

    static final BigInteger MASK = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    //////////////////////////////////////////////////
    // Type Declarations

    static class TestCase
    {
        static String inputroot = null;
        static String baselineroot = null;
        static String generateroot = null;

        static public void
        setRoots(String input, String baseline, String generate)
        {
            inputroot = input;
            baselineroot = baseline;
            generateroot = generate;
        }

        static TestCase[] alltests;

        static {
            alltests = new TestCase[2048];
            reset();
        }

        static public void reset()
        {
            Arrays.fill(alltests, null);
        }

        String title;
        String dataset;
        String constraint;
        boolean xfail;
        String[] extensions;
        Dump.Commands template;
        String testinputpath;
        String baselinepath;
        String generatepath;
        int id;

        TestCase(int id, String dataset, String extensions, String ce)
        {
            this(id, dataset, extensions, ce, null, true);
        }

        TestCase(int id, String dataset, String extensions, String ce,
                       Dump.Commands template)
        {
            this(id, dataset, extensions, ce, template, false);
        }

        TestCase(int id, String dataset, String extensions, String ce,
                       Dump.Commands template, boolean xfail)
        {
            if(alltests[id] != null)
                throw new IllegalStateException("two tests with same id");
            this.id = id;
            this.title = dataset + (ce == null ? "" : ("?" + ce));
            this.dataset = dataset;
            this.constraint = ce;
            this.xfail = xfail;
            this.extensions = extensions.split(",");
            this.template = template;
            this.testinputpath = canonjoin(this.inputroot, dataset) + "." + id;
            this.baselinepath = canonjoin(this.baselineroot, dataset) + "." + id;
            this.generatepath = canonjoin(this.generateroot, dataset) + "." + id;
            alltests[id] = this;
        }

        String makeurl(RequestMode ext)
        {
            String url = canonjoin(FAKEURLPREFIX, canonjoin(TESTINPUTDIR, dataset));
            if(ext != null) url += "." + ext.toString();
            return url;
        }

        String makequery(RequestMode ext)
        {
            String query = "";
            if(this.constraint != null) {
                String ce = this.constraint;
                // Escape it
                ce = Escape.urlEncodeQuery(ce);
                query = ce;
            }
            return query;
        }

        public String toString()
        {
            return makeurl(null);
        }
    }

    //////////////////////////////////////////////////
    // Instance variables

    protected MockMvc mockMvc;

    // Test cases

    List<TestCase> alltestcases = new ArrayList<TestCase>();

    List<TestCase> chosentests = new ArrayList<TestCase>();


    //////////////////////////////////////////////////

    @Before
    public void setup()
    {
        StandaloneMockMvcBuilder mvcbuilder =
                MockMvcBuilders.standaloneSetup(new Dap4Controller());
        mvcbuilder.setValidator(new TestServlet.NullValidator());
        this.mockMvc = mvcbuilder.build();
        testSetup(RESOURCEPATH);
        DapCache.dspregistry.register(FileDSP.class, DSPRegistry.FIRST);
        DapCache.dspregistry.register(SynDSP.class, DSPRegistry.FIRST);
        if(prop_ascii)
            Generator.setASCII(true);
        TestCase.setRoots(canonjoin(getResourceRoot(), TESTINPUTDIR),
                canonjoin(getResourceRoot(), BASELINEDIR),
                canonjoin(getResourceRoot(), GENERATEDIR));
        defineAllTestcases();
        chooseTestcases();
    }

    //////////////////////////////////////////////////
    // Define test cases

    void
    chooseTestcases()
    {
        if(false) {
            chosentests = locate(1);
            prop_visual = true;
            prop_debug = true;
	    prop_generate = false;
        } else {
            for(TestCase tc : alltestcases) {
                chosentests.add(tc);
            }
        }
    }

    void defineAllTestcases()
    {
        TestCase.reset();
        this.alltestcases.add(
                new TestCase(1, "test_one_vararray.nc", "dmr,dap", "/t[1]",
                        // S4
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                printer.printvalue('S', 4);
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(2, "test_anon_dim.syn", "dmr,dap", "/vu32[0:3]",  // test for dimension inclusion
                        // S4
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                printer.printvalue('U', 4);
                                printer.printvalue('U', 4);
                                printer.printvalue('U', 4);
                                printer.printvalue('U', 4);
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(3, "test_one_vararray.nc", "dmr,dap", "/t",  // test for dimension inclusion
                        // S4
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                printer.printvalue('S', 4);
                                printer.printvalue('S', 4);
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(4, "test_enum_array.nc", "dmr,dap", "/primary_cloud[1:2:4]",
                        // 2 S1
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                for(int i = 0; i < 2; i++) {
                                    printer.printvalue('U', 1, i);
                                }
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(5, "test_atomic_array.nc", "dmr,dap", "/vu8[1][0:2:2];/vd[1];/vs[1][0];/vo[0][1]",
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                for(int i = 0; i < 2; i++) {
                                    printer.printvalue('U', 1, i);
                                }
                                printer.printchecksum();
                                for(int i = 0; i < 1; i++) {
                                    printer.printvalue('F', 8, i);
                                }
                                printer.printchecksum();
                                for(int i = 0; i < 1; i++) {
                                    printer.printvalue('T', 0, i);
                                }
                                printer.printchecksum();
                                for(int i = 0; i < 1; i++) {
                                    printer.printvalue('O', 0, i);
                                }
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(6, "test_struct_array.nc", "dmr,dap", "/s[0:2:3][0:1]",
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                for(int i = 0; i < 4; i++) {
                                    for(int j = 0; j < 2; j++) {
                                        printer.printvalue('S', 4);
                                    }
                                }
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(7, "test_opaque_array.nc", "dmr,dap", "/vo2[1][0:1]",
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                for(int i = 0; i < 2; i++) {
                                    printer.printvalue('O', 0, i);
                                }
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(8, "test_atomic_array.nc", "dmr,dap", "/v16[0:1,3]",
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                for(int i = 0; i < 3; i++) {
                                    printer.printvalue('S', 2, i);
                                }
                                printer.printchecksum();
                            }
                        }));
        this.alltestcases.add(
                new TestCase(9, "test_atomic_array.nc", "dmr,dap", "/v16[3,0:1]",
                        new Dump.Commands()
                        {
                            public void run(Dump printer) throws IOException
                            {
                                for(int i = 0; i < 3; i++) {
                                    printer.printvalue('S', 2, i);
                                }
                                printer.printchecksum();
                            }
                        }));
    }

    //////////////////////////////////////////////////
    // Junit test methods

    @Test
    public void testServletConstraints()
            throws Exception
    {
        DapCache.flush();
        for(TestCase testcase : chosentests) {
            doOneTest(testcase);
        }
    }

    //////////////////////////////////////////////////
    // Primary test method

    void
    doOneTest(TestCase testcase)
            throws Exception
    {
        System.out.println("Testcase: " + testcase.toString());
        System.out.println("Baseline: " + testcase.baselinepath);

        for(String extension : testcase.extensions) {
            RequestMode ext = RequestMode.modeFor(extension);
            switch (ext) {
            case DMR:
                dodmr(testcase);
                break;
            case DAP:
                dodata(testcase);
                break;
            default:
                Assert.assertTrue("Unknown extension", false);
            }
        }
    }

    void
    dodmr(TestCase testcase)
            throws Exception
    {
        String url = testcase.makeurl(RequestMode.DMR);
        String query = testcase.makequery(RequestMode.DMR);

        RequestBuilder rb = MockMvcRequestBuilders
                .get(url)
                .servletPath(url)
                .param(CONSTRAINTTAG, query);
        MvcResult result = this.mockMvc.perform(rb).andReturn();

        // Collect the output
        MockHttpServletResponse res = result.getResponse();
        byte[] byteresult = res.getContentAsByteArray();

        // Test by converting the raw output to a string
        String sdmr = new String(byteresult, UTF8);

        if(prop_visual)
            visual(url, sdmr);
        if(!testcase.xfail && prop_baseline) {
            writefile(testcase.baselinepath + ".dmr", sdmr);
        } else if(prop_diff) { //compare with baseline
            // Read the baseline file
            String baselinecontent = readfile(testcase.baselinepath + ".dmr");
            System.out.println("DMR Comparison: vs " + testcase.baselinepath + ".dmr");
            Assert.assertTrue("***Fail", same(getTitle(), baselinecontent, sdmr));
        }
    }

    void
    dodata(TestCase testcase)
            throws Exception
    {
        String baseline;
        RequestMode mode = RequestMode.DAP;
        String url = testcase.makeurl(mode);
        String query = testcase.makequery(RequestMode.DMR);

        RequestBuilder rb = MockMvcRequestBuilders
                .get(url)
                .servletPath(url)
                .param(CONSTRAINTTAG, query);
        MvcResult result = this.mockMvc.perform(rb).andReturn();

        // Collect the output
        MockHttpServletResponse res = result.getResponse();
        byte[] byteresult = res.getContentAsByteArray();

        if(prop_debug || DEBUG) {
            DapDump.dumpbytes(ByteBuffer.wrap(byteresult).order(ByteOrder.nativeOrder()), true);
        }

        // Setup a ChunkInputStream
        ByteArrayInputStream bytestream = new ByteArrayInputStream(byteresult);

        ChunkInputStream reader = new ChunkInputStream(bytestream, RequestMode.DAP, ByteOrder.nativeOrder());

        String sdmr = reader.readDMR(); // Read the DMR
        if(prop_visual)
            visual(url, sdmr);

        Dump printer = new Dump();
        String sdata = printer.dumpdata(reader, true, reader.getByteOrder(), testcase.template);

        if(prop_visual)
            visual(testcase.title + ".dap", sdata);

        if(!testcase.xfail && prop_baseline)
            writefile(testcase.baselinepath + ".dap", sdata);

        if(prop_diff) {
            //compare with baseline
            // Read the baseline file
            System.out.println("Note Comparison:");
            String baselinecontent = readfile(testcase.baselinepath + ".dap");
            Assert.assertTrue("***Fail", same(getTitle(), baselinecontent, sdata));
        }
    }

    //////////////////////////////////////////////////
    // Utility methods


    // Locate the test cases with given prefix
    List<TestCase>
    locate(Object tag)
    {
        List<TestCase> results = new ArrayList<TestCase>();
        for(TestCase ct : this.alltestcases) {
            if(tag instanceof Integer && ct.id == (Integer) tag) {
                results.add(ct);
                break;
            } else if(tag instanceof String && ct.title.equals((String) tag)) {
                results.add(ct);
                break;
            }
        }
        return results;
    }
}
