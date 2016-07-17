/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.netcdf;

import dap4.core.dmr.*;

import java.util.HashMap;
import java.util.Map;

import static dap4.dap4lib.netcdf.DapNetcdf.*;

abstract public class Nc4Notes
{
    //////////////////////////////////////////////////
    // Type Decls

    static public class Notes
    {
        int gid;
        int id;
        DapNode node = null;

        public Notes(int gid, int id)
        {
            this.gid = gid;
            this.id = id;
        }

        public Notes set(DapNode node)
        {
            this.node = node;
            node.annotate(this);
            return this;
        }

        public DapNode get()
        {
            return this.node;
        }

        DapGroup group()
        {
            GroupNotes g = GroupNotes.find(gid);
            return (g == null ? null : g.get());
        }
    }

    static public class GroupNotes extends Notes
    {
        static Map<Integer, GroupNotes> allgroups = new HashMap<>();

        static public GroupNotes find(int gid)
        {
            return allgroups.get(gid);
        }

        public GroupNotes(int p, int g)
        {
            super(p, g);
            allgroups.put(g, this);
        }

        public DapGroup get()
        {
            return (DapGroup) this.node;
        }

        public GroupNotes set(DapNode node)
        {
            return (GroupNotes) super.set(node);
        }
    }

    static public class DimNotes extends Notes
    {
        static Map<Integer, DimNotes> alldims = new HashMap<>();

        static public DimNotes find(int id)
        {
            return alldims.get(id);
        }

        public DimNotes(int g, int id)
        {
            super(g, id);
            alldims.put(id, this);
        }

        public DapDimension get()
        {
            return (DapDimension) this.node;
        }

        public DimNotes set(DapNode node)
        {
            return (DimNotes) super.set(node);
        }

    }

    static public class TypeNotes extends Notes
    {
        static Map<Integer, TypeNotes> alltypes = new HashMap<>();

        static public TypeNotes find(int id)
        {
            return alltypes.get(id);
        }

        static public TypeNotes find(DapType dt)
        {
            for(Map.Entry<Integer, TypeNotes> entry : alltypes.entrySet()) {
                if(entry.getValue().get() == dt) {
                    return entry.getValue();
                }
            }
            return null;
        }

        public int opaquelen = 0;
        public int enumbase = 0;

        public TypeNotes(int g, int id)
        {
            super(g, id);
            alltypes.put(id, this);
        }

        public DapType get()
        {
            return (DapType) this.node;
        }

        public TypeNotes setOpaque(int len)
        {
            opaquelen = len;
            return this;
        }

        public TypeNotes setEnumBaseType(int bt) {this.enumbase = bt; return this;}

        public TypeNotes set(DapNode node)
        {
            return (TypeNotes) super.set(node);
        }

        static {
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

    static public class VarNotes extends Notes
    {
        static Map<Long, VarNotes> allvars = new HashMap<>();

        static public VarNotes find(int gid, int vid)
        {
            long gv = (((long) gid) << 32) | vid;
            return allvars.get(gv);
        }

        protected TypeNotes basetype = null;
        public int fieldid = -1;

        public VarNotes(int g, int v)
        {
            super(g, v);
            long gv = (((long) g) << 32) | v;
            allvars.put(gv, this);
        }

        public VarNotes setType(TypeNotes ti)
        {
            this.basetype = ti;
            return this;
        }

        public DapVariable get()
        {
            return (DapVariable) this.node;
        }

        public VarNotes set(DapNode node)
        {
            return (VarNotes) super.set(node);
        }

        public VarNotes setField(int id) {this.fieldid = id; return this;}
    }

}
