/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.cdm.dsp;

import dap4.core.dmr.*;
import dap4.core.util.DapException;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static dap4.cdm.dsp.CDMDMR.*;

public class CDMDMRFactory implements DMRFactory
{
    //////////////////////////////////////////////////

    protected Stack<DapNode> scope = new Stack<>();

    protected DapNode top() {return (scope.isEmpty()?null:scope.peek());}

    // Collect all created nodes
    protected List<DapNode> allnodes = new ArrayList<>();

    //////////////////////////////////////////////////
    // Constructor
    public CDMDMRFactory()
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
                throw new DapException("CDMFactory: unexpected container: " + container);
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
        return (DapAttribute) tag(new CDMAttribute(name, basetype),notes);
    }

    public DapAttributeSet newAttributeSet(String name, Object notes)
    {
        return (DapAttributeSet) tag(new CDMDMR.CDMAttributeSet(name),notes);
    }

    public DapOtherXML newOtherXML(String name, Object notes)
    {
        return (DapOtherXML) tag(new CDMOtherXML(name),notes);
    }

    //////////////////////////////////////////////////
    // "Top Level"  nodes

    public DapDimension newDimension(String name, long size, Object notes)
    {
        return (DapDimension) tag(new CDMDimension(name, size),notes);
    }

    public DapMap newMap(DapVariable target, Object notes)
    {
        return (DapMap) tag(new CDMMap(target),notes);
    }

    public DapAtomicVariable newAtomicVariable(String name, DapType t, Object notes)
    {
        return (DapAtomicVariable) tag(new CDMAtomicVariable(name, t),notes);
    }

    public DapGroup newGroup(String name, Object notes)
    {
        return (DapGroup) tag(new CDMGroup(name),notes);
    }

    public DapDataset newDataset(String name, Object notes)
    {
        return (DapDataset) tag(new CDMDataset(name),notes);
    }

    public DapEnumeration newEnumeration(String name, DapType basetype, Object notes)
    {
        return (DapEnumeration) tag(new CDMEnumeration(name, basetype),notes);
    }

    public DapEnumConst newEnumConst(String name, long value, Object notes)
    {
        return (DapEnumConst) tag(new CDMEnumConst(name, value),notes);
    }

    public DapStructure newStructure(String name, Object notes)
    {
        return (DapStructure) tag(new CDMStructure(name),notes);
    }

    public DapSequence newSequence(String name, Object notes)
    {
        return (DapSequence) tag(new CDMSequence(name),notes);
    }

}

