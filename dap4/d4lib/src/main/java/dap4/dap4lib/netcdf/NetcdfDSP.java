/*
TODO:
1. make sure all nodes areproperly annotated
*/

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
import dap4.core.util.DapSort;
import dap4.core.util.DapUtil;

import java.io.File;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static dap4.dap4shared.DapNetcdf.NC_NOWRITE;

/**
 * DSP for reading netcdf files through jni interface to netcdf4 library
 */
public class NetcdfDSP extends AbstractDSP
{
    //////////////////////////////////////////////////
    // Constants

    static public final boolean DEBUG = false;

    static protected String[] EXTENSIONS = new String[]{".nc",".hdf5"};

    // Define reserved attributes
    static public final String UCARTAGVLEN = "^edu.ucar.isvlen";
    static public final String UCARTAGOPAQUE = "^edu.ucar.opaque.size";
    static public final String UCARTAGUNLIM = "^edu.ucar.isunlim";

    // Annotation name for all nodes
    static public final int NC4DSPNODES = "NC4DSPNODES".hashCode();

    static protected final Pointer NC_NULL = Pointer.NULL;
    static protected final int NC_FALSE = 0;
    static protected final int NC_TRUE = 1;
    // "null" id
    static public final int NC_IDNULL = -1;
    static public final int NC_NOERR = 0;

    // Map NC_ type integers to typesort enums
    static protected final Map<Integer, TypeSort> typemap;

    static {
        typemap = new HashMap<Integer, TypeSort>();
        typemap.put(DapNetcdf.NC_BYTE, TypeSort.Int8);
        typemap.put(DapNetcdf.NC_CHAR, TypeSort.Char);
        typemap.put(DapNetcdf.NC_SHORT, TypeSort.Int16);
        typemap.put(DapNetcdf.NC_INT, TypeSort.Int32);
        typemap.put(DapNetcdf.NC_FLOAT, TypeSort.Float32);
        typemap.put(DapNetcdf.NC_DOUBLE, TypeSort.Float64);
        typemap.put(DapNetcdf.NC_UBYTE, TypeSort.UInt8);
        typemap.put(DapNetcdf.NC_USHORT, TypeSort.UInt16);
        typemap.put(DapNetcdf.NC_UINT, TypeSort.UInt32);
        typemap.put(DapNetcdf.NC_INT64, TypeSort.Int64);
        typemap.put(DapNetcdf.NC_UINT64, TypeSort.UInt64);
        typemap.put(DapNetcdf.NC_STRING, TypeSort.String);
    }

    static protected int NC_INT_BYTES = (java.lang.Integer.SIZE / java.lang.Byte.SIZE);
    static protected int NC_LONG_BYTES = (Native.LONG_SIZE);
    static protected int NC_POINTER_BYTES = (Native.POINTER_SIZE);
    static protected int NC_SIZET_BYTES = (Native.SIZE_T_SIZE);

    //////////////////////////////////////////////////
    // Types

    public static class Nc4ID
    {
        public int gid; // might also be root ncid
        public int id;  // dimension|type|variable id
        // Other kinds of nodes are not annotated

        public Nc4ID()
        {
            this(NC_IDNULL, NC_IDNULL);
        }

        public Nc4ID(int gid)
        {
            this(gid, NC_IDNULL);
        }

        public Nc4ID(int grpid, int id)
        {
            this.gid = grpid;
            this.id = id;
        }
    }

    //////////////////////////////////////////////////
    // Static variables

    static protected DapNetcdf nc4 = null;
    static public final String JNA_PATH = "jna.library.path";
    static public final String JNA_PATH_ENV = "JNA_PATH"; // environment var

    static protected String DEFAULTNETCDF4LIBNAME = "netcdf";

    static String[] DEFAULTNETCDF4PATH = new String[]{
            "/opt/netcdf4/lib",
            "/home/dmh/opt/netcdf4/lib", //temporary
            "c:/opt/netcdf", // Windows
            "/usr/jna_lib/",
    };

    static protected String jnaPath = null;
    static protected String libName = DEFAULTNETCDF4LIBNAME;

    static protected Boolean isClibraryPresent = null;

    /**
     * Use the default path to try to set jna.library.path
     *
     * @return true if we set jna.library.path
     */
    static protected String defaultNetcdf4Library()
    {
        StringBuilder pathlist = new StringBuilder();
        for(String path : DEFAULTNETCDF4PATH) {
            File f = new File(path);
            if(f.exists() && f.canRead()) {
                if(pathlist.length() > 0)
                    pathlist.append(File.pathSeparator);
                pathlist.append(path);
                break;
            }
        }
        return pathlist.length() == 0 ? null : pathlist.toString();
    }

    /**
     * set the path and name of the netcdf c library.
     * must be called before load() is called.
     *
     * @param jna_path path to shared libraries
     * @param lib_name library name
     */
    static public void setLibraryAndPath(String jna_path, String lib_name)
    {
        lib_name = nullify(lib_name);
        if(lib_name == null)
            lib_name = DEFAULTNETCDF4LIBNAME;
        jna_path = nullify(jna_path);
        if(jna_path == null) {
            jna_path = nullify(System.getProperty(JNA_PATH)); // First, try system property (-D flag).
        }
        if(jna_path == null) {
            jna_path = nullify(System.getenv(JNA_PATH_ENV)); // Next, try environment variable.
        }
        if(jna_path == null) {
            jna_path = defaultNetcdf4Library(); // Last, try some default paths.	}
            if(jna_path != null) {
                System.setProperty(JNA_PATH, jna_path);
            }
            libName = lib_name;
            jnaPath = jna_path;
        }
    }

    static protected DapNetcdf load()
    {
        if(nc4 == null) {
            if(jnaPath == null)
                setLibraryAndPath(null, null);
            try {
                // jna_path may still be null (the user didn't specify a "jna.library.path"), but try to load anyway;
                // the necessary libs may be on the system PATH.
                nc4 = (DapNetcdf) Native.loadLibrary(libName, DapNetcdf.class);
                String message = String.format("NetCDF-4 C library loaded (jna_path='%s', libname='%s').", jnaPath, libName);
                DapLog.info(message);
                if(DEBUG) {
                    System.out.println(message);
                    System.out.printf("Netcdf nc_inq_libvers='%s' isProtected=%s%n", nc4.nc_inq_libvers(), Native.isProtected());
                }
            } catch (Throwable t) {
                String message = String.format("NetCDF-4 C library not present (jna_path='%s', libname='%s'); %s.",
                        jnaPath, libName, t.getMessage());
                DapLog.warn(message);
                if(DEBUG) {
                    System.err.println(message);
                    System.err.println(t.getMessage());
                }
            }
        }
        return nc4;
    }

    /**
     * Test if the netcdf C library is present and loaded
     *
     * @return true if present
     */
    public static boolean isClibraryPresent()
    {
        if(isClibraryPresent == null)
            isClibraryPresent = load() != null;
        return isClibraryPresent;
    }

    /**
     * Convert a zero-length string to null
     *
     * @param s the string to check for length
     * @return null if s.length() == 0, s otherwise
     */
    static protected String nullify(String s)
    {
        if(s != null && s.length() == 0) s = null;
        return s;
    }

    //////////////////////////////////////////////////
    // Instance Variables

    protected boolean trace = false;
    protected boolean closed = false;
    protected int ncid = -1;        // file id
    protected int format = 0;       // from nc_inq_format
    protected int mode = 0;
    protected String path = null;

    // Map gid+typeid to type decl
    static long nctypeid(int gid, int typeid)
    {
        return (gid << 32) | typeid;
    }

    protected Map<Long, DapType> userTypes = new HashMap<>();  // hash by id

    // Map gid+dimid to dim decl
    static long ncdimid(int gid, int dimid)
    {
        return (gid << 32) | dimid;
    }

    protected Map<Long, DapDimension> alldims = new HashMap<>();  // hash by id

    protected Nc4Factory factory;
    protected DapContext cxt = null;
    protected DapDataset rootgroup = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public NetcdfDSP()
            throws DapException
    {
        this.factory = new Nc4Factory();
    }

    @Override
    public void close()
            throws DapException
    {
        if(closed) return;
        if(ncid < 0) return;
        int ret = nc4.nc_close(ncid);
        errcheck(ret);
        closed = true;
        if(trace)
            System.out.printf("Nc4DSP: closed: %s%n", path);
    }

    /**
     * A path is file if it has no base protocol or is file:
     *
     * @param path
     * @param context Any parameters that may help to decide.
     * @return true if this path appears to be processible by this DSP
     */
    static public boolean match(String path, DapContext context)
    {
        for(String s: EXTENSIONS) {
            if(path.endsWith(s)) return true;
        }
            return false;
    }

    @Override
    public DSP
    open(String path, DapContext cxt)
            throws DapException
    {
        if(!isClibraryPresent())
            throw new UnsupportedOperationException("Couldn't load NetCDF C library (see log for details).");
        int ret, mode;
        IntByReference ncidp = new IntByReference();
        try {
            mode = NC_NOWRITE;
            ret = nc4.nc_open(path, mode, ncidp);
            errcheck(ret);
            ncid = ncidp.getValue();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        // Figure out what kind of file
        IntByReference formatp = new IntByReference();
        ret = nc4.nc_inq_format(this.ncid, formatp);
        errcheck(ret);
        this.format = formatp.getValue();
        if(trace)
            System.out.printf("Nc4DSP: open: %s; ncid=%d; format=%d%n",
                    path, ncid, this.format);
        // create and fill the root group
        rootgroup = factory.newDataset(this.path, ncid);
        fillGroup(rootgroup);
        // save the set of all nodes
        rootgroup.annotate(NC4DSPNODES, factory.getAllNodes());
        return this;
    }

    @Override
    public DataDataset
    getDataDataset()
    {
        return null;
    }

    //////////////////////////////////////////////////

    protected int getGID(DapNode node)
    {
        return factory.getGID(node);
    }

    protected int getID(DapNode node)
    {
        return factory.getID(node);
    }

    //////////////////////////////////////////////////

    protected void
    makeGroup(DapGroup parent, int gid)
            throws DapException
    {
        int ret;
        byte[] namep = new byte[DapNetcdf.NC_MAX_NAME + 1];
        errcheck(ret = nc4.nc_inq_grpname(gid, namep));
        DapGroup g = factory.newGroup(makeString(namep), gid);
        factory.enterContainer(g);
        fillGroup(g);
        factory.leaveContainer();
    }

    protected void fillGroup(DapGroup parent)
            throws DapException
    {
        int[] dimids = getDimensions(parent);
        int[] udims = getUnlimitedDimensions(parent);
        for(int dimid : dimids) {
            makeDimension(parent, dimid, udims);
        }
        int[] typeids = getUserTypes(parent);
        for(int typeid : typeids) {
            makeUserType(parent, typeid);
        }
        int[] varids = getVars(parent);
        for(int varid : varids) {
            makeVar(parent, varid);
        }
        // fill in any attributes
        String[] gattnames = getAttributes(parent);
        addAttributes(parent, gattnames);
        if(format == DapNetcdf.NC_FORMAT_NETCDF4) {
            // read subordinate groups
            int[] groupids = getGroups(parent);
            for(int groupid : groupids) {
                makeGroup(parent, groupid);
            }
        }
    }

    protected int[]
    getGroups(DapGroup parent)
            throws DapException
    {
        int ret, n;
        int gid = getID(parent);
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_grps(gid, ip, NC_NULL));
        n = ip.getValue();
        int[] grpids = new int[n];
        errcheck(ret = nc4.nc_inq_grps(gid, ip, grpids));
        return grpids;
    }

    protected int[]
    getDimensions(DapGroup parent)
            throws DapException
    {
        int ret, n;
        int gid = getID(parent);
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_ndims(gid, ip));
        n = ip.getValue();
        Memory mem = new Memory(NC_INT_BYTES * n);
        errcheck(ret = nc4.nc_inq_dimids(gid, ip, mem, NC_FALSE));
        int[] dimids = mem.getIntArray(0, n);
        return dimids;
    }

    protected int[]
    getUnlimitedDimensions(DapGroup parent)
            throws DapException
    {
        int ret, n;
        int gid = getID(parent);
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_unlimdims(gid, ip, NC_NULL));
        n = ip.getValue();
        int[] dimids;
        if(n == 0)
            dimids = new int[0];
        else {
            Memory mem = new Memory(NC_INT_BYTES * n);
            errcheck(ret = nc4.nc_inq_unlimdims(gid, ip, mem));
            dimids = mem.getIntArray(0, n);
        }
        return dimids;
    }

    protected int[]
    getUserTypes(DapGroup parent)
            throws DapException
    {
        int ret, n;
        int gid = getID(parent);
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_typeids(gid, ip, NC_NULL));
        n = ip.getValue();
        Memory mem = new Memory(NC_INT_BYTES * n);
        errcheck(ret = nc4.nc_inq_typeids(gid, ip, mem));
        int[] typeids = mem.getIntArray(0, n);
        return typeids;
    }

    protected int[]
    getVars(DapGroup parent)
            throws DapException
    {
        int ret, n;
        int gid = getID(parent);
        IntByReference ip = new IntByReference();
        errcheck(ret = nc4.nc_inq_nvars(gid, ip));
        n = ip.getValue();
        Memory mem = new Memory(NC_INT_BYTES * n);
        errcheck(ret = nc4.nc_inq_varids(gid, ip, mem));
        int[] ids = mem.getIntArray(0, n);
        return ids;
    }

    protected String[]
    getAttributes(DapNode parent)
            throws DapException
    {
        int ret, n;
        int gid, varid;
        boolean isglobal = parent.getSort().isa(DapSort.GROUP);
        if(isglobal) {
            gid = getID(parent);
            varid = nc4.NC_GLOBAL;
        } else {
            gid = getGID(parent);
            varid = getID(parent);
        }
        IntByReference ip = new IntByReference();
        if(isglobal)
            errcheck(ret = nc4.nc_inq_natts(gid, ip));
        else
            errcheck(ret = nc4.nc_inq_var(gid, varid, NC_NULL, NC_NULL, NC_NULL, NC_NULL, ip));
        n = ip.getValue();
        String[] names = new String[n];
        byte[] namep = new byte[DapNetcdf.NC_MAX_NAME + 1];
        for(int i = 0; i < n; i++) {
            errcheck(ret = nc4.nc_inq_attname(gid, varid, i, namep));
            names[i] = makeString(namep);
        }
        return names;
    }

    protected void
    makeDimension(DapGroup parent, int did, int[] udims)
            throws DapException
    {
        int ret = NC_NOERR;
        int gid = getID(parent);
        byte[] name = new byte[DapNetcdf.NC_MAX_NAME + 1];
        SizeTByReference lenp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_dim(gid, did, name, lenp));
        String dname = makeString(name);
        DapDimension dim = factory.newDimension(dname, lenp.getValue().longValue(), did);
        // Mark if unlimited
        boolean isunlimited = false;
        for(int i = 0; i < udims.length; i++) {
            if(udims[i] == did) isunlimited = true;
            break;
        }
        DapAttribute ultag = factory.newAttribute(UCARTAGUNLIM, DapType.INT8);
        ultag.setValues(new Object[]{(Byte) (byte) 1});
        dim.addAttribute(ultag);
        if(trace)
            System.out.printf("Nc4DSP: dimension: %s size=%d%n", dname, dim.getSize());
    }

    protected void
    makeUserType(DapGroup parent, int tid)
            throws DapException
    {
        int ret = NC_NOERR;
        int gid = getID(parent);
        byte[] namep = new byte[DapNetcdf.NC_MAX_NAME + 1];
        SizeTByReference lenp = new SizeTByReference();
        IntByReference basetypep = new IntByReference();
        IntByReference classp = new IntByReference();
        SizeTByReference nfieldsp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_user_type(gid, tid, namep, lenp, basetypep, nfieldsp, classp));
        String name = makeString(namep);
        switch (classp.getValue()) {
        case DapNetcdf.NC_OPAQUE:
            // This is treated as atomic
            break;
        case DapNetcdf.NC_ENUM:
            makeEnumType(parent, tid, name, basetypep.getValue());
            break;
        case DapNetcdf.NC_COMPOUND:
            makeCompoundType(parent, tid, name, nfieldsp.getValue().longValue());
            break;
        case DapNetcdf.NC_VLEN:
            makeVlenType(parent, tid, name, basetypep.getValue());
            break;
        }
    }

    protected DapType
    findType(int pid, int nctype)
            throws DapException
    {
        if(nctype <= DapNetcdf.NC_MAX_ATOMIC_TYPE)
            return findAtomicType(nctype);
        DapType dt = userTypes.get(nctypeid(pid, nctype));
        return dt;
    }

    protected DapType
    findAtomicType(int nctype)
            throws DapException
    {
        TypeSort sort = typemap.get(nctype);
        if(sort == null)
            throw new DapException("Illegal Base type: " + nctype);
        DapType dtsort = DapType.lookup(sort);
        return dtsort;
    }

    protected void
    makeEnumType(DapGroup parent, int tid, String name, int basetype)
            throws DapException
    {
        int ret;
        int gid = getID(parent);
        SizeTByReference nmembersp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_enum(gid, tid, NC_NULL, NC_NULL, NC_NULL, nmembersp));
        DapType dtbase = findAtomicType(basetype);
        if(dtbase == null || !dtbase.isIntegerType())
            throw new DapException("Illegal Enumeration base type: " + basetype);
        DapEnumeration de = factory.newEnumeration(name, dtbase, tid);
        // Construct list of enum consts
        int nconsts = nmembersp.getValue().intValue();
        byte[] namep = new byte[DapNetcdf.NC_MAX_NAME + 1];
        IntByReference valuep = new IntByReference();
        for(int i = 0; i < nconsts; i++) {
            // Get info about the ith const
            errcheck(ret = nc4.nc_inq_enum_member(gid, tid, i, namep, valuep));
            de.addEnumConst(factory.newEnumConst(makeString(namep), (long) valuep.getValue()));
        }
    }

    protected void
    makeCompoundType(DapGroup parent, int tid, String sname, long nfields)
            throws DapException
    {
        int gid = getID(parent);
        DapStructure ds = factory.newStructure(sname, tid);
        byte[] name = new byte[DapNetcdf.NC_MAX_NAME + 1];
        SizeTByReference offsetp = new SizeTByReference();
        IntByReference fieldtypep = new IntByReference();
        IntByReference ndimsp = new IntByReference();
        for(int i = 0; i < nfields; i++) {
            int ret;
            // Get everything but actual dims
            errcheck(ret = nc4.nc_inq_compound_field(gid, tid, i, name,
                    offsetp, fieldtypep, ndimsp, NC_NULL));
            // Get dimsizes
            int ndims = ndimsp.getValue();
            // recall to get dimsizes
            Memory mem = new Memory(NC_POINTER_BYTES * ndims);
            errcheck(ret = nc4.nc_inq_compound_field(gid, tid, i, name,
                    offsetp, fieldtypep, ndimsp, mem));
            int[] dimsizes = mem.getIntArray(0, ndims);
            DapDimension[] dims = dimensionize(rootgroup, dimsizes);
            makeField(ds, i, makeString(name), fieldtypep.getValue(),
                    offsetp.getValue().intValue(), dims);
        }
    }

    protected DapDimension[]
    dimensionize(DapGroup root, int[] dimsizes)
    {
        DapDimension[] dims = new DapDimension[dimsizes.length];
        for(int i = 0; i < dimsizes.length; i++) {
            dims[i] = factory.newDimension(null, dimsizes[i], NC_IDNULL);
        }
        return dims;
    }

    protected void
    makeField(DapStructure ds, int index, String name, int basetype, int offset, DapDimension[] dims)
            throws DapException
    {
        int gid = getGID(ds);
        DapType dtbase = findType(gid, basetype);
        if(dtbase == null)
            throw new DapException("Undefined field base type: " + basetype);
        DapVariable field;
        switch (dtbase.getTypeSort()) {
        case Struct:
            field = factory.newStructure(name, index);
            break;
        case Seq:
            field = factory.newSequence(name, index);
            break;
        default:
            field = factory.newAtomicVariable(name, dtbase, index);
            break;
        }
        if(dims != null) {
            for(int i = 0; i < dims.length; i++) {
                field.addDimension(dims[i]);
            }
        }
    }

    protected void
    makeVar(DapGroup parent, int varid)
            throws DapException
    {
        int ret;
        int gid = getID(parent);
        byte[] namep = new byte[DapNetcdf.NC_MAX_NAME + 1];
        IntByReference basetypep = new IntByReference();
        IntByReference ndimsp = new IntByReference();
        IntByReference nattsp = new IntByReference();
        errcheck(ret = nc4.nc_inq_var(gid, varid, namep, basetypep, ndimsp, NC_NULL, nattsp));
        int ndims = ndimsp.getValue();
        // recall to get dimids
        Memory mem = new Memory(NC_POINTER_BYTES * ndims);
        errcheck(ret = nc4.nc_inq_var(gid, varid, namep, basetypep, ndimsp, mem, nattsp));
        int[] dimids = mem.getIntArray(0, ndims);
        DapType basetype = findType(gid, basetypep.getValue());
        DapVariable var = factory.newVariable(makeString(namep), basetype, varid);
        for(int i = 0; i < ndims; i++) {
            DapDimension dim = alldims.get(ncdimid(gid, dimids[i]));
            if(dim == null)
                throw new DapException("Undefined variable dimension id: " + dimids[i]);
            var.addDimension(dim);
        }
        // Now, if this is of type opaque, tag it with the size
        if(basetype.getTypeSort() == TypeSort.Opaque) {
            SizeTByReference sizep = new SizeTByReference();
            errcheck(ret = nc4.nc_inq_opaque(gid, basetypep.getValue(), namep, sizep));
            DapAttribute sizetag = factory.newAttribute(UCARTAGOPAQUE, DapType.INT64);
            sizetag.setValues(new Object[]{(Long) sizep.getValue().longValue()});
            var.addAttribute(sizetag);
        }
        // fill in any attributes
        String[] attnames = getAttributes(var);
        addAttributes(var, attnames);
    }

    protected void
    makeVlenType(DapGroup parent, int tid, String vname, int basetype)
            throws DapException
    {
        int ref;
        int gid = getID(parent);
        // We map vlen to a sequence with a single field
        // of the basetype. Field name is same as the vlen type
        DapSequence ds = factory.newSequence(vname, tid);
        makeField(ds, 0, vname, basetype, 0, null);
        // Annotate to indicate that this came from a vlen
        DapAttribute tag = factory.newAttribute(UCARTAGVLEN, DapType.INT8);
        tag.setValues(new Object[]{(Byte) (byte) 1});
        ds.addAttribute(tag);
    }

    protected void
    addAttributes(DapNode parent, String[] names)
            throws DapException
    {
        for(String name : names) {
            addAttribute(parent, name);
        }
    }

    protected void
    addAttribute(DapNode parent, String name)
            throws DapException
    {
        int ret;
        int gid, varid;
        boolean isglobal = parent.getSort().isa(DapSort.GROUP);
        if(isglobal) {
            gid = getID(parent);
            varid = nc4.NC_GLOBAL;
        } else {
            gid = getGID(parent);
            varid = getID(parent);
        }
        IntByReference basetypep = new IntByReference();
        errcheck(ret = nc4.nc_inq_atttype(gid, varid, name, basetypep));
        DapType basetype = findType(gid, basetypep.getValue());
        if(!basetype.getTypeSort().isAtomic())
            throw new DapException("Non-atomic attribute types not supported: " + name);
        DapAttribute da = factory.newAttribute(name, basetype);
        parent.addAttribute(da);
        SizeTByReference countp = new SizeTByReference();
        errcheck(ret = nc4.nc_inq_attlen(gid, varid, name, countp));
        // Get the values of the attribute
        Object[] values = getAttributeValues(da, countp.getValue().longValue(), gid, varid);
        da.setValues(values);
    }

    protected Object[]
    getAttributeValues(DapAttribute da, long lcount, int gid, int varid)
            throws DapException
    {
        int ret;
        int count = (int) lcount;
        DapType basetype = da.getBaseType();
        TypeSort typesort = basetype.getTypeSort();
        // Currently certain types only are allowed.
        if(!typesort.isAtomic() || typesort == TypeSort.Opaque)
            throw new DapException("Unsupported attribute type: " + typesort);
        Object valuelist = getRawAttributeValues(basetype, count, gid, varid, da.getShortName());
        Object[] values = new Object[count];
        values = convert(count, valuelist, basetype);
        return values;
    }

    protected Object
    getRawAttributeValues(DapType daptype, long lcount, int gid, int varid, String name)
            throws DapException
    {
        int ret = NC_NOERR;
        int count = (int) lcount;
        TypeSort sort = daptype.getTypeSort();
        int nativetypesize = daptype.getSize();
        if(sort.isStringType())
            nativetypesize = NC_POINTER_BYTES;
        else if(nativetypesize == 0)
            throw new DapException("Illegal Type Sort:" + sort);
        Memory mem = new Memory(nativetypesize * count);
        errcheck(ret = nc4.nc_get_att(gid, varid, name, mem));
        Object values = null;
        switch (sort) {
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
        case URL:
            values = mem.getStringArray(0, count);
            break;
        default:
            throw new IllegalArgumentException("Unexpected sort: " + sort);
        }
        return values;
    }

    protected Object[]
    convert(long lcount, Object src, DapType basetype)
            throws DapException
    {
        int count = (int) lcount;
        TypeSort sort = basetype.getTypeSort();
        boolean isenum = (sort == TypeSort.Enum);
        boolean ischar = (sort == TypeSort.Char);
        TypeSort truetype = basetype.getAtomicType();
        Object[] dst;
        if(ischar)
            dst = new Character[count];
        else
            dst = new Object[count];
        try {
            if(isenum) {
                long[] indices = enumIndices(src, count, truetype);
                for(int i = 0; i < dst.length; i++) {
                    dst[i] = indices[i];
                }
            } else {
                for(int i = 0; i < dst.length; i++) {
                    dst[i] = Array.get(src, i);
                }
            }
            return dst;
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new DapException(e);
        }
    }

    protected long[]
    enumIndices(Object src, int count, TypeSort truetype)
            throws DapException
    {
        long[] indices = new long[count];
        for(int i = 0; i < count; i++) {
            Object o = Array.get(src, i);
            switch (truetype) {
            case Int8:
                indices[i] = (Byte) o;
                break;
            case UInt8:
                indices[i] = (Byte) o;
                if(indices[i] < 0) indices[i] = -indices[i];
                break;
            case Int16:
                indices[i] = (Short) o;
                break;
            case UInt16:
                indices[i] = (Short) o;
                if(indices[i] < 0) indices[i] = -indices[i];
                break;
            case Int32:
                indices[i] = (Integer) o;
                break;
            case UInt32:
                indices[i] = (Integer) o;
                if(indices[i] < 0) indices[i] = -indices[i];
                break;
            case Int64:
                indices[i] = (Long) o;
                break;
            case UInt64:
                indices[i] = (Long) o;
                if(indices[i] < 0) indices[i] = -indices[i];
                break;
            default:
                throw new DapException("Illegal sort: " + truetype);
            }
        }
        return indices;
    }

    protected String makeString(byte[] b)
    {
        // null terminates
        int count = 0;
        while(count < b.length && b[count] != 0) {
            ;
        }
        return new String(b, 0, count, DapUtil.UTF8);
    }

    protected void
    errcheck(int ret)
            throws DapException
    {
        if(ret != 0)
            throw fail(ret);
    }

    protected DapException
    fail(int ret)
    {
        return new DapException(String.format("Nc4DSP: errno=%d; %s", ret, nc4.nc_strerror(ret)));
    }
}
