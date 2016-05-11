/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.cdm;

import dap4.core.data.DataException;
import dap4.core.data.DataStructure;
import dap4.core.data.DataVariable;
import dap4.core.dmr.DapAtomicVariable;
import dap4.core.dmr.DapStructure;
import dap4.core.dmr.DapVariable;
import dap4.dap4lib.AbstractDataVariable;
import ucar.ma2.Array;
import ucar.ma2.ArrayStructure;
import ucar.ma2.StructureData;
import ucar.ma2.StructureMembers;

import java.util.Arrays;
import java.util.List;

/**
 * Define DSP support
 * for a single structure instance.
 */

public class CDMDataStructure extends AbstractDataVariable implements DataStructure
{
    //////////////////////////////////////////////////
    // Instance Variables

    protected CDMDSP dsp = null;
    protected CDMDataCompoundArray parent;
    //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
    protected long recno;
    protected byte[] checksum = null;
    protected DapStructure dapstruct = null;
    StructureData cdmdata = null;
    DataVariable[] fieldcache = null;
    List<StructureMembers.Member> members = null;

    //////////////////////////////////////////////////
    // Constructors

    public CDMDataStructure(CDMDSP dsp, DapStructure dap, CDMDataCompoundArray cdv, long recno, StructureData data)
            throws DataException
    {
        super(dap);
        this.dsp = dsp;
        this.parent = cdv;
        //Coverity[FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD]
        this.recno = recno;
        this.dapstruct = dap;
        // Locate our Structuredata from our parent
        this.cdmdata = data;
        this.members = cdmdata.getMembers();
        this.fieldcache = new DataVariable[members.size()];
        Arrays.fill(this.fieldcache, null);
    }

    //////////////////////////////////////////////////
    // DataStructure Interface

    // Read named field
    @Override
    public DataVariable readfield(String name) throws DataException
    {
        StructureMembers.Member member = cdmdata.findMember(name);
        return readfield(member);
    }

    public void
    addField(int fieldno, DataVariable dvfield)
    {

    }

    // Read ith field
    @Override
    public DataVariable readfield(int i)
            throws DataException
    {
        if(i < 0 || i >= this.members.size())
            throw new DataException("readfield: index out of bounds: " + i);
        return readfield(this.members.get(i));
    }

    protected DataVariable readfield(StructureMembers.Member member)
            throws DataException
    {
        int index = this.members.indexOf(member);
        DapVariable field = this.dapstruct.getField(index);
        if(fieldcache[index] == null) {
            Array array = cdmdata.getArray(member);
            switch (array.getDataType()) {
            case SEQUENCE:
            case STRUCTURE:
                fieldcache[index] = new CDMDataCompoundArray(dsp, field, (ArrayStructure) array);
                break;
            default:
                fieldcache[index] = new CDMDataAtomic(dsp, (DapAtomicVariable) field, array);
                break;
            }
        }
        return fieldcache[index];
    }

}
