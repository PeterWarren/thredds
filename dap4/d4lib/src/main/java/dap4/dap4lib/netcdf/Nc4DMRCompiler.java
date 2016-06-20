/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.dap4lib.netcdf;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import dap4.core.data.DataDataset;
import dap4.core.dmr.*;
import dap4.core.util.DapContext;
import dap4.core.util.DapException;
import dap4.core.util.DapUtil;

import static dap4.dap4lib.netcdf.DapNetcdf.*;
import static dap4.dap4lib.netcdf.Nc4DMR.*;
import static dap4.dap4lib.netcdf.Nc4Data.Mem;
import static dap4.dap4lib.netcdf.NetcdfDSP.EXTENSIONS;


/**
 * Compile netcdf file info into DMR
 */
public class Nc4DMRCompiler
{
    //////////////////////////////////////////////////
    // Constants

    static public final boolean DEBUG = false;

    // Define reserved attributes
    static public final String UCARTAGVLEN = "^edu.ucar.isvlen";
    static public final String UCARTAGOPAQUE = "^edu.ucar.opaque.size";
    static public final String UCARTAGUNLIM = "^edu.ucar.isunlim";

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

    protected DapNetcdf nc4 = null;

    protected boolean trace = false;
    protected boolean closed = false;

    protected int ncid = -1;        // file id
    protected int format = 0;       // from nc_inq_format
    protected int mode = 0;
    protected String path = null;

    protected String pathprefix = null;

    protected DataDataset dataset = null;


    protected Nc4DMRFactory factory = null;
    protected NetcdfDSP dsp = null;
    protected DapDataset dmr = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public Nc4DMRCompiler(NetcdfDSP dsp, int ncid, Nc4DMRFactory factory)
            throws DapException
    {
        this.dsp = dsp;
        this.nc4 = dsp.getJNI();
        this.path = dsp.getPath();
        this.ncid = ncid;
        this.factory = factory;
    }


    //////////////////////////////////////////////////
    // Main entry point

    public DapDataset
    compile()
            throws DapException
    {
        // create and fill the root group
        buildrootgroup(this.ncid);
        if(this.dmr != null) dmr.finish();
        return this.dmr;
    }

    //////////////////////////////////////////////////

    protected void
    buildrootgroup(int ncid)
            throws DapException
    {
        int ret;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        errcheck(ret = nc4.nc_inq_grpname(ncid, namep));
        GroupNotes gi = new GroupNotes(ncid, ncid);
        String[] pieces = DapUtil.canonicalpath(this.path).split("[/]");
        DapDataset g = factory.newDataset(pieces[pieces.length-1],gi);
        gi.set(g);
        this.dmr = g;
        factory.enterContainer(g);
        fillgroup(ncid);
        factory.leaveContainer();
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
        GroupNotes gi = new GroupNotes(parent, gid);
        DapGroup g = factory.newGroup(makeString(namep), gi);
        gi.set(g);
        factory.enterContainer(g);
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
        DimNotes di = new DimNotes(gid, did);
        DapDimension dim = factory.newDimension(name, lenp.longValue(), di);
        di.set(dim);
        if(isunlimited) {
            DapAttribute ultag = factory.newAttribute(UCARTAGUNLIM, DapType.INT8, null);
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
        TypeNotes ti = new TypeNotes(gid, tid);
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
    buildopaquetype(TypeNotes ti, String name, int len)
            throws DapException
    {
        int ret;
        ti.setOpaque(len);
        DapType dt = DapType.lookup(TypeSort.Opaque);
        ti.set(dt);
    }

    protected void
    buildenumtype(TypeNotes ti, String name, int basetype)
            throws DapException
    {
        int ret;
        SizeTByReference nmembersp = new SizeTByReference();
        SizeTByReference sizep = new SizeTByReference();
        byte[] namep = new byte[NC_MAX_NAME + 1];
        IntByReference basetypep = new IntByReference();
        IntByReference valuep = new IntByReference();
        TypeNotes base = TypeNotes.find(basetype);
        if(!isintegertype(base))
            throw new DapException("Enum base type must be integer type");
        errcheck(ret = nc4.nc_inq_enum(ti.gid, ti.id, namep, basetypep, sizep, nmembersp));
        DapEnumeration de = factory.newEnumeration(name, DapType.lookup(base.get().getTypeSort()), ti);
        ti.set(de);
        factory.enterContainer(de);
        // build list of enum consts
        int nconsts = nmembersp.intValue();
        for(int i = 0; i < nconsts; i++) {
            // Get info about the ith const
            errcheck(ret = nc4.nc_inq_enum_member(ti.gid, ti.id, i, namep, valuep));
            de.addEnumConst(factory.newEnumConst(makeString(namep), (long) valuep.getValue(), null));
        }
        factory.leaveContainer();
    }

    protected void
    buildcompoundtype(TypeNotes ti, String name, long nfields)
            throws DapException
    {
        DapStructure ds = factory.newStructure(name, ti);
        ti.set(ds);
        factory.enterContainer(ds);
        for(int i = 0; i < nfields; i++) {
            buildfield(ti, i);
        }
        factory.leaveContainer();
    }

    protected void
    buildfield(TypeNotes ti, int fid)
            throws DapException
    {
        int ret;
        byte[] namep = new byte[NC_MAX_NAME + 1];
        SizeTByReference offsetp = new SizeTByReference();
        IntByReference fieldtypep = new IntByReference();
        IntByReference ndimsp = new IntByReference();

        // Get everything but actual dims
        errcheck(ret = nc4.nc_inq_compound_field(ti.gid, ti.id, fid, namep,
                offsetp, fieldtypep, ndimsp, NC_NULL));
        int fieldtype = fieldtypep.getValue();
        TypeNotes baset = TypeNotes.find(fieldtype);
        if(baset == null)
            throw new DapException("Undefined field base type: " + fieldtype);
        int[] dimsizes = getFieldDimsizes(ti.gid, ti.id, fid, ndimsp.getValue());
        makeField(ti, fid, makeString(namep), baset, offsetp.intValue(), dimsizes);
    }

    protected void
    makeField(TypeNotes parent, int index, String name, TypeNotes baset, int offset, int[] dimsizes)
            throws DapException
    {
        DapVariable field;
        VarNotes vn = new VarNotes(parent.gid, parent.id).setField(index);
        switch (baset.get().getTypeSort()) {
        case Struct:
            field = factory.newStructure(name, vn);
            break;
        case Seq:
            field = factory.newSequence(name, vn);
            break;
        default:
            field = factory.newAtomicVariable(name, baset.get(), vn);
            break;
        }
        // set dimsizes
        if(dimsizes.length > 0) {
            for(int i = 0; i < dimsizes.length; i++) {
                DapDimension dim = factory.newDimension(null, dimsizes[i], null);
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
        String name = makeString(namep);
        TypeNotes xtype = TypeNotes.find(xtypep.getValue());
        VarNotes vi = new VarNotes(gid, vid).setType(xtype);
        if(xtype == null)
            throw new DapException("Unknown type id: " + xtype.id);
        DapVariable var = factory.newAtomicVariable(name, xtype.get(),vi);
        vi.set(var);
        int[] dimids = getVardims(gid, vid, ndimsp.getValue());
        for(int i = 0; i < dimids.length; i++) {
            DimNotes di = DimNotes.find(dimids[i]);
            if(di == null)
                throw new DapException("Undefined variable dimension id: " + dimids[i]);
            var.addDimension(di.get());
        }
        // Now, if this is of type opaque, tag it with the size
        if(xtype.get().isOpaqueType()) {
            DapAttribute sizetag = factory.newAttribute(UCARTAGOPAQUE, DapType.INT64, null);
            sizetag.setValues(new Object[]{(long) xtype.opaquelen});
            var.addAttribute(sizetag);
        }
        // fill in any attributes
        String[] attnames = getAttributes(gid, vid);
        for(String a : attnames) {
            buildattr(gid, vid, a);
        }
    }

    protected void
    buildvlentype(TypeNotes ti, String vname, int basetype)
            throws DapException
    {
        int ref;
        // We map vlen to a sequence with a single field
        // of the basetype. Field name is same as the vlen type
        DapSequence ds = factory.newSequence(vname, ti);
        ti.set(ds);
        factory.enterContainer(ds);
        TypeNotes baset = TypeNotes.find(basetype);
        if(baset == null)
            throw new DapException("Undefined vlen basetype: " + basetype);
        makeField(ti, 0, vname, baset, 0, new int[0]);
        // Annotate to indicate that this came from a vlen
        DapAttribute tag = factory.newAttribute(UCARTAGVLEN, DapType.INT8, null);
        tag.setValues(new Object[]{(Byte) (byte) 1});
        ds.addAttribute(tag);
        factory.leaveContainer();
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
        TypeNotes base = TypeNotes.find(basetype);
        if(!islegalattrtype(base))
            throw new DapException("Non-atomic attribute types not supported: " + name);
        SizeTByReference countp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_attlen(gid, vid, name, countp));
        // Get the values of the attribute
        Object[] values = getAttributeValues(gid, vid, name, base, countp.intValue());
        DapAttribute da = factory.newAttribute(name, (DapType) base.get(), null);
        da.setValues(values);

        if(isglobal) {
            GroupNotes gi = GroupNotes.find(gid);
        } else {
            VarNotes vi = VarNotes.find(gid, vid);
            vi.get().addAttribute(da);
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
    getAttributeValues(int gid, int vid, String name, TypeNotes base, int count)
            throws DapException
    {
        int ret;
        // Currently certain types only are allowed.
        if(!islegalattrtype(base))
            throw new DapException("Unsupported attribute type: " + base.get().getShortName());
        if(isenumtype(base))
            base = enumbasetype(base);
        Object valuelist = getRawAttributeValues(base, count, gid, vid, name);
        Object[] values = new Object[count];
        values = convert(count, valuelist, base);
        return values;
    }

    Object
    getRawAttributeValues(TypeNotes base, int count, int gid, int vid, String name)
            throws DapException
    {
        int nativetypesize = base.get().getSize();
        if(isstringtype(base))
            nativetypesize = NC_POINTER_BYTES;
        else if(nativetypesize == 0)
            throw new DapException("Illegal Type Sort:" + base.get().getShortName());
        Object values = null;
        if(count > 0) {
            int ret;
            long totalsize = nativetypesize * count;
            Memory mem = Mem.allocate(totalsize);
            errcheck(ret = nc4.nc_get_att(gid, vid, name, mem));
            switch (base.get().getTypeSort()) {
            case Char:
                values = mem.getByteArray(0, count);
                break;
            case Int8:
                values = mem.getByteArray(0, count);
                break;
            case UInt8:
                values = mem.getByteArray(0, count);
                break;
            case Int16:
                values = mem.getShortArray(0, count);
                break;
            case UInt16:
                values = mem.getShortArray(0, count);
                break;
            case Int32:
                values = mem.getIntArray(0, count);
                break;
            case UInt32:
                values = mem.getIntArray(0, count);
                break;
            case Int64:
                values = mem.getLongArray(0, count);
                break;
            case UInt64:
                values = mem.getLongArray(0, count);
                break;
            case Float32:
                values = mem.getFloatArray(0, count);
                break;
            case Float64:
                values = mem.getDoubleArray(0, count);
                break;
            case String:
                values = mem.getStringArray(0, count);
                break;
            case Opaque:
                values = mem.getByteArray(0, (int) totalsize);
                break;
            case Enum:
                break;
            default:
                throw new IllegalArgumentException("Unexpected sort: " + base.get().getShortName());
            }
        }
        return values;
    }

    Object[]
    convert(int count, Object src, TypeNotes basetype)
            throws DapException
    {
        boolean isenum = isenumtype(basetype);
        boolean isopaque = isopaquetype(basetype);
        TypeNotes truetype = basetype;
        if(isenum)
            truetype = enumbasetype(basetype);

        Object[] dst;
        if(ischartype(basetype))
            dst = new Character[count];
        else
            dst = new Object[count];
        try {
            for(int i = 0; i < dst.length; i++) {
                switch (basetype.get().getTypeSort()) {
                case Char:
                    if(src instanceof char[])
                        dst[i] = ((char[]) src)[i];
                    else
                        dst[i] = (char) (((byte[]) src)[i]);
                    break;
                case Int8:
                case UInt8:
                    dst[i] = ((byte[]) src)[i];
                    break;
                case Int16:
                case UInt16:
                    dst[i] = ((short[]) src)[i];
                    break;
                case Int32:
                case UInt32:
                    dst[i] = ((int[]) src)[i];
                    break;
                case Int64:
                case UInt64:
                    dst[i] = ((long[]) src)[i];
                    break;
                case Float32:
                    dst[i] = ((float[]) src)[i];
                    break;
                case Float64:
                    dst[i] = ((double[]) src)[i];
                    break;
                case String:
                    dst[i] = ((String[]) src)[i];
                    break;
                case Opaque:
                    byte[] alldata = (byte[]) src;
                    int oplen = alldata.length / count;
                    for(i = 0; i < count; i++) {
                        dst[i] = new byte[oplen];
                        System.arraycopy(alldata, oplen * i, dst[i], 0, oplen);
                    }
                    break;
                case Enum:
                    dst = convert(count, src, truetype);
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected sort: " + basetype.get().getShortName());
                }
            }
            return dst;
        } catch (IllegalArgumentException |
                ArrayIndexOutOfBoundsException e
                ) {
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
    islegalattrtype(TypeNotes nctype)
    {
        return isatomictype(nctype)
                || isenumtype(nctype)
                || isopaquetype(nctype);
    }

    boolean
    isatomictype(TypeNotes t)
    {
        return (t.id <= NC_MAX_ATOMIC_TYPE);
    }

    boolean
    isstringtype(TypeNotes nctype)
    {
        return (nctype.id == NC_STRING);
    }

    boolean
    ischartype(TypeNotes t)
    {
        return (t.id == NC_CHAR);
    }

    boolean
    isintegertype(TypeNotes t)
    {
        return (t.id <= NC_UINT64 && t.id != NC_CHAR);
    }

    boolean
    isenumtype(TypeNotes nctype)
    {
        return (nctype == null ? false : nctype.get().isEnumType());
    }

    TypeNotes
    enumbasetype(TypeNotes etype)
    {
        if(etype == null || !etype.get().isEnumType()) return null;
        return (TypeNotes) ((DapEnumeration) etype.get()).getBaseType().annotation();
    }


    boolean
    isopaquetype(TypeNotes t)
    {
        return (t == null ? false : ((DapType) t.get()).isOpaqueType());
    }
}
