/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.serial;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.HashMap;
import java.util.Map;

import static dap4.dap4lib.netcdf.DapNetcdf.*;

public class D4DMR
{
    //////////////////////////////////////////////////

    static public class D4Attribute extends DapAttribute
    {
        public D4Attribute(String name, DapType basetype)
        {
            super(name, basetype);
        }
    }

    static public class D4AttributeSet extends DapAttributeSet
    {
        public D4AttributeSet(String name)
        {
            super(name);
        }
    }

    static public class D4Dimension extends DapDimension
    {
        public D4Dimension(String name, long size)
        {
            super(name, size);
        }
    }

    static public class D4Map extends DapMap
    {
        public D4Map(DapVariable target)
        {
            super(target);
        }
    }

    static public class D4AtomicVariable extends DapAtomicVariable
    {
        public D4AtomicVariable(String name, DapType t)
        {
            super(name, t);
        }
    }

    abstract static public class D4Variable extends DapVariable
    {
        public D4Variable(String name)
        {
            super(name);
        }
    }

    static public class D4Group extends DapGroup
    {
        public D4Group(String name)
        {
            super(name);
        }
    }

    static public class D4Dataset extends DapDataset
    {
        public D4Dataset(String name)
        {
            super(name);
        }
    }

    static public class D4Enumeration extends DapEnumeration
    {
        public D4Enumeration(String name, DapType basetype)
        {
            super(name, basetype);
        }
    }

    static public class D4EnumConst extends DapEnumConst
    {
        public D4EnumConst(String name, long value)
        {
            super(name, value);
        }
    }

    static public class D4Structure extends DapStructure
    {
        public D4Structure(String name)
        {
            super(name);
        }
    }

    static public class D4Sequence extends DapSequence
    {
        public D4Sequence(String name)
        {
            super(name);
        }
    }

    static public class D4OtherXML extends DapOtherXML
    {
        public D4OtherXML(String name)
        {
            super(name);
        }
    }
}
