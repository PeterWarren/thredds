/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.core.data;

import java.io.IOException;
import java.util.List;

/**
DataStructure represents a single instance of a structure.
*/

public interface DataStructure extends DataCompound
{
    public void addField(int fieldno, DataVariable dvfield) throws DataException;

    // Get ith field  as variable
    public DataVariable getfield(int i) throws DataException;

    // Get named field of the offsetth instance
    public DataVariable getfield(String name) throws DataException;

    // This is a hack to support the problem that CDM has not scalar objects:
    // only ArrayStrccuture. So provide a mechanism to convert a scalar to a
    // compound array of rank 0.
    public DataCompoundArray asCompoundArray() throws DataException;

}
