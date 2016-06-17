/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.netcdf;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static dap4.dap4lib.netcdf.Nc4DMR.*;

public class Nc4DMRFactory implements DMRFactory
{
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
    public Nc4DMRFactory()
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
    tag(DapNode container, DapNode node, Notes info)
    {
        DapNode n = tag(node, info);
        if(container != null) try {
            switch (container.getSort()) {
            case DATASET:
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
    tag(DapNode node, Notes info)
    {
        node.annotate(info);
        allnodes.add(node);
        return node;
    }

    //////////////////////////////////////////////////
    // DMRFactory Extended API

    public DapAttribute newAttribute(String name, DapType basetype)
    {
        return (DapAttribute) tag(new Nc4Attribute(name, basetype), null);
    }

    public DapAttributeSet newAttributeSet(String name)
    {
        return (DapAttributeSet) tag(new Nc4DMR.Nc4AttributeSet(name), null);
    }

    public DapOtherXML newOtherXML(String name)
    {
        return (DapOtherXML) tag(new Nc4OtherXML(name), null);
    }

    //////////////////////////////////////////////////
    // "Top Level"  nodes

    public DapDimension newDimension(String name, long size, Notes info)
    {
        return (DapDimension) tag(top(), new Nc4Dimension(name, size), info);
    }

    public DapMap newMap(DapVariable target, Notes info)
    {
        return (DapMap) tag(top(), new Nc4Map(target), info);
    }

    public DapVariable newAtomicVariable(String name, DapType t, Notes info)
    {
        return (DapVariable) tag(scope.peek(), new Nc4AtomicVariable(name, t), info);
    }

    public DapGroup newGroup(String name, Notes info)
    {
        return (DapGroup) tag(top(), new Nc4Group(name), info);
    }

    public DapDataset newDataset(String name, Notes info)
            throws DapException
    {
        return (DapDataset) tag(top(), new Nc4Dataset(name), info);
    }

    public DapEnumeration newEnumeration(String name, DapType basetype, Notes info)
    {
        return (DapEnumeration) tag(top(), new Nc4Enumeration(name, basetype), info);
    }

    public DapEnumConst newEnumConst(String name, long value, Notes info)
    {
        return (DapEnumConst) tag(top(), new Nc4EnumConst(name, value), info);
    }

    public DapStructure newStructure(String name, Notes info)
    {
        return (DapStructure) tag(top(), new Nc4Structure(name), info);
    }

    public DapSequence newSequence(String name, Notes info)
    {
        return (DapSequence) tag(top(), new Nc4Sequence(name), info);
    }

}

