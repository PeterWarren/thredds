/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/
package dap4.dap4lib;

import dap4.core.ce.CEConstraint;
import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.*;

import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * DAP Printer.
 * Given a constraint and a Dataset
 * print the constrained subset of the Dataset in text form.
 */

public class DAPPrint
{
    //////////////////////////////////////////////////
    // Constants

    static protected final int COLUMNS = 8;

    static protected final char LPAREN = '(';
    static protected final char RPAREN = ')';
    static protected final char LBRACE = '{';
    static protected final char RBRACE = '}';
    static protected final char LBRACKET = '[';
    static protected final char RBRACKET = ']';

    // Could use enumset, but it is so ugly,
    // so use good old OR'able flags
    static protected final int NILFLAGS = 0;
    static protected final int PERLINE = 1; // print xml attributes 1 per line
    static protected final int NONAME = 2; // do not print name xml attribute
    static protected final int NONNIL = 4; // print empty xml attributes

    //////////////////////////////////////////////////
    // Type declarations

    static class CommandlineOptions
    {
        // Local copies of the command line options
        public String path = null;
        public String outputfile = null;
    }

    //////////////////////////////////////////////////
    // Instance variables

    protected PrintWriter writer = null;
    protected IndentWriter printer = null;

    protected DSP dsp = null;
    protected DapDataset dmr = null;
    protected DataDataset data = null;

    //////////////////////////////////////////////////
    //Constructor(s)

    protected DAPPrint()
    {
    }

    public DAPPrint(Writer writer)
    {
        this.writer = new PrintWriter(writer);
        this.printer = new IndentWriter(this.writer);
    }

    //////////////////////////////////////////////////
    // API 

    public void flush()
    {
        this.printer.flush();
    }

    public void close()
    {
        this.flush();
    }

    //////////////////////////////////////////////////
    // External API

    /**
     * Print a DataDataset:
     * - optionally constrained
     *
     * @param dataset
     * @throws DapException
     */

    public void
    print(DataDataset dataset)
            throws DapException
    {
        print(dataset,null);
    }

    /**
     * Print a DataDataset:
     * - optionally constrained
     *
     * @param dataset
     * @param ce constraint on what to print
     * @throws DapException
     */

    public void
    print(DataDataset dataset, CEConstraint ce)
            throws DapException
    {
        if(ce == null)
            ce = CEConstraint.getUniversal((DapDataset) dataset.getTemplate());
        printDAP(ce, dataset);
    }


    public void
    printDAP(CEConstraint ce, DataDataset dataset)
            throws DapException
    {
        this.printer.setIndent(0);
        List<DataVariable> topvars = dataset.getTopVariables();
        for(int i = 0; i < topvars.size(); i++) {
            DataVariable top = topvars.get(i);
            if(ce.references(top.getTemplate())) {
                printVariable(top, ce);
            }
        }
        printer.eol();
    }

    //////////////////////////////////////////////////

    /**
     * Print an arbitrary DataVariable  using constraint.
     * <p>
     * Handling newlines is a bit tricky
     * so the rule is that the
     * last newline is elided and left
     * for the caller to print.
     * Exceptions: ?
     *
     * @param datav - the node to print
     * @param ce    - the constraint
     * @throws DapException Note that the PrintWriter is global.
     */

    protected void
    printVariable(DataVariable datav, CEConstraint ce)
            throws DapException
    {
        DapVariable dapv = datav.getVariable();
        Odometer odom;
        if(dapv.getRank() == 0)
            odom = Odometer.factoryScalar();
        else {
            List<Slice> slices = ce.getConstrainedSlices(dapv);
            odom = Odometer.factory(slices, false);
        }
        String name = dapv.getFQN();
        DapSort sort = dapv.getSort();
        while(odom.hasNext()) {
            Index pos = odom.next();
            if(odom.rank() == 0)
                printer.marginPrint(name + " = ");
            else {
                String s = indicesToString(pos);
                printer.marginPrint(name + s + " = ");
            }
            printLeaf(datav, pos, sort, ce);
        }
    }

    protected void
    printLeaf(DataVariable datav, Index pos, CEConstraint ce)
            throws DapException
    {
        DataSort sort = datav.getSort();
        switch (sort) {
        case ATOMIC:
            DataAtomic atom = (DataAtomic) datav;
            DapAtomicVariable av = (DapAtomicVariable) atom.getTemplate();
            Object value = atom.read(pos);
            try {
                printer.print(valueString(value, av.getBaseType()));
            } catch (DapException ioe) {
                throw new DataException(ioe);
            }
            printer.eol();
            break;

        case COMPOUNDARRAY:
            break;

        case STRUCTURE:// Might be singleton Structure or DataCompoundArray
            DataStructure stdata = (DataStructure) datav;
            DapStructure dstruct = (DapStructure) datav.getTemplate();
            List<DapVariable> dfields = dstruct.getFields();
            printer.indent();
            for(int f = 0; f < dfields.size(); f++) {
                DataVariable df = stdata.getfield(f);
                printVariable(df, ce);
            }
            printer.outdent();
            break;

        case SEQUENCE:
            DataSequence sqdata = (DataSequence) datav;
            DapSequence dseq = (DapSequence) datav.getTemplate();
            printer.indent();
            dfields = dseq.getFields();
            long count = sqdata.getRecordCount();
            for(long r = 0; r < count; r++) {
                DataRecord dr = sqdata.getRecord(r);
                printRecord(dr, ce);
            }
            printer.outdent();
            break;

        case RECORD:

        default:
            throw new DataException("Attempt to treat non-variable as variable:" + datav.getTemplate().getFQN());
        }
    }

    protected void
    printRecord(DataRecord dr, CEConstraint ce)
            throws DapException
    {
        DapSequence dseq = (DapSequence) dr.getTemplate();
        List<DapVariable> dfields = dseq.getFields();
        printer.indent();
        for(int f = 0; f < dfields.size(); f++) {
            DataVariable dv = dr.getfield(f);
            printVariable(dv, ce);
        }
        printer.outdent();
    }

    ;

    protected String
    indicesToString(Index indices)
            throws DapException
    {
        StringBuilder buf = new StringBuilder();
        if(indices != null && indices.getRank() > 0) {
            for(int i = 0; i < indices.getRank(); i++) {
                buf.append(i == 0 ? LBRACKET : ",");
                buf.append(String.format("%d", indices.get(i)));
            }
            buf.append(RBRACKET);
        }
        return buf.toString();
    }

    protected String
    valueString(Object value, DapType basetype)
            throws DataException
    {
        if(value == null) return "null";
        TypeSort atype = basetype.getTypeSort();
        boolean unsigned = atype.isUnsigned();
        switch (atype) {
        case Int8:
        case UInt8:
            long lvalue = ((Byte) value).longValue();
            if(unsigned) lvalue &= 0xFFL;
            return String.format("%d", lvalue);
        case Int16:
        case UInt16:
            lvalue = ((Short) value).longValue();
            if(unsigned) lvalue &= 0xFFFFL;
            return String.format("%d", lvalue);
        case Int32:
        case UInt32:
            lvalue = ((Integer) value).longValue();
            if(unsigned) lvalue &= 0xFFFFFFFFL;
            return String.format("%d", lvalue);
        case Int64:
        case UInt64:
            lvalue = ((Long) value).longValue();
            if(unsigned) {
                BigInteger b = BigInteger.valueOf(lvalue);
                b = b.and(DapUtil.BIG_UMASK64);
                return b.toString();
            } else
                return String.format("%d", lvalue);
        case Float32:
            return String.format("%f", ((Float) value));
        case Float64:
            return String.format("%f", ((Double) value));
        case Char:
            return "'" + ((Character) value).toString() + "'";
        case String:
        case URL:
            return "\"" + ((String) value) + "\"";
        case Opaque:
            ByteBuffer opaque = (ByteBuffer) value;
            StringBuilder s = new StringBuilder();
            s.append("0x");
            for(int i = 0; i < opaque.limit(); i++) {
                byte b = opaque.get(i);
                char c = hexchar((b >> 4) & 0xF);
                s.append(c);
                c = hexchar((b) & 0xF);
                s.append(c);
            }
            return s.toString();
        case Enum:
            return valueString(value, ((DapEnumeration) basetype).getBaseType());
        default:
            break;
        }
        throw new DataException("Unknown type: " + basetype);
    }


    //////////////////////////////////////////////////
    // Misc. Utilities

    static char
    hexchar(int i)
    {
        return "0123456789ABCDEF".charAt((i & 0xF));
    }


    static protected String
    getPrintValue(Object value)
    {
        if(value instanceof String) {
            return Escape.entityEscape((String) value);
        } else
            return value.toString();
    }

}

