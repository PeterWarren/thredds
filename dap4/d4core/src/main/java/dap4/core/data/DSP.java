/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import dap4.core.data.DataDataset;
import dap4.core.data.DataVariable;
import dap4.core.dmr.DapDataset;
import dap4.core.util.*;

import java.io.IOException;

public interface DSP
{
    /* Unfortunately, Java does not (yet, sigh!, as of java 7) allow
       including static methods (with no body) in an interface.
       As with IOSPs, we need a quick match function to indicate
       that this DSP is likely to be able to process this file.
     */
    /* All implementing classes must implement:
       1. a static dspMatch() function
       2. A parameterless constructor
     */

    // static public boolean dspMatch(String path, DapContext context);

    /**
     *
     * @param path  It is assumed that the path is appropriate to the dsp
     *              E.g. an absolute path or a url.
     * @return  DSP wrapping the path source
     * @throws DapException
     */
    public DSP open(String path) throws DapException;

    public String getLocation();
    public Object getContext();
    public void setContext(DapContext cxt);
    public DapDataset getDMR() throws DapException;
    public DataDataset getDataset() throws DataException;
    public void setDataset(DataDataset ds);
    public void close() throws IOException;
    public Object getAnnotation();

}
