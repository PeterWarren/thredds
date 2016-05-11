/*
 * Copyright (c) 1998 - 2011. University Corporation for Atmospheric Research/Unidata
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import ucar.unidata.util.test.UnitTestCommon;
import ucar.unidata.util.test.category.NeedsExternalResource;

public class TestByteRange extends UnitTestCommon
{
    // Collect testcases locally
    static public class Testcase
    {
        String title;
        String url;
        String cdl;

        public Testcase(String title, String url)
        {
            this.title = title;
            this.url = url;
        }
    }

    String testserver = null;
    List<Testcase> testcases = null;

    public TestByteRange()
    {
	super("ByteRange tests");
        definetestcases();
    }

    void
    definetestcases()
    {
        String threddsRoot = getThreddsroot();
        testcases = new ArrayList<Testcase>();
        testcases.add(new Testcase("TestByteRanges",
                "http://localhost:8081/thredds/fileServer/scanLocal/sss_binned_L3_MON_SCI_V4.0_2011.nc"
                //"http://data.nodc.noaa.gov/thredds/fileServer/aquarius/nodc_binned_V4.0/monthly/sss_binned_L3_MON_SCI_V4.0_2011.nc"
        ));
    }

    @Test
    @Category(NeedsExternalResource.class)
    public void
    testByteRange() throws Exception
    {
        System.out.println("TestByteRange:");
        for(Testcase testcase : testcases) {
            System.out.println("url: " + testcase.url);
            process1(testcase);
        }
    }

    void process1(Testcase testcase)
        throws Exception
    {
        NetcdfFile ncfile = NetcdfFile.open(testcase.url);
        if(ncfile == null)
            throw new Exception("Cannot read: " + testcase.url);
        StringWriter ow = new StringWriter();
        PrintWriter pw = new PrintWriter(ow);
        ncfile.writeCDL(pw, false);
        pw.close();
        ow.close();
        String captured = ow.toString();
	if(prop_visual)
            visual(testcase.title, captured);
//	if(prop_diff)
//            diff(testcase, captured);
    }

/*
    void diff(Testcase testcase, String captured)
        throws Exception
    {
        Reader baserdr = new StringReader(testcase.cdl);
        StringReader resultrdr = new StringReader(captured);
        // Diff the two files
        Diff diff = new Diff("Testing " + testcase.title);
        boolean pass = !diff.doDiff(baserdr, resultrdr);
        baserdr.close();
        resultrdr.close();
    }

    protected void
    baseline(Testcase testcase, String output)
    {
        try {
            // See if the cdl is in a file or a string.
            if(!testcase.cdl.startsWith("file://"))
                return;
            File f = new File(testcase.cdl.substring("file://".length(), testcase.cdl.length()));
            Writer w = new FileWriter(f);
            w.write(output);
            w.close();
        } catch (IOException ioe) {
            System.err.println("IOException");
        }

    }
*/

}
