/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information. */

package dap4.core.util;

import java.util.Arrays;

public class Index
{
    static public Index SCALAR;

    static {
        SCALAR = new Index(0);
        SCALAR.indices[0] = 0;
        SCALAR.dimsizes[0] = 1;
    }

    public int rank;
    public boolean isscalar;
    public long[] indices; // allow direct access
    public long[] dimsizes; // allow direct access

    public Index(int rank)
    {
        this.isscalar = (rank == 0);
        if(rank == 0) rank++;
        this.rank = rank;
        this.dimsizes = new long[rank];
        indices = new long[rank];
        Arrays.fill(indices, 0);
        Arrays.fill(dimsizes, 0);
    }

    public Index(Index index)
    {
        this(index.getRank());
        System.arraycopy(index.indices,0,this.indices,0,this.rank);
        System.arraycopy(index.dimsizes,0,this.dimsizes,0,this.rank);
    }

    /**
     * Compute the linear index
     * from the current odometer indices.
     */
    public long
    index()
    {
        long offset = 0;
        for(int i = 0; i < this.indices.length; i++) {
            offset *= this.dimsizes[i];
            offset += this.indices[i];
        }
        return offset;
    }

    public int getRank() {return this.rank;}

    public long
    get(int i)
    {
	if(i < 0 || i >= this.rank)
	    throw new IllegalArgumentException();
	return this.indices[i];
    }

}
