/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.dmr;
import dap4.core.util.DapSort;

public interface DMRFactory
{
    DapAttribute newAttribute(String name, DapType basetype, Object notes);
    DapAttributeSet newAttributeSet(String name, Object notes);
    DapOtherXML newOtherXML(String name, Object notes);
    DapDimension newDimension(String name, long size, Object notes);
    DapMap newMap(DapVariable target, Object notes);
    DapAtomicVariable newAtomicVariable(String name, DapType t, Object notes);
    DapGroup newGroup(String name, Object notes);
    DapDataset newDataset(String name, Object notes);
    DapEnumeration newEnumeration(String name, DapType basetype, Object notes);
    DapEnumConst newEnumConst(String name, long value, Object notes);
    DapStructure newStructure(String name, Object notes);
    DapSequence newSequence(String name, Object notes);
}
