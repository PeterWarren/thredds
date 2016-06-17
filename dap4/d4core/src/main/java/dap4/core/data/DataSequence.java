/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import java.util.List;

/**
DataSequence represents a set of records.
*/

public interface DataSequence extends DataCompound
{
    public long getRecordCount();

    public void addRecord(DataRecord r);

    // Read a single record
    public DataRecord getRecord(long recordno) throws DataException;
}
