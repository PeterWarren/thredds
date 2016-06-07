/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.netcdf;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static dap4.dap4lib.netcdf.NetcdfDSP.*;

public class Nc4Factory extends DefaultDMRFactory
{

    // Nc4DSP Uses two annotations keys: one to track ids
    // and one to track all nodes in tree by FQN
    static public final int NC4DSPKEY = "NC4DSPKEY".hashCode();

    //////////////////////////////////////////////////
    // Type Decls

    static protected class ID
    {
        public int gid; // might also be root ncid
        public int id;  // dimension|type|variable id

        // Other kinds of nodes are not annotated
        public ID()
        {
            this(NC_GRPNULL, NC_IDNULL);
        }

        public ID(int gid)
        {
            this(gid, NC_IDNULL);
        }

        public ID(int grpid, int id)
        {
            this.gid = grpid;
            this.id = id;
        }
    }

    //////////////////////////////////////////////////

    protected Stack<DapNode> scope = new Stack<>();

    protected DapNode top()
    {
        return scope.empty() ? null : scope.peek();
    }

    // Collect all created nodes
    protected List<DapNode> allnodes = new ArrayList<>();

    //////////////////////////////////////////////////
    // Constructor
    public Nc4Factory()
    {
        super();
    }

    //////////////////////////////////////////////////
    // Accessors

    public List<DapNode>
    getAllNodes()
    {
        List<DapNode> nodes = this.allnodes;
        this.allnodes = null;
        return nodes;
    }

    void enterContainer(DapNode c)
    {
        scope.push(c);
    }

    void leaveContainer()
    {
        scope.pop();
    }

    //////////////////////////////////////////////////
    // Annotation management

    protected DapNode
    tag(DapNode container, DapNode annotatednode, int id)
    {
        return tag(container, annotatednode, id, NetcdfDSP.NC_GRPNULL);
    }

    protected DapNode
    tag(DapNode container, DapNode annotatednode, int id, int gid)
    {
        DapNode n = tag(annotatednode, id, gid);
        if(container != null) try {
            switch (container.getSort()) {
            case GROUP:
                ((DapGroup) container).addDecl(n);
                break;
            case STRUCTURE:
            case SEQUENCE:
                ((DapStructure) container).addField(n);
                break;
            default:
                throw new DapException("Nc4Factory: unexpected container: " + container);
            }
        } catch (DapException e) {
            throw new IllegalStateException(e);
        }
        return n;
    }

    protected DapNode
    tag(DapNode annotatednode, int id)
    {
        return tag(annotatednode, id, NetcdfDSP.NC_GRPNULL);
    }

    protected DapNode
    tag(DapNode annotatednode, int id, int gid)
    {
        DapNode parent = top();
        ID nid = new ID(gid, id);
        annotatednode.annotate(NC4DSPKEY, nid);
        allnodes.add(annotatednode);
        return annotatednode;
    }

    /**
     * Return gid of containing group
     */
    public int
    getGID(DapNode node)
    {
        ID id = (ID) node.annotation(NC4DSPKEY);
        return id.gid;
    }

    /**
     * Return id of object
     */
    public int
    getID(DapNode node)
    {
        ID id = (ID) node.annotation(NC4DSPKEY);
        return id.id;
    }

    //////////////////////////////////////////////////
    // DapFactory Extended API

    public DapAttribute newAttribute(String name, DapType basetype)
    {
        return (DapAttribute) tag(super.newAttribute(name, basetype), -1);
    }

    public DapAttributeSet newAttributeSet(String name)
    {
        return (DapAttributeSet) tag(super.newAttributeSet(name), -1);
    }

    public DapOtherXML newOtherXML(String name)
    {
        return (DapOtherXML) tag(super.newOtherXML(name), -1);
    }

    //////////////////////////////////////////////////
    // "Top Level"  nodes

    public DapDimension newDimension(String name, long size, int id)
    {
        return (DapDimension) tag(top(), super.newDimension(name, size), id);
    }

    public DapMap newMap(DapVariable target, int id)
    {
        return (DapMap) tag(top(), super.newMap(target), id);
    }

    public DapVariable newAtomicVariable(String name, DapType t, int id)
    {
        return (DapVariable) tag(scope.peek(), super.newAtomicVariable(name, t), id);
    }

    public DapVariable newVariable(String name, DapType t, int gid, int id)
    {
        return (DapVariable) tag(top(), super.newVariable(name, t), id, gid);
    }

    public DapGroup newGroup(String name, int id)
    {
        return (DapGroup) tag(top(), super.newGroup(name), id);
    }

    public DapDataset newDataset(String name, int id)
    {
        return (DapDataset) tag(top(), super.newDataset(name), id);
    }

    public DapEnumeration newEnumeration(String name, DapType basetype, int id)
    {
        return (DapEnumeration) tag(top(), super.newEnumeration(name, basetype), id);
    }

    public DapEnumConst newEnumConst(String name, long value, int id)
    {
        return (DapEnumConst) tag(top(), super.newEnumConst(name, value), id);
    }

    public DapStructure newStructure(String name, int id)
    {
        return (DapStructure) tag(top(), super.newStructure(name), id);
    }

    public DapSequence newSequence(String name, int id)
    {
        return (DapSequence) tag(top(), super.newSequence(name), id);
    }
}

