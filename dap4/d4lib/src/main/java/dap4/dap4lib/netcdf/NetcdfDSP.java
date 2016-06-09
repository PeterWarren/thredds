/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

/*
TODO:
1. make sure all nodes areproperly annotated
*/


package dap4.dap4lib.netcdf;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import dap4.core.data.DSP;
import dap4.core.data.DapDataFactory;
import dap4.core.dmr.*;
import dap4.core.util.DapContext;
import dap4.core.util.DapException;
import dap4.core.util.DapUtil;
import dap4.dap4lib.AbstractDSP;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dap4.dap4lib.netcdf.DapNetcdf.*;

/**
 * DSP for reading netcdf files through jni interface to netcdf4 library
 */
public class NetcdfDSP extends AbstractDSP
{
    //////////////////////////////////////////////////
    // Constants

    static public final boolean DEBUG = false;

    static String PATHSUFFIX = "/src/data";

    static String[] EXTENSIONS = new String[]{".nc", ".hdf5"};

    // Define reserved attributes
    static public final String UCARTAGVLEN = "^edu.ucar.isvlen";
    static public final String UCARTAGOPAQUE = "^edu.ucar.opaque.size";
    static public final String UCARTAGUNLIM = "^edu.ucar.isunlim";

    // Annotation name for all nodes
    static public final int NC4DSPNODES = "NC4DSPNODES".hashCode();

    static final Pointer NC_NULL = Pointer.NULL;
    static final int NC_FALSE = 0;
    static final int NC_TRUE = 1;
    // "null" id(s)
    static public final int NC_GRPNULL = 0;
    static public final int NC_IDNULL = -1;
    static public final int NC_NOERR = 0;

    static int NC_INT_BYTES = (java.lang.Integer.SIZE / java.lang.Byte.SIZE);
    static int NC_LONG_BYTES = (Native.LONG_SIZE);
    static int NC_POINTER_BYTES = (Native.POINTER_SIZE);
    static int NC_SIZET_BYTES = (Native.SIZE_T_SIZE);

    //////////////////////////////////////////////////
    // com.sun.jna.Memory control

    static abstract protected class Mem
    {
        static Memory
        allocate(long size)
        {
            if(size == 0)
                throw new IllegalArgumentException("Attempt to allocate zero bytes");
            Memory m = new Memory(size);
            return m;
        }
    }


    //////////////////////////////////////////////////
    // Type decls

    static protected class Groupinfo
    {
        int parent;
        int gid;
        DapGroup group = null;

        Groupinfo(int p, int g)
        {
            this.parent = p;
            this.gid = g;
        }

        Groupinfo setGroup(DapGroup g)
        {
            this.group = g;
            return this;
        }

        boolean isRoot()
        {
            return this.parent == this.gid;
        }

    }

    static protected class Diminfo
    {
        int gid;
        int did;
        DapDimension dim = null;

        Diminfo(int g, int d)
        {
            this.gid = g;
            this.did = d;
        }

        Diminfo setDim(DapDimension d)
        {
            this.dim = d;
            return this;
        }
    }

    static protected class Typeinfo
    {
        int gid;
        int tid;
        boolean isenum = false;
        boolean isopaque = false;
        int basetype = NC_NAT;
        int opaquelen = 0;
        TypeSort sort = null;
        DapDecl type = null; // use DapDecl to handle compount->DapStructure

        Typeinfo(int g, int t)
        {
            this.gid = g;
            this.tid = t;
        }

        Typeinfo setOpaque(boolean tf, int len)
        {
            isopaque = tf;
            opaquelen = len;
            return this;
        }

        Typeinfo setEnum(boolean tf, int bt)
        {
            isenum = tf;
            basetype = bt;
            return this;
        }

        Typeinfo setSort(TypeSort s)
        {
            sort = s;
            return this;
        }

        Typeinfo setType(DapDecl t)
        {
            this.type = t;
            return this;
        }
    }

    static protected class Varinfo
    {
        int gid;
        int vid;
        int basetype;
        DapVariable var = null;

        Varinfo(int g, int v, int bt)
        {
            this.gid = g;
            this.vid = v;
            basetype = bt;
        }

        Varinfo setVar(DapVariable v)
        {
            this.var = v;
            return this;
        }
    }

    //////////////////////////////////////////////////
    // Static variables

    //////////////////////////////////////////////////
    // Static methods

    /**
     * A path is file if it has no base protocol or is file:
     *
     * @param path
     * @param context Any parameters that may help to decide.
     * @return true if this path appears to be processible by this DSP
     */
    static public boolean dspMatch(String path, DapContext context)
    {
        for(String s : EXTENSIONS) {
            if(path.endsWith(s)) return true;
        }
        return false;
    }

    //////////////////////////////////////////////////
    // Instance Variables

    DapNetcdf nc4 = null;

    boolean trace = false;
    boolean closed = false;

    int ncid = -1;        // file id
    int format = 0;       // from nc_inq_format
    int mode = 0;
    String path = null;

    String pathprefix = null;

    Nc4DapFactory dmrfactory = null;
    Nc4DataFactory datafactory = null;

    Map<Integer, Groupinfo> allgroups = new HashMap<>();
    Map<Integer, Diminfo> alldims = new HashMap<>();
    Map<Integer, Typeinfo> alltypes = new HashMap<>();
    List<Varinfo> allvars = new ArrayList<>();

    //////////////////////////////////////////////////
    // Constructor(s)

    public NetcdfDSP()
            throws DapException
    {
        if(this.nc4 == null) {
            try {
                this.nc4 = NetcdfLoader.load();
            } catch (IOException ioe) {
                throw new DapException(ioe);
            }
            if(this.nc4 == null)
                throw new DapException("Could not load libnetcdf");
        }
        dmrfactory = new Nc4DMRFactory();
        datafactory = new Nc4DataFactory();
        defineatomictypes();
    }

    void
    defineatomictypes()
    {
        alltypes.put(NC_BYTE, new Typeinfo(0, NC_BYTE)
		.setSort(TypeSort.Int8)
		.setType(DapType.INT8));
        alltypes.put(NC_CHAR, new Typeinfo(0, NC_CHAR)
		.setSort(TypeSort.Char)
		.setType(DapType.CHAR));
        alltypes.put(NC_SHORT, new Typeinfo(0, NC_SHORT)
		.setSort(TypeSort.Int16)
		.setType(DapType.INT16));
        alltypes.put(NC_INT, new Typeinfo(0, NC_INT)
		.setSort(TypeSort.Int32)
		.setType(DapType.INT32));
        alltypes.put(NC_FLOAT, new Typeinfo(0, NC_FLOAT)
		.setSort(TypeSort.Float32)
		.setType(DapType.FLOAT32));
        alltypes.put(NC_DOUBLE, new Typeinfo(0, NC_DOUBLE)
		.setSort(TypeSort.Float64)
		.setType(DapType.FLOAT64));
        alltypes.put(NC_UBYTE, new Typeinfo(0, NC_UBYTE)
		.setSort(TypeSort.UInt8)
		.setType(DapType.UINT8));
        alltypes.put(NC_USHORT, new Typeinfo(0, NC_USHORT)
		.setSort(TypeSort.UInt16)
		.setType(DapType.UINT16));
        alltypes.put(NC_UINT, new Typeinfo(0, NC_UINT)
		.setSort(TypeSort.UInt32)
		.setType(DapType.UINT32));
        alltypes.put(NC_INT64, new Typeinfo(0, NC_INT64)
		.setSort(TypeSort.Int64)
		.setType(DapType.INT64));
        alltypes.put(NC_UINT64, new Typeinfo(0, NC_UINT64)
		.setSort(TypeSort.UInt64)
		.setType(DapType.UINT64));
        alltypes.put(NC_STRING, new Typeinfo(0, NC_STRING)
		.setSort(TypeSort.String)
		.setType(DapType.STRING));
    }

    @Override
    public DSP
    open(String path, DapContext cxt)
            throws DapException
    {
        int ret, mode;
        IntByReference ncidp = new IntByReference();
        try {
            mode = NC_NOWRITE;
            errcheck(ret = nc4.nc_open(path, mode, ncidp));
            this.ncid = ncidp.getValue();
            // Figure out what kind of file
            IntByReference formatp = new IntByReference();
            errcheck(ret = nc4.nc_inq_format(ncid, formatp));
            this.format = formatp.getValue();
            if(DEBUG)
                System.out.printf("TestNetcdf: open: %s; ncid=%d; format=%d%n",
                        path, ncid, this.format);
            // create and fill the root group
            buildrootgroup(ncid);
            getDMR().finish();
            buildDataDataset();
            return this;
        } catch (Exception t) {
            t.printStackTrace();
        }
        return null;
    }

    @Override
    public void close()
            throws DapException
    {
        if(this.closed) return;
        if(this.ncid < 0) return;
        int ret = nc4.nc_close(ncid);
        errcheck(ret);
        closed = true;
        if(trace)
            System.out.printf("NetcdfDSP: closed: %s%n", path);
    }

    //////////////////////////////////////////////////
    // Accessors

    //////////////////////////////////////////////////

    protected void
    buildrootgroup(int ncid)
            throws DapException
    {
        int ret;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        errcheck(ret = nc4.nc_inq_grpname(ncid, namep));
        Groupinfo gi = new Groupinfo(ncid, ncid);
        DapDataset g = dmrfactory.newDataset(makeString(namep), ncid);
        gi.setGroup(g);
        setDMR(g);
        dmrfactory.enterContainer(g);
        fillgroup(ncid);
        dmrfactory.leaveContainer();
    }

    protected void
    fillgroup(int gid)
            throws DapException
    {
        int ret, mode;
        int[] dims = getDimensions(gid);
        int[] udims = getUnlimitedDimensions(gid);
        for(int dimid : dims) {
            builddim(gid, dimid, udims);
        }
        int[] typeids = getUserTypes(gid);
        for(int typeid : typeids) {
            buildusertype(gid, typeid);
        }
        int[] varids = getVars(gid);
        for(int varid : varids) {
            buildvar(gid, varid);
        }
        // globalattributes
        String[] gattnames = getAttributes(gid, NC_GLOBAL);
        for(String ga : gattnames) {
            buildattr(gid, NC_GLOBAL, ga);
        }
        int[] groupids = getGroups(gid);
        for(int groupid : groupids) {
            buildgroup(gid, groupid);
        }
    }

    protected void
    buildgroup(int parent, int gid)
            throws DapException
    {
        int ret;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        errcheck(ret = nc4.nc_inq_grpname(gid, namep));
        Groupinfo gi = new Groupinfo(parent, gid);
        DapGroup g = dmrfactory.newGroup(makeString(namep), gid);
        gi.setGroup(g);
        dmrfactory.enterContainer(g);
        fillgroup(gid);
    }

    protected void
    builddim(int gid, int did, int[] udims)
            throws DapException
    {
        int ret = NC_NOERR;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        SizeTByReference lenp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_dim(gid, did, namep, lenp));
        String name = makeString(namep);
        int len = lenp.intValue();
        boolean isunlimited = contains(udims, did);
        Diminfo di = new Diminfo(gid, did);
        alldims.put(did, di);
        DapDimension dim = dmrfactory.newDimension(name, lenp.longValue(), did);
        di.setDim(dim);
        if(isunlimited) {
            DapAttribute ultag = dmrfactory.newAttribute(UCARTAGUNLIM, DapType.INT8);
            ultag.setValues(new Object[]{(Byte) (byte) 1});
            dim.addAttribute(ultag);
        }
        if(trace)
            System.out.printf("NetcdfDSP: dimension: %s size=%d%n", name, dim.getSize());
    }

    protected void
    buildusertype(int gid, int tid)
            throws DapException
    {
        int ret = NC_NOERR;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        SizeTByReference lenp = new SizeTByReference();
        IntByReference basetypep = new IntByReference();
        IntByReference classp = new IntByReference();
        SizeTByReference nfieldsp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_user_type(gid, tid, namep, lenp, basetypep, nfieldsp, classp));
        String name = makeString(namep);
        int basetype = basetypep.getValue();
        Typeinfo ti = new Typeinfo(gid, tid);
        alltypes.put(tid, ti);
        switch (classp.getValue()) {
        case NC_OPAQUE:
            buildopaquetype(ti, name, lenp.intValue());
            break;
        case NC_ENUM:
            buildenumtype(ti, name, basetype);
            break;
        case NC_COMPOUND:
            buildcompoundtype(ti, name, nfieldsp.intValue());
            break;
        case NC_VLEN:
            buildvlentype(ti, name, basetype);
            break;
        default:
            throw new DapException("Unknown class: " + classp.getValue());
        }
    }

    protected void
    buildopaquetype(Typeinfo ti, String name, int len)
            throws DapException
    {
        int ret;
        ti.setOpaque(true, len);
        DapType dt = DapType.lookup(TypeSort.Opaque);
        ti.setType(dt);
    }

    protected void
    buildenumtype(Typeinfo ti, String name, int basetype)
            throws DapException
    {
        int ret;
        SizeTByReference nmembersp = new SizeTByReference();
        SizeTByReference sizep = new SizeTByReference();
        byte[] namep = new byte[NC_MAX_NAME + 1];
        IntByReference basetypep = new IntByReference();
        IntByReference valuep = new IntByReference();

        ti.setEnum(true, basetype);
        if(!isintegertype(basetype))
            throw new DapException("Enum base type must be integer type");

        errcheck(ret = nc4.nc_inq_enum(ti.gid, ti.tid, namep, basetypep, sizep, nmembersp));

        Typeinfo bt = findtype(basetype);
        DapEnumeration de = dmrfactory.newEnumeration(name, DapType.lookup(bt.sort), ti.tid);
        ti.setType(de);

        // build list of enum consts
        int nconsts = nmembersp.intValue();
        for(int i = 0; i < nconsts; i++) {
            // Get info about the ith const
            errcheck(ret = nc4.nc_inq_enum_member(ti.gid, ti.tid, i, namep, valuep));
            de.addEnumConst(dmrfactory.newEnumConst(makeString(namep), (long) valuep.getValue()));
        }
    }

    protected void
    buildcompoundtype(Typeinfo ti, String name, long nfields)
            throws DapException
    {
        DapStructure ds = dmrfactory.newStructure(name, ti.tid);
        ti.setType(ds);
        for(int i = 0; i < nfields; i++) {
            buildfield(ti, i);
        }
    }

    protected void
    buildfield(Typeinfo ti, int fid)
            throws DapException
    {
        int ret;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        SizeTByReference offsetp = new SizeTByReference();
        IntByReference fieldtypep = new IntByReference();
        IntByReference ndimsp = new IntByReference();

        // Get everything but actual dims
        errcheck(ret = nc4.nc_inq_compound_field(ti.gid, ti.tid, fid, namep,
                offsetp, fieldtypep, ndimsp, NC_NULL));
        int fieldtype = fieldtypep.getValue();
        Typeinfo baset = findtype(fieldtype);
        if(baset == null)
            throw new DapException("Undefined field base type: " + fieldtype);
        int[] dimsizes = getFieldDimsizes(ti.gid, ti.tid, fid, ndimsp.getValue());
        makeField(ti, fid, makeString(namep), baset, offsetp.intValue(), dimsizes);
    }

    protected void
    makeField(Typeinfo parent, int index, String name, Typeinfo baset, int offset, int[] dimsizes)
            throws DapException
    {
        DapVariable field;
        switch (baset.sort) {
        case Struct:
            field = dmrfactory.newStructure(name, index);
            break;
        case Seq:
            field = dmrfactory.newSequence(name, index);
            break;
        default:
            field = dmrfactory.newAtomicVariable(name, (DapType) baset.type, index);
            break;
        }
        // set dimsizes
        if(dimsizes.length > 0) {
            for(int i = 0; i < dimsizes.length; i++) {
                DapDimension dim = dmrfactory.newDimension(null, dimsizes[i], NC_IDNULL);
                field.addDimension(dim);
            }
        }
    }

    protected void
    buildvar(int gid, int vid)
            throws DapException
    {
        int ret;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        IntByReference ndimsp = new IntByReference();
        IntByReference xtypep = new IntByReference();
        IntByReference nattsp = new IntByReference();
        errcheck(ret = nc4.nc_inq_var(gid, vid, namep, xtypep, ndimsp, NC_NULL, nattsp));
        int xtype = xtypep.getValue();
        Varinfo vi = new Varinfo(gid, vid, xtype);
        allvars.add(vi);
        Typeinfo ti = findtype(xtype);
        if(ti == null)
            throw new DapException("Unknown type id: " + xtype);
        DapVariable var = dmrfactory.newVariable(makeString(namep), (DapType) ti.type, gid, vid);
        vi.setVar(var);
        int[] dimids = getVardims(gid, vid, ndimsp.getValue());
        for(int i = 0; i < dimids.length; i++) {
            Diminfo di = finddim(dimids[i]);
            if(di == null)
                throw new DapException("Undefined variable dimension id: " + dimids[i]);
            var.addDimension(di.dim);
        }
        // Now, if this is of type opaque, tag it with the size
        Typeinfo bti = findtype(xtype);
        if(bti == null)
            throw new DapException("Undefined variable basetype: " + xtype);
        if(bti.isenum) {
            DapAttribute sizetag = dmrfactory.newAttribute(UCARTAGOPAQUE, DapType.INT64);
            sizetag.setValues(new Object[]{(long) bti.opaquelen});
            var.addAttribute(sizetag);
        }
        // fill in any attributes
        String[] attnames = getAttributes(gid, vid);
        for(String a : attnames) {
            buildattr(gid, vid, a);
        }
    }

    protected void
    buildvlentype(Typeinfo ti, String vname, int basetype)
            throws DapException
    {
        int ref;
        // We map vlen to a sequence with a single field
        // of the basetype. Field name is same as the vlen type
        DapSequence ds = dmrfactory.newSequence(vname, ti.tid);
        ti.setType(ds);
        Typeinfo baset = findtype(basetype);
        if(baset == null)
            throw new DapException("Undefined vlen basetype: " + basetype);
        makeField(ti, 0, vname, baset, 0, new int[0]);
        // Annotate to indicate that this came from a vlen
        DapAttribute tag = dmrfactory.newAttribute(UCARTAGVLEN, DapType.INT8);
        tag.setValues(new Object[]{(Byte) (byte) 1});
        ds.addAttribute(tag);
    }

    protected void
    buildattr(int gid, int vid, String name)
            throws DapException
    {
        int ret;
        boolean isglobal = (vid == NC_GLOBAL);
        IntByReference basetypep = new IntByReference();
        errcheck(ret = nc4.nc_inq_atttype(gid, vid, name, basetypep));
        int basetype = basetypep.getValue();
        if(!islegalattrtype(basetype))
            throw new DapException("Non-atomic attribute types not supported: " + name);
        SizeTByReference countp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_attlen(gid, vid, name, countp));
        // Get the values of the attribute
        Object[] values = getAttributeValues(gid, vid, name, basetype, countp.intValue());
        Typeinfo ti = findtype(basetype);
        DapAttribute da = dmrfactory.newAttribute(name, (DapType) ti.type);
        da.setValues(values);

        if(isglobal) {
            Groupinfo gi = findgroup(gid);
        } else {
            Varinfo vi = findvar(gid, vid);
            vi.var.addAttribute(da);
        }
    }

    //////////////////////////////////////////////////

    int[]
    getGroups(int gid)
            throws DapException
    {
        int ret, n;
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_grps(gid, ip, NC_NULL));
        n = ip.getValue();
        int[] grpids;
        if(n > 0) {
            Memory mem = Mem.allocate(NC_INT_BYTES * n);
            errcheck(ret = nc4.nc_inq_grps(gid, ip, mem));
            grpids = mem.getIntArray(0, n);
        } else
            grpids = new int[0];
        return grpids;
    }

    int[]
    getDimensions(int gid)
            throws DapException
    {
        int ret, n;
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_ndims(gid, ip));
        n = ip.getValue();
        int[] dimids;
        if(n > 0) {
            Memory mem = Mem.allocate(NC_INT_BYTES * n);
            errcheck(ret = nc4.nc_inq_dimids(gid, ip, mem, NC_FALSE));
            dimids = mem.getIntArray(0, n);
        } else
            dimids = new int[0];
        return dimids;
    }

    int[]
    getUnlimitedDimensions(int gid)
            throws DapException
    {
        int ret, n;
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_unlimdims(gid, ip, NC_NULL));
        n = ip.getValue();
        int[] dimids;
        if(n == 0)
            dimids = new int[0];
        else {
            Memory mem = Mem.allocate(NC_INT_BYTES * n);
            errcheck(ret = nc4.nc_inq_unlimdims(gid, ip, mem));
            dimids = mem.getIntArray(0, n);
        }
        return dimids;
    }

    int[]
    getUserTypes(int gid)
            throws DapException
    {
        int ret, n;
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_typeids(gid, ip, NC_NULL));
        n = ip.getValue();
        int[] typeids;
        if(n > 0) {
            Memory mem = Mem.allocate(NC_INT_BYTES * n);
            errcheck(ret = nc4.nc_inq_typeids(gid, ip, mem));
            typeids = mem.getIntArray(0, n);
        } else
            typeids = new int[0];
        return typeids;
    }

    int[]
    getVars(int gid)
            throws DapException
    {
        int ret, n;
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_nvars(gid, ip));
        n = ip.getValue();
        int[] ids;
        if(n > 0) {
            Memory mem = Mem.allocate(NC_INT_BYTES * n);
            errcheck(ret = nc4.nc_inq_varids(gid, ip, mem));
            ids = mem.getIntArray(0, n);
        } else
            ids = new int[0];
        return ids;
    }

    int[]
    getVardims(int gid, int vid, int ndims)
            throws DapException
    {
        int ret;
        int[] dimids;

        if(ndims > 0) {
            byte[] namep = new byte[NC_MAX_NAME + 1];
            IntByReference ndimsp = new IntByReference();
            IntByReference xtypep = new IntByReference();
            IntByReference nattsp = new IntByReference();
            Memory mem = Mem.allocate(NC_INT_BYTES * ndims);
            errcheck(ret = nc4.nc_inq_var(gid, vid, namep, xtypep, ndimsp, mem, nattsp));
            dimids = mem.getIntArray(0, ndims);
        } else
            dimids = new int[0];
        return dimids;
    }

    int[]
    getFieldDimsizes(int gid, int tid, int fid, int ndims)
            throws DapException
    {
        int ret;
        int[] dimsizes;
        if(ndims > 0) {
            byte[] name = new byte[NC_MAX_NAME + 1];
            SizeTByReference offsetp = new SizeTByReference();
            IntByReference fieldtypep = new IntByReference();
            IntByReference ndimsp = new IntByReference();
            Memory mem = Mem.allocate(NC_INT_BYTES * ndims);
            errcheck(ret = nc4.nc_inq_compound_field(gid, tid, fid, name,
                    offsetp, fieldtypep, ndimsp, mem));
            dimsizes = mem.getIntArray(0, ndims);
        } else
            dimsizes = new int[0];
        return dimsizes;
    }

    String[]
    getAttributes(int gid, int vid)
            throws DapException
    {
        int ret, n;
        boolean isglobal = (vid == NC_GLOBAL);
        IntByReference nattsp = new IntByReference();
        byte[] namep = new byte[NC_MAX_NAME + 1];
        IntByReference ndimsp = new IntByReference();
        IntByReference xtypep = new IntByReference();
        if(isglobal)
            errcheck(ret = nc4.nc_inq_natts(gid, nattsp));
        else {
            errcheck(ret = nc4.nc_inq_var(gid, vid, namep, xtypep, ndimsp, NC_NULL, nattsp));
        }
        n = nattsp.getValue();
        String[] names = new String[n];
        for(int i = 0; i < n; i++) {
            errcheck(ret = nc4.nc_inq_attname(gid, vid, i, namep));
            names[i] = makeString(namep);
        }
        return names;
    }

    Object[]
    getAttributeValues(int gid, int vid, String name, int basetype, int count)
            throws DapException
    {
        int ret;
        // Currently certain types only are allowed.
        if(!islegalattrtype(basetype))
            throw new DapException("Unsupported attribute type: " + typenamefor(basetype));
        if(isenumtype(basetype))
            basetype = findtype(basetype).basetype;
        Object valuelist = getRawAttributeValues(basetype, count, gid, vid, name);
        Object[] values = new Object[count];
        values = convert(count, valuelist, basetype);
        return values;
    }

    Object
    getRawAttributeValues(int basetype, int count, int gid, int vid, String name)
            throws DapException
    {
        int ret = NC_NOERR;
        SizeTByReference lenp = new SizeTByReference();
        byte[] namep = new byte[NC_MAX_NAME + 1];
        errcheck(ret = nc4.nc_inq_type(gid, basetype, namep, lenp));
        int nativetypesize = lenp.intValue();
        if(isstringtype(basetype))
            nativetypesize = NC_POINTER_BYTES;
        else if(nativetypesize == 0)
            throw new DapException("Illegal Type Sort:" + typenamefor(basetype));
        Object values = null;
        if(count > 0) {
            long totalsize = nativetypesize * count;
            Memory mem = Mem.allocate(totalsize);
            errcheck(ret = nc4.nc_get_att(gid, vid, name, mem));
            if(isopaquetype(basetype)) {
                values = mem.getByteArray(0, (int) totalsize);
            } else switch (basetype) {
            case NC_CHAR:
                values = mem.getByteArray(0, count);
                break;
            case NC_BYTE:
                values = mem.getByteArray(0, count);
                break;
            case NC_UBYTE:
                values = mem.getByteArray(0, count);
                break;
            case NC_SHORT:
                values = mem.getShortArray(0, count);
                break;
            case NC_USHORT:
                values = mem.getShortArray(0, count);
                break;
            case NC_INT:
                values = mem.getIntArray(0, count);
                break;
            case NC_UINT:
                values = mem.getIntArray(0, count);
                break;
            case NC_INT64:
                values = mem.getLongArray(0, count);
                break;
            case NC_UINT64:
                values = mem.getLongArray(0, count);
                break;
            case NC_FLOAT:
                values = mem.getFloatArray(0, count);
                break;
            case NC_DOUBLE:
                values = mem.getDoubleArray(0, count);
                break;
            case NC_STRING:
                values = mem.getStringArray(0, count);
                break;
            default:
                throw new IllegalArgumentException("Unexpected sort: " + typenamefor(basetype));
            }
        }
        return values;
    }

    Object[]
    convert(int count, Object src, int basetype)
            throws DapException
    {
        int truetype = basetype;
        boolean isenum = isenumtype(basetype);
        boolean isopaque = isopaquetype(basetype);
        if(isenum)
            truetype = enumbasetype(basetype);
        Object[] dst;
        if(ischartype(basetype))
            dst = new Character[count];
        else
            dst = new Object[count];
        try {
            if(isenum) {
                return convert(count, src, truetype);
            } else if(isopaque) {
                byte[] alldata = (byte[]) src;
                int oplen = alldata.length / count;
                for(int i = 0; i < count; i++) {
                    dst[i] = new byte[oplen];
                    System.arraycopy(alldata, oplen * i, dst[i], 0, oplen);
                }
            } else {
                for(int i = 0; i < dst.length; i++) {
                    switch (basetype) {
                    case NC_CHAR:
                        if(src instanceof char[])
                            dst[i] = ((char[]) src)[i];
                        else
                            dst[i] = (char) (((byte[]) src)[i]);
                        break;
                    case NC_BYTE:
                    case NC_UBYTE:
                        dst[i] = ((byte[]) src)[i];
                        break;
                    case NC_SHORT:
                    case NC_USHORT:
                        dst[i] = ((short[]) src)[i];
                        break;
                    case NC_INT:
                    case NC_UINT:
                        dst[i] = ((int[]) src)[i];
                        break;
                    case NC_INT64:
                    case NC_UINT64:
                        dst[i] = ((long[]) src)[i];
                        break;
                    case NC_FLOAT:
                        dst[i] = ((float[]) src)[i];
                        break;
                    case NC_DOUBLE:
                        dst[i] = ((double[]) src)[i];
                        break;
                    case NC_STRING:
                        dst[i] = ((String[]) src)[i];
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected sort: " + typenamefor(basetype));
                    }
                }
            }
            return dst;
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new DapException(e);
        }
    }

    String makeString(byte[] b)
    {
        // null terminates
        int count;
        for(count = 0; (count < b.length && b[count] != 0); count++) {
            ;
        }
        return new String(b, 0, count, DapUtil.UTF8);
    }

    protected void
    errcheck(int ret)
            throws DapException
    {
        if(ret != 0) {
            String msg = String.format("TestNetcdf: errno=%d; %s", ret, nc4.nc_strerror(ret));
            if(DEBUG)
                System.err.println(msg);
            throw new DapException(msg);
        }
    }

    boolean
    contains(int[] list, int value)
    {
        for(int i = 0; i < list.length; i++) {
            if(list[i] == value) return true;
        }
        return false;
    }

    boolean
    islegalattrtype(int nctype)
    {
        return isatomictype(nctype)
                || isenumtype(nctype)
                || isopaquetype(nctype);
    }

    boolean
    isatomictype(int nctype)
    {
        return (nctype <= NC_MAX_ATOMIC_TYPE);
    }

    boolean
    isstringtype(int nctype)
    {
        return (nctype == NC_STRING);
    }

    boolean
    ischartype(int nctype)
    {
        return (nctype == NC_CHAR);
    }

    boolean
    isintegertype(int nctype)
    {
        return (nctype <= NC_UINT64 && nctype != NC_CHAR);
    }

    boolean
    isenumtype(int nctype)
    {
        Typeinfo ti = findtype(nctype);
        return (ti == null ? false : ti.isenum);
    }

    int
    enumbasetype(int nctype)
    {
        Typeinfo ti = findtype(nctype);
        return (ti == null || !ti.isenum ? NC_NAT : ti.basetype);
    }


    boolean
    isopaquetype(int nctype)
    {
        Typeinfo ti = findtype(nctype);
        return (ti == null ? false : ti.isopaque);
    }

    Typeinfo
    findtype(int id)
    {
        return alltypes.get(id);
    }

    String
    typenamefor(int id)
    {
        Typeinfo ti = findtype(id);
        return (ti == null ? null : ((DapNode)ti.type).getShortName());
    }

    Diminfo
    finddim(int id)
    {
        return alldims.get(id);
    }

    long
    dimsize(int did)
    {
        Diminfo di = finddim(did);
        return (di == null ? -1 : di.dim.getSize());
    }

    Varinfo
    findvar(int gid, int vid)
    {
        if(vid == NC_GLOBAL)
            return null;
        for(Varinfo vi : allvars) {
            if(vi.gid == gid && vi.vid == vid)
                return vi;
        }
        return null;
    }

    Groupinfo
    findgroup(int gid)
    {
        return allgroups.get(gid);
    }

    //////////////////////////////////////////////////
    // Data Build

    protected void
    buildDataDataset()
    {
    }

    @Override
    public void
    compile()
            throws DapException
    {
        assert (this.dataset != null && this.databuffer != null);
        if(DEBUG) {
            DapDump.dumpbytes(this.databuffer, false);
        }
        this.datadataset = factory.newDataset(this.dsp, this.dataset);
        this.dsp.setDataDataset(this.datadataset);

        // iterate over the variables represented in the databuffer
        for(DapVariable vv : this.dataset.getTopVariables()) {
            DataVariable array = compileVar(vv);
            this.datadataset.addVariable(array);
        }
    }

    protected DataVariable
    compileVar(DapVariable dapvar)
            throws DapException
    {
        DataVariable array = null;
        boolean isscalar = dapvar.getRank() == 0;
        if(dapvar.getSort() == DapSort.ATOMICVARIABLE) {
            array = compileAtomicVar((DapAtomicVariable)dapvar);
        } else if(dapvar.getSort() == DapSort.STRUCTURE) {
            if(isscalar)
                array = compileStructure((DapStructure) dapvar, null, 0);
            else
                array = compileStructureArray(dapvar);
        } else if(dapvar.getSort() == DapSort.SEQUENCE) {
            if(isscalar)
                array = compileSequence((DapSequence) dapvar, null, 0);
            else
                array = compileSequenceArray(dapvar);
        }
        if(dapvar.isTopLevel()) {
            // extract the checksum from databuffer src,
            // attach to the array, and make into an attribute
            byte[] checksum = getChecksum(databuffer);
            dapvar.setChecksum(checksum);
        }
        return array;
    }

    protected DataAtomic
    compileAtomicVar(DapAtomicVariable atomvar)
            throws DapException
    {
        DapType daptype = atomvar.getBaseType();
        D4DataAtomic data = (D4DataAtomic)factory.newAtomicVariable(this.dsp, atomvar, databuffer.position());
        long total = 0;
        long dimproduct = data.getCount();
        if(!daptype.isEnumType() && !daptype.isFixedSize()) {
            // this is a string, url, or opaque
            int[] positions = new int[(int) dimproduct];
            int savepos = databuffer.position();
            // Walk the bytestring and return the instance count (in databuffer)
            total = walkByteStrings(positions, databuffer);
            databuffer.position(savepos);// leave position unchanged
            data.setByteStringOffsets(total, positions);
        } else {
            total = dimproduct * daptype.getSize();
        }
        skip(databuffer, (int) total);
        return data;
    }

    /**
     * Compile a structure array.
     *
     * @param dapvar the template
     * @return A DataCompoundArray for the databuffer for this struct.
     * @throws DapException
     */
    DataCompoundArray
    compileStructureArray(DapVariable dapvar)
            throws DapException
    {
        DataCompoundArray structarray
                = factory.newCompoundArray(this.dsp, dapvar);
        DapStructure struct = (DapStructure) dapvar;
        long dimproduct = structarray.getCount();
        for(int i = 0; i < dimproduct; i++) {
            DataStructure instance = compileStructure(struct, structarray, i);
            structarray.addElement(instance);
        }
        return structarray;
    }


    /**
     * Compile a structure instance.
     *
     * @param dapstruct The template
     * @return A DataStructure for the databuffer for this struct.
     * @throws DapException
     */
    DataStructure
    compileStructure(DapStructure dapstruct, DataCompoundArray array, int index)
            throws DapException
    {
        DataStructure d4ds = factory.newStructure(dsp, dapstruct, array, index);
        List<DapVariable> dfields = dapstruct.getFields();
        for(int m = 0; m < dfields.size(); m++) {
            DapVariable dfield = dfields.get(m);
            DataVariable dvfield = compileVar(dfield);
            d4ds.addField(m, dvfield);
        }
        return d4ds;
    }

    /**
     * Compile a sequence array.
     *
     * @param dapvar the template
     * @return A DataCompoundArray for the databuffer for this sequence.
     * @throws DapException
     */
    DataCompoundArray
    compileSequenceArray(DapVariable dapvar)
            throws DapException
    {
        DapSequence dapseq = (DapSequence) dapvar;
        DataCompoundArray seqarray
                = factory.newCompoundArray(this.dsp, dapseq);
        long dimproduct = seqarray.getCount();
        for(int i = 0; i < dimproduct; i++) {
            DataSequence dseq = compileSequence(dapseq, seqarray, i);
            seqarray.addElement(dseq);
        }
        return seqarray;
    }

    /**
     * Compile a sequence from a set of records.
     *
     * @param dapseq The template for this sequence
     * @param array  the parent compound array
     * @param index  within the parent compound array
     * @return A DataSequence for the records for this sequence.
     * @throws DapException
     */
    DataSequence
    compileSequence(DapSequence dapseq, DataCompoundArray array, int index)
            throws DapException
    {
        List<DapVariable> dfields = dapseq.getFields();
        // Get the count of the number of records
        long nrecs = getCount(databuffer);
        DataSequence seq = factory.newSequence(this.dsp, dapseq, array, index);
        for(int r = 0; r < nrecs; r++) {
            DataRecord rec = factory.newRecord(this.dsp, dapseq, seq, r);
            for(int m = 0; m < dfields.size(); m++) {
                DapVariable dfield = dfields.get(m);
                DataVariable dvfield = compileVar(dfield);
                rec.addField(m, dvfield);
            }
            seq.addRecord(rec);
        }
        return seq;
    }

    //////////////////////////////////////////////////
    // Utilities

    protected byte[]
    getChecksum(ByteBuffer data)
            throws DapException
    {
        if(!ChecksumMode.enabled(RequestMode.DAP, checksummode)) return null;
        if(data.remaining() < CHECKSUMSIZE)
            throw new DapException("Short serialization: missing checksum");
        byte[] checksum = new byte[CHECKSUMSIZE];
        data.get(checksum);
        return checksum;
    }

    static protected void
    skip(ByteBuffer data, int count)
    {
        data.position(data.position() + count);
    }

    static protected int
    getCount(ByteBuffer data)
    {
        long count = data.getLong();
        count = (count & 0xFFFFFFFF);
        return (int) count;
    }

    /**
     * Compute the size in databuffer of the serialized form
     *
     * @param daptype
     * @return type's serialized form size
     */
    static protected int
    computeTypeSize(DapType daptype)
    {
        TypeSort atype = daptype.getAtomicType();
        return Dap4Util.daptypeSize(atype);
    }

    static protected long
    walkByteStrings(int[] positions, ByteBuffer databuffer)
    {
        int count = positions.length;
        long total = 0;
        int savepos = databuffer.position();
        // Walk each bytestring
        for(int i = 0; i < count; i++) {
            int pos = databuffer.position();
            positions[i] = pos;
            int size = getCount(databuffer);
            total += COUNTSIZE;
            total += size;
            skip(databuffer, size);
        }
        databuffer.position(savepos);// leave position unchanged
        return total;
    }

}
