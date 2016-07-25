/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.servlet;

import dap4.core.ce.CEConstraint;
import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.*;
import dap4.core.data.DSP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Given a DSP, serialize
 * possibly constrained data.
 */

public class DapSerializer
{
    //////////////////////////////////////////////////
    // Instance variables

    protected OutputStream stream = null;
    protected SerialWriter writer = null;
    protected DSP dsp = null;
    protected CEConstraint ce = null;
    protected ByteOrder order = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public DapSerializer()
    {
    }

    /**
     * Primary constructor
     *
     * @param dsp        The DSP to write
     * @param constraint Any applicable constraint
     * @param stream     Write to this stream
     * @param order      The byte order to use
     */
    public DapSerializer(DSP dsp, CEConstraint constraint,
                         OutputStream stream, ByteOrder order)
            throws IOException
    {
        this.dsp = dsp;
        this.order = order;
        this.stream = stream;
        this.ce = constraint;
    }

    public void
    write(DapDataset dmr)
            throws IOException
    {
        writer = new SerialWriter(this.stream, this.order);
	    writer.flush(); // If stream is ChunkWriter, then dump DMR
        // Iterate over the top-level variables in the constraint
        DataCursor dataset = dsp.getRootCursor();
        for(DapVariable var : dmr.getTopVariables()) {
            if(!this.ce.references(var))
                continue;
	    DataCursor varcursor = dataset.get(var);
            if(varcursor == null)
                throw new DapException("DapSerializer: cannot find  Variable data " + var.getFQN());
            writeVariable(cursor, writer);
        }
    }

    //////////////////////////////////////////////////
    // Recursive variable writer

    /**
     * @param data - cursor referencing what to write
     * @param dst - where to write
     * @throws IOException
     */
    protected void
    writeVariable(DataCursor data,SerialWriter dst)
            throws IOException
    {
        DapVariable template = (DapVariable)data.getTemplate());
        dst.startVariable();
        switch (template.getSort()) {
        case ATOMIC:
            writeAtomicVariable(data,dst);
            break;
        case STRUCTURE:
            writeStructure(data,dst);
            break;
        case SEQUENCE:
            writeSequence(data,dst);
            break;
        default:
            assert false : "Unexpected variable type";
        }
        dst.endVariable();
    }

    /**
     * Write out an atomic variable.
     *
     * @param vv the atomic variable
     * @param dv the variable's data
     * @throws IOException
     */
    protected void
    writeAtomicVariable(DataCursor data, SerialWriter dst)
            throws DataException
    {
        DapAtomicVariable template = (DapAtomicVariable)data.getTemplate());
	assert (this.ce.references(template));
        DapType basetype = template.getBaseType();
        if(dapvar.getRank() == 0) { // scalar
            dst.writeObject(basetype, data.read(Index.SCALAR);
        } else {// dimensioned
	    // get the slices from constraint
            List<Slice> slices = ce.getConstrainedSlices(dapvar);
            if(slices == null)
	        throw new DataException("Unknown variable: " + template.getFQN());
            long count = DapUtil.sliceProduct(slices);
            Object vector = data.read(slices);
            dst.writeArray(basetype, vector);
 	}
    }

    /**
     * Write out a single or array structure instance
     *
     * @param data
     * @param dst - where to write
     * @throws DataException
     */

    protected void
    writeStructure(DataCursor data, SerialWriter dst)
            throws DataException
    {
        DapStructure template = (DapStructure)data.getTemplate());
	assert (this.ce.references(template));
	if(template.getRank() == 0) { // scalar
	    writeStructure1(data,dst);
	} else {
	    List<Slices> slices = ce.getConstrainedSlices(template);
            long count = DapUtil.sliceProduct(slices);
	    dst.writeCount(count);
	    Odometer odom = Odometer.factory(slices);
	    while(odom.hasNext()) {
		Index index = odom.next();
	        DataCursor instance = (DataCursor)data.read(index);
		writeStructure1(instance,dst);
            }
    }

    /**
     * Write out a single structure instance
     *
     * @param data
     * @param dst - where to write
     * @throws DataException
     */

    protected void
    writeStructure1(DataCursor instance, SerialWriter dst)
            throws DataException
    {
        DapStructure template = (DapStructure)instance.getTemplate());
	assert (this.ce.references(template));
        for(DapVariable field : template.getFields()) {
	    if(!this.ce.references(field)) continue; // not in the view
            DataCursor df = instance.read(field);
	    writeVariable(df,dst);
	}
    }

    /**
     * Write out a single or array sequence instance
     *
     * @param data
     * @param dst - where to write
     * @throws DataException
     */

    protected void
    writeSequence(DataCursor data, SerialWriter dst)
            throws DataException
    {
        DapSequence template = (DapSequence)data.getTemplate());
	assert (this.ce.references(template));
	if(template.getRank() == 0) { // scalar
	    writeSequence1(data,dst);
	} else {
	    List<Slices> slices = ce.getConstrainedSlices(template);
            long count = DapUtil.sliceProduct(slices);
	    dst.writeCount(count);
	    Odometer odom = Odometer.factory(slices);
	    while(odom.hasNext()) {
		Index index = odom.next();
	        DataCursor instance = (DataCursor)data.read(index);
		writeSequence1(instance,dst);
            }
    }


    /**
     * Write out a single Sequence of records
     * (Eventually use any filter in the DapVariable)
     *
     * @param instance the sequence instance
     * @param dst write target
     * @throws DataException
     */

    protected void
    writeSequence1(DataCursor instance, SerialWriter dst)
            throws DataException
    {
        DapSequence template = (DapSequenceucture)instance.getTemplate());
	assert (this.ce.references(template));
        long nrecs = instance.getRecordCount();
	for(long i=0;i<nrecs;i++) {
	    DataCursor record = instance.getRecord(i);
	    writeRecord(record,dst);
	}
    }

    /**
     * Write out a single Record instance.
     *
     * @param record the record data cursor
     * @param dst to which to write
     * @throws DataException
     */

    protected void
    writeRecord(DataCursor record, SerialWriter dst)
            throws DataException
    {
        DapSequence template = (DapSequence)record.getTemplate());
        for(DapVariable field : template.getFields()) {
	    if(!this.ce.references(field)) continue; // not in the view
            DataCursor df = record.read(field);
	    writeVariable(df,dst);
	}
    }

}


