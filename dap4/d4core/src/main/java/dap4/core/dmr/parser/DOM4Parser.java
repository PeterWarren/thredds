/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.dmr.parser;

import dap4.core.dmr.*;
import dap4.core.util.DapException;
import dap4.core.util.DapSort;
import dap4.core.util.DapUtil;
import dap4.core.util.Escape;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.*;

/**
 * Implement the Dap4 Parser Using a DOM Parser
 */

public class DOM4Parser implements Dap4Parser
{

    //////////////////////////////////////////////////
    // Constants

    static final float DAPVERSION = 4.0f;
    static final float DMRVERSION = 1.0f;

    static final int RULENULL = 0;
    static final int RULEDIMREF = 1;
    static final int RULEMAPREF = 2;
    static final int RULEVAR = 3;
    static final int RULEMETADATA = 4;

    static final BigInteger BIG_INT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    static final DapSort[] METADATASCOPES = new DapSort[]{
            DapSort.DATASET,
            DapSort.GROUP,
            DapSort.DIMENSION,
            DapSort.MAP,
            DapSort.ATOMICVARIABLE,
            DapSort.STRUCTURE,
            DapSort.SEQUENCE,
            DapSort.ATTRIBUTESET
    };

    static final Map<String, DapSort> sortmap;

    static {
        sortmap = new HashMap<String, DapSort>();
        sortmap.put("attribute", DapSort.ATTRIBUTE);
        sortmap.put("dataset", DapSort.DATASET);
        sortmap.put("dim", DapSort.DIMENSION);
        sortmap.put("dimension", DapSort.DIMENSION);
        sortmap.put("enumeration", DapSort.ENUMERATION);
        sortmap.put("enumconst", DapSort.ENUMCONST);
        sortmap.put("group", DapSort.GROUP);
        sortmap.put("map", DapSort.MAP);
        sortmap.put("otherxml", DapSort.OTHERXML);
        sortmap.put("sequence", DapSort.SEQUENCE);
        sortmap.put("structure", DapSort.STRUCTURE);
        sortmap.put("char", DapSort.ATOMICVARIABLE);
        sortmap.put("int8", DapSort.ATOMICVARIABLE);
        sortmap.put("uint8", DapSort.ATOMICVARIABLE);
        sortmap.put("int16", DapSort.ATOMICVARIABLE);
        sortmap.put("uint16", DapSort.ATOMICVARIABLE);
        sortmap.put("int32", DapSort.ATOMICVARIABLE);
        sortmap.put("uint32", DapSort.ATOMICVARIABLE);
        sortmap.put("int64", DapSort.ATOMICVARIABLE);
        sortmap.put("uint64", DapSort.ATOMICVARIABLE);
        sortmap.put("float32", DapSort.ATOMICVARIABLE);
        sortmap.put("float64", DapSort.ATOMICVARIABLE);
        sortmap.put("string", DapSort.ATOMICVARIABLE);
        sortmap.put("url", DapSort.ATOMICVARIABLE);
        sortmap.put("opaque", DapSort.ATOMICVARIABLE);
        sortmap.put("enum", DapSort.ATOMICVARIABLE);
    }

    //////////////////////////////////////////////////
    // static variables

    static protected int globaldebuglevel = 0;

    //////////////////////////////////////////////////
    // Static methods

    static public void setGlobalDebugLevel(int level)
    {
        globaldebuglevel = level;
    }

    static DapSort
    nodesort(Node n)
    {
        String elem = n.getLocalName();
        DapSort sort = sortmap.get(elem.toLowerCase());
        return sort;
    }

    //////////////////////////////////////////////////
    // Instance variables

    protected DapFactory factory = null;
    protected ErrorResponse errorresponse = null;
    protected Deque<DapNode> scopestack = new ArrayDeque<DapNode>();
    protected DapDataset root = null; // of the parse
    protected boolean trace = false;
    public java.io.PrintStream debugstream = System.err;

    //////////////////////////////////////////////////
    // Constructors

    public DOM4Parser(DapFactory factory)
    {
        this.factory = factory; // see Dap4Actions
    }

    //////////////////////////////////////////////////
    // Accessors

    public void setDebugLevel(int level)
    {
        if(level > 0) {
            trace = true;
        }
    }

    public void setDebugStream(java.io.PrintStream stream)
    {
        if(stream != null)
            this.debugstream = stream;
    }

    public ErrorResponse getErrorResponse()
    {
        return errorresponse;
    }

    public DapDataset getDMR()
    {
        return this.root;
    }

    //////////////////////////////////////////////////
    // Parser API

    public boolean
    parse(String input)
            throws SAXException
    {
        try {
            DocumentBuilderFactory domfactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dombuilder = domfactory.newDocumentBuilder();
            StringReader rdr = new StringReader(input);
            InputSource src = new InputSource(rdr);
            Document doc = dombuilder.parse(src);
            doc.getDocumentElement().normalize();
            rdr.close();
            return true;
        } catch (ParserConfigurationException | IOException e) {
            throw new SAXException(e);
        }
    }

    //////////////////////////////////////////////////
    // Parser specific methods

    DapGroup
    getGroupScope()
            throws ParseException
    {
        DapGroup gscope = (DapGroup) searchScope(DapSort.GROUP, DapSort.DATASET);
        if(gscope == null) throw new ParseException("Undefined Group Scope");
        return gscope;
    }

    DapNode
    getMetadataScope()
            throws ParseException
    {
        // Search up the stack for first match.
        DapNode match = searchScope(METADATASCOPES);
        if(match == null)
            throw new ParseException("No enclosing metadata capable scope");
        return match;
    }

    DapNode
    getParentScope()
            throws DapException
    {
        DapNode parent = searchScope(DapSort.STRUCTURE, DapSort.SEQUENCE, DapSort.GROUP, DapSort.DATASET, DapSort.ENUMERATION);
        if(parent == null) throw new DapException("Undefined parent Scope");
        return parent;
    }

    DapVariable
    getVariableScope()
            throws DapException
    {
        DapNode match = searchScope(DapSort.ATOMICVARIABLE, DapSort.STRUCTURE, DapSort.SEQUENCE);
        if(match == null)
            throw new ParseException("No enclosing variable scope");
        return (DapVariable) match;
    }

    DapNode
    getScope(DapSort... sort)
            throws DapException
    {
        DapNode node = searchScope(sort);
        if(node == null) // return exception if not found
            throw new ParseException("No enclosing scope of specified type");
        return node;
    }

    DapNode
    searchScope(DapSort... sort)
    {
        Iterator it = scopestack.iterator();
        while(it.hasNext()) {
            DapNode node = (DapNode) it.next();
            for(int j = 0; j < sort.length; j++) {
                if(node.getSort() == sort[j])
                    return node;
            }
        }
        return null;
    }

    DapVariable
    findVariable(DapNode parent, String name)
    {
        DapVariable var = null;
        switch (parent.getSort()) {
        case DATASET:
        case GROUP:
            var = ((DapGroup) parent).findVariable(name);
            break;
        case STRUCTURE:
            var = (DapVariable) ((DapStructure) parent).findByName(name);
            break;
        case SEQUENCE:
            var = (DapVariable) ((DapSequence) parent).findByName(name);
            break;
        default:
            break;
        }
        return var;
    }

    // XML Attribute utilities
    protected String
    pull(Node n, String name)
    {
        NamedNodeMap map = n.getAttributes();
        Node attr = map.getNamedItem(name);
        if(attr == null)
            return null;
        return attr.getNodeValue();
    }

    //////////////////////////////////////////////////
    // Attribute construction

    DapAttribute
    makeAttribute(DapSort sort, String name, DapType basetype, List<String> nslist)
            throws ParseException
    {
        DapAttribute attr = factory.newAttribute(name, basetype);
        if(sort == DapSort.ATTRIBUTE) {
            attr.setBaseType(basetype);
        }
        attr.setNamespaceList(nslist);
        return attr;
    }

    boolean isempty(SaxEvent token)
    {
        return token == null || isempty(token.value);
    }

    boolean isempty(String text)
    {
        return (text == null || text.length() == 0);
    }

    boolean
    islegalenumtype(DapType kind)
    {
        return kind.isIntegerType();
    }

    boolean
    islegalattributetype(DapType kind)
    {
        return kind.isLegalAttrType();
    }

    /*
    protected void
    changeAttribute(DapAttribute attr, String description)
            throws DapException
    {
        SaxEvent name = pull(description, "name");
        if(isempty(name))
            throw new ParseException("Attribute: Empty attribute name");
        String attrname = name.value;
        if(!attr.getShortName().equals(attrname))
            throw new ParseException("Attribute: DATA DMR: Attribute name mismatch:" + name.name);
        switch (attr.getSort()) {
        case ATTRIBUTE:
            SaxEvent atype = pull(description, "type");
            SaxEvent value = pull(description, "value");
            String typename = (atype == null ? "Int32" : atype.value);
            if("Byte".equalsIgnoreCase(typename)) typename = "UInt8";
            DapType basetype = DapType.reify(typename);
            if(basetype != attr.getBaseType())
                throw new ParseException("Attribute: DATA DMR: Attempt to change attribute type: " + typename);
            attr.clearValues();
            if(value != null)
                attr.setValues(new Object[]{value.value});
            break;
        case ATTRIBUTESET:
            // clear the contained attributes
            attr.setAttributes(new HashMap<String, DapAttribute>());
            break;
        case OTHERXML:
            throw new ParseException("Attribute: DATA DMR: OtherXML attributes not supported");
        }
    }

    DapAttribute
    createatomicattribute(XMLAttributeMap attrs, NamespaceList nslist, DapNode parent)
            throws DapException
    {
        SaxEvent name = pull(attrs, "name");
        SaxEvent atype = pull(attrs, "type");
        if(false) { // if enable, then allow <Attribute type="..." value="..."/>
            SaxEvent value = pull(attrs, "value");
        }
        if(isempty(name))
            throw new ParseException("Attribute: Empty attribute name");
        String typename = (atype == null ? "Int32" : atype.value);
        if("Byte".equalsIgnoreCase(typename)) typename = "UInt8";
        DapType basetype = DapType.reify(typename);
        if(basetype == null || !islegalattributetype(basetype))
            throw new ParseException("Attribute: Invalid attribute type: " + typename);
        List<String> hreflist = convertNamespaceList(nslist);
        DapAttribute attr = makeAttribute(DapSort.ATTRIBUTE, name.value, basetype, hreflist, parent);
        return attr;
    }

    protected void
    createvalue(SaxEvent value, DapAttribute parent)
            throws DapException
    {
        List<String> textlist = null;
        if(value.eventtype == SaxEventType.CHARACTERS) {
            textlist = ParseUtil.collectValues(value.text);
        } else if(value.eventtype == SaxEventType.ATTRIBUTE) {
            textlist = new ArrayList<String>();
            textlist.add(value.value);
        }
        if(textlist != null)
            parent.setValues(textlist.toArray());
    }

    DapAttribute
    createotherxml(XMLAttributeMap attrs, DapNode parent)
            throws DapException
    {
        SaxEvent name = pull(attrs, "name");
        SaxEvent href = pull(attrs, "href");
        if(isempty(name))
            throw new ParseException("OtherXML: Empty name");
        List<String> nslist = new ArrayList<String>();
        if(!isempty(href))
            nslist.add(href.value);
        DapAttribute other
                = makeAttribute(DapSort.OTHERXML, name.value, null, nslist, parent);
        parent.setAttribute(other);
        return other;
    }
    */

    //////////////////////////////////////////////////
    // Recursive descent parser

    protected void
    parseresponse(Node root)
            throws ParseException
    {
        String elemname = root.getLocalName();
        if(elemname.equalsIgnoreCase("Error")) {
            parseerror(root);
        } else if(elemname.equalsIgnoreCase("Dataset")) {
            parsedataset(root);
        } else
            throw new ParseException("Unexpected response root: " + elemname);
    }


    protected void
    parsedataset(Node rootnode)
            throws ParseException
    {
        if(trace) trace("dataset.enter");
        String name = pull(rootnode, "name");
        String dapversion = pull(rootnode, "dapversion");
        String dmrversion = pull(rootnode, "dmrversion");
        if(isempty(name))
            throw new ParseException("Empty dataset name attribute");
        // convert and test version numbers
        float ndapversion = DAPVERSION;
        try {
            ndapversion = Float.parseFloat(dapversion);
        } catch (NumberFormatException nfe) {
            ndapversion = DAPVERSION;
        }
        if(ndapversion != DAPVERSION)
            throw new ParseException("Dataset dapVersion mismatch: " + dapversion);
        float ndmrversion = DAPVERSION;
        try {
            ndmrversion = Float.parseFloat(dmrversion);
        } catch (NumberFormatException nfe) {
            ndmrversion = DMRVERSION;
        }
        if(ndmrversion != DMRVERSION)
            throw new ParseException("Dataset dmrVersion mismatch: " + dmrversion);
        this.root = factory.newDataset(name);
        this.root.setDapVersion(Float.toString(ndapversion));
        this.root.setDMRVersion(Float.toString(ndmrversion));
        this.root.setDataset(this.root);
        scopestack.push(this.root);
        // recurse
        fillgroup(rootnode, this.root);
        if(trace) trace("dataset.exit");
        assert (scopestack.peek() != null && scopestack.peek().getSort() == DapSort.DATASET);
        this.root.sort();
        scopestack.pop();
        if(!scopestack.isEmpty())
            throw new ParseException("Dataset: nested dataset");
        this.root.finish();
    }

    protected DapGroup
    parsegroup(Node node)
            throws DapException
    {
        if(trace) trace("group.enter");
        String name = pull(node, "name");
        DapGroup g = factory.newGroup(name);
        scopestack.push(g);
        fillgroup(node, g);
        if(trace) trace("group.exit");
        scopestack.pop();
        return g;
    }

    protected void
    fillgroup(Node group, DapGroup g)
            throws ParseException
    {
        try {
            if(trace) trace("fillgroup.enter");
            NodeList nodes = group.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                DapSort sort = nodesort(n);
                switch (sort) {
                case ATOMICVARIABLE:
                    g.addDecl(parseatomicvar(n));
                    break;
                case ATTRIBUTESET:
                    g.addAttribute(parseattrset(n));
                    break;
                case ATTRIBUTE:
                    g.addAttribute(parseattr(n));
                    break;
                case OTHERXML:
                    g.addAttribute(parseotherxml(n));
                    break;
                case DIMENSION:
                    g.addDecl(parsedimdef(n));
                    break;
                case GROUP:
                    g.addDecl(parsegroup(n));
                    break;
                case ENUMERATION:
                    g.addDecl(parseenumdef(n));
                    break;
                case STRUCTURE:
                    g.addDecl(parsecontainervar(n, true));
                    break;
                case SEQUENCE:
                    g.addDecl(parsecontainervar(n, false));
                    break;
                default:
                    throw new ParseException("Unexpected element: " + n);
                }
            }
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected DapEnumeration
    parseenumdef(Node node)
            throws ParseException
    {
        try {
            if(trace) trace("enumdef.enter");
            String name = pull(node, "name");
            if(isempty(name))
                throw new ParseException("Enumdef: Empty Enum Declaration name");
            String typename = pull(node, "basetype");
            DapType basedaptype = null;
            if(typename == null) {
                basedaptype = DapEnumeration.DEFAULTBASETYPE;
            } else {
                if("Byte".equalsIgnoreCase(typename)) typename = "UInt8";
                basedaptype = DapType.reify(typename);
                if(basedaptype == null || !islegalenumtype(basedaptype))
                    throw new ParseException("Enumdef: Invalid Enum Declaration Type name: " + typename);
            }
            DapEnumeration dapenum = factory.newEnumeration(name, basedaptype);
            DapGroup parent = getGroupScope();
            parent.addDecl(dapenum);
            scopestack.push(dapenum);
            List<DapEnumConst> econsts = parseenumconsts(node);
            if(econsts.size() == 0)
                throw new ParseException("Enumdef: no enum constants specified");
            DapEnumeration eparent = (DapEnumeration) scopestack.pop();
            eparent.setEnumConsts(econsts);
            if(trace) trace("enumdef.exit");
            return dapenum;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected List<DapEnumConst>
    parseenumconsts(Node enumdef)
            throws ParseException
    {
        if(trace) trace("enumconsts.enter");
        List<DapEnumConst> econsts = new ArrayList<>();
        NodeList nodes = enumdef.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            DapEnumConst dec = parseenumconst(n);
            econsts.add(dec);
        }
        if(trace) trace("enumconsts.exit");
        return econsts;
    }

    protected DapEnumConst
    parseenumconst(Node node)
            throws ParseException
    {
        try {
            if(trace) trace("enumconst.enter");
            String name = pull(node, "name");
            String value = pull(node, "value");
            if(isempty(name))
                throw new ParseException("Enumconst: Empty enum constant name");
            if(isempty(value))
                throw new ParseException("Enumdef: Invalid enum constant value: " + value);
            long lvalue = 0;
            try {
                BigInteger bivalue = new BigInteger(value);
                bivalue = DapUtil.BIG_UMASK64.and(bivalue);
                lvalue = bivalue.longValue();
            } catch (NumberFormatException nfe) {
                throw new ParseException("Enumconst: illegal value: " + value);
            }
            DapEnumeration parent = (DapEnumeration) getScope(DapSort.ENUMERATION);
            // Verify that the name is a legal enum constant name, which is restricted
            // vis-a-vis other names
            if(!ParseUtil.isLegalEnumConstName(name))
                throw new ParseException("Enumconst: illegal enumeration constant name: " + name);
            DapEnumConst dec = new DapEnumConst(name, lvalue);
            return dec;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected DapDimension
    parsedimdef(Node node)
            throws ParseException
    {
        if(trace) trace("dimdef.enter");
        String name = pull(node, "name");
        String size = pull(node, "size");
        long lvalue = 0;
        if(isempty(name))
            throw new ParseException("Dimdef: Empty dimension declaration name");
        if(isempty(size))
            throw new ParseException("Dimdef: Empty dimension declaration size");
        try {
            lvalue = Long.parseLong(size);
            if(lvalue <= 0)
                throw new ParseException("Dimdef: value <= 0: " + lvalue);
        } catch (NumberFormatException nfe) {
            throw new ParseException("Dimdef: non-integer value: " + size);
        }
        DapDimension dim = factory.newDimension(name, lvalue);
        dim.setShared(true);
        if(trace) trace("dimdef.exit");
        return dim;
    }

    protected DapAtomicVariable
    parseatomicvar(Node node)
            throws ParseException
    {
        if(trace) trace("atomicvariable.enter");
        String name = pull(node, "name");
        if(isempty(name))
            throw new ParseException("Atomicvariable: Empty dimension reference name");
        String typename = node.getLocalName();
        if("Byte".equals(typename)) typename = "UInt8"; // special case
        DapSort sort = nodesort(node);
        if(sort != DapSort.ATOMICVARIABLE)
            throw new ParseException("Unexpected element: " + node);
        try {
            DapType basetype;
            if("enum".equalsIgnoreCase(typename)) {
                String enumfqn = pull(node, "enum");
                if(isempty(enumfqn))
                    throw new ParseException("Enumvariable: Empty enum type name");
                basetype = (DapEnumeration) root.findByFQN(enumfqn, DapSort.ENUMERATION);
                if(basetype == null)
                    throw new ParseException("EnumVariable: no such enum: " + name);
            } else
                basetype = DapType.reify(typename);
            if(basetype == null)
                throw new ParseException("AtomicVariable: Illegal type: " + typename);
            DapAtomicVariable var = factory.newAtomicVariable(name, basetype);
            // Look at the parent scope
            DapNode parent = getParentScope();
            if(parent == null)
                throw new ParseException("Variable has no parent");
            switch (parent.getSort()) {
            case DATASET:
            case GROUP:
                ((DapGroup) parent).addDecl(var);
                break;
            case STRUCTURE:
            case SEQUENCE:
                ((DapStructure) parent).addField(var);
                break;
            default:
                throw new IllegalStateException("Atomic variable in illegal scope");
            }
            scopestack.push(var);
            fillvar(node, var, false);
            scopestack.pop();
            return var;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected void
    fillvar(Node node, DapVariable var, boolean iscontainer)
            throws ParseException
    {
        try {
            if(trace) trace("fillvar.enter");
            NodeList nodes = node.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                DapSort sort = nodesort(n);
                if(!iscontainer && sort.oneof(DapSort.ATOMICVARIABLE, DapSort.STRUCTURE, DapSort.SEQUENCE))
                    throw new ParseException("Unexpected node type in var decl: " + sort);
                switch (sort) {
                case ATTRIBUTESET:
                    var.addAttribute(parseattrset(n));
                    break;
                case ATTRIBUTE:
                    var.addAttribute(parseattr(n));
                    break;
                case OTHERXML:
                    var.addAttribute(parseotherxml(n));
                    break;
                case DIMENSION: // really dimref
                    var.addDimension(parsedimref(n, var));
                    break;
                case MAP:
                    var.addMap(parsemap(n, var));
                    break;
                case ATOMICVARIABLE:
                    ((DapStructure) var).addField(parseatomicvar(n));
                    break;
                case STRUCTURE:
                    ((DapStructure) var).addField(parsecontainervar(n, true));
                    break;
                case SEQUENCE:
                    ((DapStructure) var).addField(parsecontainervar(n, false));
                    break;
                default:
                    throw new ParseException("Fillvar: Unexpected element: " + n);
                }
            }
            if(trace) trace("fillvar.exit");
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected DapDimension
    parsedimref(Node node, DapVariable var)
            throws ParseException
    {
        try {
            if(trace) trace("dimref.enter");
            String dimname = pull(node, "name");
            String size = pull(node, "size");
            DapDimension dim;
            if(dimname != null && size != null)
                throw new ParseException("Dimref: both name and size specified");
            if(dimname == null && size == null)
                throw new ParseException("Dimref: no name or size specified");
            if(dimname != null && isempty(dimname))
                throw new ParseException("Dimref: Empty dimension reference name");
            else if(size != null && isempty(size))
                throw new ParseException("Dimref: Empty dimension size");
            if(dimname != null) {
                DapGroup dg = var.getGroup();
                if(dg == null)
                    throw new ParseException("Internal error: variable has no containing group");
                dim = (DapDimension) dg.findByFQN(dimname, DapSort.DIMENSION);
            } else {// size != null; presume a number; create unique anonymous dimension
                size = size.trim();
                // Note that we create it in the root group
                assert (root != null);
                long anonsize;
                try {
                    anonsize = Long.parseLong(size.trim());
                } catch (NumberFormatException nfe) {
                    throw new ParseException("Dimref: Illegal dimension size");
                }
                dim = root.createAnonymous(anonsize);
            }
            if(dim == null)
                throw new ParseException("Unknown dimension: " + dimname);
            return dim;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected DapMap
    parsemap(Node node, DapVariable var)
            throws ParseException
    {
        if(trace) trace("map.enter");
        String name = pull(node, "name");
        if(isempty(name))
            throw new ParseException("Mapref: Empty map name");
        DapVariable target;
        try {
            target = (DapVariable) root.findByFQN(name, DapSort.ATOMICVARIABLE, DapSort.SEQUENCE, DapSort.STRUCTURE);
        } catch (DapException de) {
            throw new ParseException(de);
        }
        if(target == null)
            throw new ParseException("Mapref: undefined target variable: " + name);
        // Verify that this is a legal map =>
        // 1. it is outside the scope of its parent if the parent
        //    is a structure.
        DapNode container = target.getContainer();
        DapNode scope;
        try {
            scope = getParentScope();
        } catch (DapException de) {
            throw new ParseException(de);
        }
        if((container.getSort() == DapSort.STRUCTURE || container.getSort() == DapSort.SEQUENCE)
                && container == scope)
            throw new ParseException("Mapref: map target variable not in outer scope: " + name);
        DapMap map = factory.newMap(target);
        if(trace) trace("map.exit");
        return map;
    }

    protected DapStructure
    parsecontainervar(Node node, boolean isstruct)
            throws ParseException
    {
        try {
            if(trace) trace((isstruct ? "structurevar" : "sequencevar") + ".enter");
            String name = pull(node, "name");
            if(isempty(name))
                throw new ParseException("Empty container name");
            DapStructure var = (isstruct ? factory.newStructure(name) : factory.newSequence(name));
            // Look at the parent scope
            DapNode parent = scopestack.peek();
            if(parent == null)
                throw new ParseException("Variable has no parent: " + name);
            switch (parent.getSort()) {
            case DATASET:
            case GROUP:
                ((DapGroup) parent).addDecl(var);
                break;
            case STRUCTURE:
            case SEQUENCE:
                ((DapStructure) parent).addField(var);
                break;
            default:
                assert false : "Structure variable in illegal scope";
            }
            scopestack.push(var);
            fillvar(node, var, true);
            if(trace) trace((isstruct ? "structurevar" : "sequencevar") + ".exit");
            return var;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected DapAttribute
    parseattr(Node node)
            throws ParseException
    {
        if(trace) trace("attribute.enter");
        String name = pull(node, "name");
        if(isempty(name))
            throw new ParseException("Attribute: Empty attribute name");
        List<String> nslist = parsenamespaces(node);
        DapAttribute attr = makeAttribute(DapSort.ATTRIBUTESET, name, null, nslist);
        scopestack.push(attr);
        List<String> values = new ArrayList<String>();
        String val = pull(node, "value");
        if(val == null) {
            values.add(val);
        } else {
            if(node.hasChildNodes()) {
                NodeList nodes = node.getChildNodes();
                for(int i = 0; i < nodes.getLength(); i++) {
                    Node n = nodes.item(i);
                    String kind = n.getLocalName();
                    if(kind.equalsIgnoreCase("Value")) {
                        val = pull(n, "value");
                        values.add(val);
                    } else
                        throw new ParseException("Unexpected non-value element in attribute");
                }
            } else {
                values.add(node.getTextContent());
            }
        }
        if(values.size() == 0)
            throw new ParseException("Attribute: attribute has no values");
        scopestack.pop();
        if(trace) trace("attribute.exit");
        return attr;
    }

    protected DapAttributeSet
    parseattrset(Node node)
            throws ParseException
    {
        try {
            if(trace) trace("attrset.enter");
            String name = pull(node, "name");
            if(isempty(name))
                throw new ParseException("AttributeSet: Empty attribute name");
            List<String> nslist = parsenamespaces(node);
            DapAttributeSet attrset = factory.newAttributeSet(name);
            scopestack.push(attrset);
            NodeList nodes = node.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                DapSort sort = nodesort(n);
                switch (sort) {
                case ATTRIBUTE:
                    DapAttribute attr = parseattr(n);
                    attrset.addAttribute(attr);
                    break;
                case ATTRIBUTESET:
                    DapAttributeSet aset = parseattrset(n);
                    attrset.addAttribute(aset);
                    break;
                default:
                    throw new ParseException("Unexpected attribute set element: " + n);
                }
            }
            scopestack.pop();
            if(trace) trace("attributeset.exit");
            return attrset;
        } catch (DapException e) {
            throw new ParseException(e);
        }
    }

    protected List<String>
    parsenamespaces(Node node)
            throws ParseException
    {
        List<String> nslist = new ArrayList<>();
        NodeList nodes = node.getChildNodes();
        for(int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if("namespace".equalsIgnoreCase(n.getLocalName())) {
                String ns = pull(n, "href");
                if(isempty(ns))
                    throw new ParseException("Illegal null namespace href: " + node);
                if(!nslist.contains(ns)) nslist.add(ns);
            }
        }
        return nslist;
    }

    protected DapAttribute
    parseotherxml(Node node)
            throws ParseException
    {
        if(trace) trace("otherxml.enter");
        String name = pull(node, "name");
        DapNode parent = getMetadataScope();
        DapOtherXML other = factory.newOtherXML(name);
        // Get the child node(s)
        NodeList nodes = node.getChildNodes();
        switch (nodes.getLength()) {
        case 0:
            break;
        case 1:
            other.setRoot(nodes.item(0));
            break;
        default:
            throw new ParseException("OtherXML: multiple top level nodes not supported");
        }
        if(trace) trace("otherxml.exit");
        return other;
    }

    protected void
    parseerror(Node node)
            throws ParseException
    {
        if(trace) trace("error.enter");
        String xhttpcode = pull(node, "httpcode");
        String shttpcode = (xhttpcode == null ? "400" : xhttpcode);
        int httpcode = 0;
        try {
            httpcode = Integer.parseInt(shttpcode);
        } catch (NumberFormatException nfe) {
            throw new ParseException("Error Response; illegal http code: " + shttpcode);
        }
        this.errorresponse = new ErrorResponse();
        this.errorresponse.setCode(httpcode);
        if(trace) trace("error.exit");
    }

    protected void
    errormessage(String value)
            throws ParseException
    {
        if(trace) trace("errormessage.enter");
        assert (this.errorresponse != null) : "Internal Error";
        String message = value;
        message = Escape.entityUnescape(message); // Remove XML encodings
        this.errorresponse.setMessage(message);
        if(trace) trace("errormessage.exit");
    }

    protected void
    errorcontext(String value)
            throws ParseException
    {
        if(trace) trace("errorcontext.enter");
        assert (this.errorresponse != null) : "Internal Error";
        String context = value;
        context = Escape.entityUnescape(context); // Remove XML encodings
        this.errorresponse.setContext(context);
        if(trace) trace("errorcontext.exit");
    }

    protected void
    errorotherinfo(String value)
            throws ParseException
    {
        if(trace) trace("errorotherinfo.enter");
        assert (this.errorresponse != null) : "Internal Error";
        String other = value;
        other = Escape.entityUnescape(other); // Remove XML encodings
        this.errorresponse.setOtherInfo(other);
        if(trace) trace("errorotherinfo.exit");
    }

    //////////////////////////////////////////////////
    // Utilities

    protected void trace(String action)
    {
        if(!trace) return;
        debugstream.println("ACTION: " + action);
        debugstream.flush();
    }
}
