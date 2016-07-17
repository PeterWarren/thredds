/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import dap4.core.ce.CEConstraint;
import dap4.core.dmr.DapDataset;
import dap4.core.util.DapContext;
import dap4.core.util.DapException;

import java.io.IOException;

public interface DSP
{
    /* All implementing classes must implement:
       1. a static dspMatch() function
       2. A parameterless constructor
     */

    /**
     * Determine if a path refers to an object processable by this DSP
     *
     * @param path
     * @param context
     * @return   true if this path can be processed by an instance of this DSP
     */
    public boolean dspMatch(String path, DapContext context);

    /**
     * @param path It is assumed that the path is appropriate to the dsp
     *             E.g. an absolute path or a url.
     * @return DSP wrapping the path source
     * @throws DapException
     */
    public DSP open(String path) throws DapException;

    public String getLocation();

    public void setLocation(String location);

    public Object getContext();

    public void setContext(DapContext cxt);

    public DapDataset getDMR() throws DapException;

    public DataDataset getDataset() throws DataException;

    public void setDataset(DataDataset ds);

    public void close() throws IOException;

    public Object getAnnotation();

    public CEConstraint getConstraint();

    public void setConstraint(CEConstraint ce);
}
