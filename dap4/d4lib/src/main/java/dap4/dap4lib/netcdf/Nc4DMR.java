/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.netcdf;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.HashMap;
import java.util.Map;

import static dap4.dap4lib.netcdf.DapNetcdf.*;

public class Nc4DMR
{
    //////////////////////////////////////////////////
    // Type Decls

    static class Notes
    {
        int gid;
        int id;
        DapNode node = null;

        Notes(int gid, int id)
        {
            this.gid = gid;
            this.id = id;
        }

        Notes set(DapNode node)
        {
            this.node = node;
            if(node.annotation() != null)
                assert (node.annotation() == this);
            else
                node.annotate(this);
            return this;
        }

        DapNode get()
        {
            return this.node;
        }
    }

    static class GroupNotes extends Notes
    {
        static Map<Integer, GroupNotes> allgroups = new HashMap<>();

        static GroupNotes find(int gid)
        {
            return allgroups.get(gid);
        }

        GroupNotes(int p, int g)
        {
            super(p, g);
            allgroups.put(g, this);
        }

        DapGroup get()
        {
            return (DapGroup) this.node;
        }

        GroupNotes set(DapNode node)
        {
            super.set(node);
            return this;
        }
    }

    static class DimNotes extends Notes
    {
        static Map<Integer, DimNotes> alldims = new HashMap<>();

        static DimNotes find(int gid)
        {
            return alldims.get(gid);
        }

        DimNotes(int g, int id)
        {
            super(g, id);
            alldims.put(id, this);
        }

        DapDimension get()
        {
            return (DapDimension) this.node;
        }

        DimNotes set(DapNode node)
        {
            super.set(node);
            return this;
        }
    }

    static class TypeNotes extends Notes
    {
        static Map<Integer, TypeNotes> alltypes;

        static TypeNotes find(int gid)
        {
            return alltypes.get(gid);
        }

        int opaquelen = 0;

        TypeNotes(int g, int id)
        {
            super(g, id);
            alltypes.put(id, this);
        }

        DapType get()
        {
            return (DapType) this.node;
        }

        TypeNotes set(DapNode node)
        {
            super.set(node);
            return this;
        }

        TypeNotes setOpaque(int len)
        {
            opaquelen = len;
            return this;
        }

        static {
            alltypes = new HashMap<>();
            new TypeNotes(0, NC_BYTE).set(DapType.INT8);
            new TypeNotes(0, NC_CHAR).set(DapType.CHAR);
            new TypeNotes(0, NC_SHORT).set(DapType.INT16);
            new TypeNotes(0, NC_INT).set(DapType.INT32);
            new TypeNotes(0, NC_FLOAT).set(DapType.FLOAT32);
            new TypeNotes(0, NC_DOUBLE).set(DapType.FLOAT64);
            new TypeNotes(0, NC_UBYTE).set(DapType.UINT8);
            new TypeNotes(0, NC_USHORT).set(DapType.UINT16);
            new TypeNotes(0, NC_UINT).set(DapType.UINT32);
            new TypeNotes(0, NC_INT64).set(DapType.INT64);
            new TypeNotes(0, NC_UINT64).set(DapType.UINT64);
            new TypeNotes(0, NC_STRING).set(DapType.STRING);
        }

    }

    static class VarNotes extends Notes
    {
        static Map<Long, VarNotes> allvars = new HashMap<>();

        static VarNotes find(int gid, int vid)
        {
            long gv = (((long) gid) << 32) | vid;
            return allvars.get(gv);
        }

        TypeNotes basetype = null;
        int fieldid = -1;

        VarNotes(int g, int v)
        {
            super(g, v);
            long gv = (((long) g) << 32) | v;
            allvars.put(gv, this);
        }

        VarNotes setType(TypeNotes ti)
        {
            this.basetype = ti;
            return this;
        }

        DapVariable get()
        {
            return (DapVariable) this.node;
        }

        VarNotes set(DapNode node)
        {
            super.set(node);
            return this;
        }


        VarNotes setField(int i)
        {
            this.fieldid = i;
            return this;
        }
    }


    //////////////////////////////////////////////////

    static public class Nc4Attribute extends DapAttribute
    {
        public Nc4Attribute(String name, DapType basetype)
        {
            super(name, basetype);
        }
    }

    static public class Nc4AttributeSet extends DapAttributeSet
    {
        public Nc4AttributeSet(String name)
        {
            super(name);
        }
    }

    static public class Nc4Dimension extends DapDimension
    {
        public Nc4Dimension(String name, long size)
        {
            super(name, size);
        }
    }

    static public class Nc4Map extends DapMap
    {
        public Nc4Map(DapVariable target)
        {
            super(target);
        }
    }

    static public class Nc4AtomicVariable extends DapAtomicVariable
    {
        public Nc4AtomicVariable(String name, DapType t)
        {
            super(name, t);
        }
    }

    abstract static public class Nc4Variable extends DapVariable
    {
        public Nc4Variable(String name)
        {
            super(name);
        }
    }

    static public class Nc4Group extends DapGroup
    {
        public Nc4Group(String name)
        {
            super(name);
        }
    }

    static public class Nc4Dataset extends DapDataset
    {
        public Nc4Dataset(String name)
                throws DapException
        {
            super(name);
        }
    }

    static public class Nc4Enumeration extends DapEnumeration
    {
        public Nc4Enumeration(String name, DapType basetype)
        {
            super(name, basetype);
        }
    }

    static public class Nc4EnumConst extends DapEnumConst
    {
        public Nc4EnumConst(String name, long value)
        {
            super(name, value);
        }
    }

    static public class Nc4Structure extends DapStructure
    {
        public Nc4Structure(String name)
        {
            super(name);
        }
    }

    static public class Nc4Sequence extends DapSequence
    {
        public Nc4Sequence(String name)
        {
            super(name);
        }
    }

    static public class Nc4OtherXML extends DapOtherXML
    {
        public Nc4OtherXML(String name)
        {
            super(name);
        }
    }
}
