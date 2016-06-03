/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib;

import dap4.core.data.DataCompoundArray;
import dap4.core.data.DataException;
import dap4.core.data.DataRecord;
import dap4.core.data.DataVariable;
import dap4.core.dmr.DapSequence;

import java.util.Arrays;

/**
 * DataRecord represents a record from a sequence.
 * It is effectively equivalent to a Structure instance.
 */

public class D4DataRecord extends D4DataVariable implements DataRecord
{

    //////////////////////////////////////////////////
    // Instance variables

    //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
    protected D4DataSequence parent = null;
    //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
    protected int recno = 0;
    protected DataVariable[] fields;

    //////////////////////////////////////////////////
    // Constructors

    public D4DataRecord(D4DSP dsp, DapSequence dap, D4DataSequence parent, int recno)
            throws DataException
    {
        super(dsp, dap);
        this.dsp = dsp;
        this.parent = parent;
        this.recno = recno;
        this.fields = new D4DataVariable[dap.getFields().size()];
        Arrays.fill(this.fields, null);
    }

    //////////////////////////////////////////////////
    // Accessors

    @Override
    public void
    addField(int fieldno, DataVariable ddv)
            throws DataException
    {
        if(fieldno < 0 || fieldno >= fields.length)
            throw new DataException("Illegal field index: " + fieldno);
        fields[fieldno] = ddv;
    }

    //////////////////////////////////////////////////
    // DataStructure Interface

    // Read field by index
    @Override
    public DataVariable readfield(int i) throws DataException
    {
        if(i < 0 || i >= fields.length)
            throw new DataException("Illegal field index: " + i);
        return fields[i];
    }

    // Read field by name
    @Override
    public DataVariable readfield(String shortname) throws DataException
    {
        for(int i = 0; i < fields.length; i++) {
            if(fields[i].getTemplate().getShortName().equals(shortname))
                return fields[i];
        }
        return null;
    }

    public DataCompoundArray asCompoundArray()
            throws DataException
    {
        throw new UnsupportedOperationException();
    }

}
