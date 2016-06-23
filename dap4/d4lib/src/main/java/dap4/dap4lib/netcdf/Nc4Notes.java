/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.netcdf;

import dap4.core.dmr.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            return this;
        }

        public DapNode get()
        {
            return this.node;
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
            allgroups.put(g,this);
        }

        public DapGroup get()
        {
            return (DapGroup) this.node;
        }
    }

    static public class DimNotes extends Notes
    {
        static Map<Integer, DimNotes> alldims = new HashMap<>();

        static public DimNotes find(int gid)
        {
            return alldims.get(gid);
        }

        public DimNotes(int g, int id)
        {
            super(g, id);
            alldims.put(g,this);
        }

        public DapDimension get()
        {
            return (DapDimension) this.node;
        }
    }

    static public class TypeNotes extends Notes
    {
        static Map<Integer, TypeNotes> alltypes = new HashMap<>();

        static public TypeNotes find(int gid) {return alltypes.get(gid);}
        static public TypeNotes find(DapType dt)
        {
            for(Map.Entry<Integer, TypeNotes> entry : alltypes.entrySet())
                if(entry.getValue().get() == dt) {return entry.getValue();}
            return null;
        }

        int opaquelen = 0;

        public TypeNotes(int g, int id)
        {
            super(g, id);
            alltypes.put(g,this);
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

    }

    static public class VarNotes extends Notes
    {
        static List<VarNotes> allvars = new ArrayList<>();

        static public VarNotes find(int gid, int vid)
        {
            for(VarNotes vi : allvars) {
                if(vi.gid == gid && vi.id == vid)
                    return vi;
            }
            return null;
        }

        public TypeNotes basetype = null;

        public VarNotes(int g, int v)
        {
            super(g, v);
            allvars.add(this);
        }

        public VarNotes setType(TypeNotes ti) {this.basetype = ti; return this;}

        public DapVariable get()
        {
            return (DapVariable) this.node;
        }
    }
}
