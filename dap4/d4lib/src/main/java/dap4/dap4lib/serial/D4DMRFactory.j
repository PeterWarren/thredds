/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.dap4lib.serial;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static dap4.dap4lib.serial.D4DMR.*;

public class D4DMRFactory implements DMRFactory
{
    //////////////////////////////////////////////////

    protected Stack<DapNode> scope = new Stack<>();

    protected DapNode top()
    {
        return (this.scope.isEmpty() ? null : this.scope.peek());
    }

    // Collect all created nodes
    protected List<DapNode> allnodes = new ArrayList<>();

    //////////////////////////////////////////////////
    // Constructor
    public D4DMRFactory()
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
    tag(DapNode node, Object notes)
    {
        allnodes.add(node);
        node.annotate(notes);
        DapNode container = top();
        if(container != null) try {
            switch (container.getSort()) {
            case DATASET:
            case GROUP:
                ((DapGroup) container).addDecl(node);
                break;
            case STRUCTURE:
            case SEQUENCE:
                ((DapStructure) container).addField(node);
                break;
            case ENUMERATION:
                ((DapEnumeration) container).addEnumConst((DapEnumConst) node);
                break;
            default:
                throw new DapException("D4Factory: unexpected container: " + container);
            }
        } catch (DapException e) {
            throw new IllegalStateException(e);
        }
        return node;
    }

    //////////////////////////////////////////////////
    // DMRFactory Extended API

    public DapAttribute newAttribute(String name, DapType basetype, Object notes)
    {
        return (DapAttribute) tag(new D4Attribute(name, basetype), notes);
    }

    public DapAttributeSet newAttributeSet(String name, Object notes)
    {
        return (DapAttributeSet) tag(new D4DMR.D4AttributeSet(name), notes);
    }

    public DapOtherXML newOtherXML(String name, Object notes)
    {
        return (DapOtherXML) tag(new D4OtherXML(name), notes);
    }

    //////////////////////////////////////////////////
    // "Top Level"  nodes

    public DapDimension newDimension(String name, long size, Object notes)
    {
        return (DapDimension) tag(new D4Dimension(name, size), notes);
    }

    public DapMap newMap(DapVariable target, Object notes)
    {
        return (DapMap) tag(new D4Map(target), notes);
    }

    public DapAtomicVariable newAtomicVariable(String name, DapType t, Object notes)
    {
        return (DapAtomicVariable) tag(new D4AtomicVariable(name, t), notes);
    }

    public DapVariable newStructureVariable(String name, DapType t, Object notes)
    {
        return (DapVariable) tag(new D4Structure(name),notes);
    }

    public DapVariable newSequenceVariable(String name, DapType t, Object notes)
        {
            return (DapVariable) tag(new D4Sequence(name),notes);
        }

    public DapGroup newGroup(String name, Object notes)
    {
        return (DapGroup) tag(new D4Group(name), notes);
    }

    public DapDataset newDataset(String name, Object notes)
    {
        return (DapDataset) tag(new D4Dataset(name), notes);
    }

    public DapEnumeration newEnumeration(String name, DapType basetype, Object notes)
    {
        return (DapEnumeration) tag(new D4Enumeration(name, basetype), notes);
    }

    public DapEnumConst newEnumConst(String name, long value, Object notes)
    {
        return (DapEnumConst) tag(new D4EnumConst(name, value), notes);
    }

    public DapStructure newStructure(String name, Object notes)
    {
        return (DapStructure) tag(new D4Structure(name), notes);
    }

    public DapSequence newSequence(String name, Object notes)
    {
        return (DapSequence) tag(new D4Sequence(name), notes);
    }

}

