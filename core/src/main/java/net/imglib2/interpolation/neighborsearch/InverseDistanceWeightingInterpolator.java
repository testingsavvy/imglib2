/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2014 Stephan Preibisch, Tobias Pietzsch, Barry DeZonia,
 * Stephan Saalfeld, Albert Cardona, Curtis Rueden, Christian Dietz, Jean-Yves
 * Tinevez, Johannes Schindelin, Lee Kamentsky, Larry Lindsey, Grant Harris,
 * Mark Hiner, Aivar Grislis, Martin Horn, Nick Perry, Michael Zinsmaier,
 * Steffen Jaensch, Jan Funke, Mark Longair, and Dimiter Prodanov.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package net.imglib2.interpolation.neighborsearch;

import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.Sampler;
import net.imglib2.neighborsearch.KNearestNeighborSearch;
import net.imglib2.type.numeric.RealType;

/**
 * {@link RealRandomAccess} to a {@link KNearestNeighborSearch} whose sample
 * value is generated by weighting the <em>k</em> nearest neighbors of a query
 * real coordinate by their inverse distance to an arbitrary power p.
 * 
 * @param <T>
 * 
 * @author ImgLib2 authors
 * @author Stephan Preibisch
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
public class InverseDistanceWeightingInterpolator< T extends RealType< T > > extends RealPoint implements RealRandomAccess< T >
{
	final static protected double minThreshold = Double.MIN_VALUE * 1000;

	final protected KNearestNeighborSearch< T > search;

	final T value;

	final int numNeighbors;

	final double p;

	final double p2;

	/**
	 * Creates a new {@link InverseDistanceWeightingInterpolator} based on a
	 * {@link KNearestNeighborSearch}.
	 * 
	 * @param search
	 *            - the {@link KNearestNeighborSearch}
	 * @param p
	 *            power applied to the distance, higher values result in
	 *            'sharper' results, 0 results in a non-weighted mean of the
	 *            <em>k</em> nearest neighbors.
	 */
	public InverseDistanceWeightingInterpolator( final KNearestNeighborSearch< T > search, final double p )
	{
		super( search.numDimensions() );

		this.search = search;
		this.p = p;
		p2 = p / 2.0;

		search.search( this );
		this.value = search.getSampler( 0 ).get().copy();
		this.numNeighbors = search.getK();
	}

	@Override
	public T get()
	{
		search.search( this );

		if ( numNeighbors == 1 || search.getSquareDistance( 0 ) / search.getSquareDistance( 1 ) < minThreshold )
			value.set( search.getSampler( 0 ).get() );
		else
		{
			double sumIntensity = 0;
			double sumWeights = 0;

			for ( int i = 0; i < numNeighbors; ++i )
			{
				final Sampler< T > sampler = search.getSampler( i );

				if ( sampler == null )
					break;

				final T t = sampler.get();

				final double weight = computeWeight( search.getSquareDistance( i ) );

				sumWeights += weight;
				sumIntensity += t.getRealDouble() * weight;
			}

			value.setReal( sumIntensity / sumWeights );
		}

		return value;
	}

	protected double computeWeight( final double squareDistance )
	{
		return 1.0 / Math.pow( squareDistance, p2 );
	}

	@Override
	public InverseDistanceWeightingInterpolator< T > copy()
	{
		// TODO: Ugly cast, needs a change in the KNearestNeighborSearch
		// interface
		return new InverseDistanceWeightingInterpolator< T >( search.copy(), p );
	}

	@Override
	public InverseDistanceWeightingInterpolator< T > copyRealRandomAccess()
	{
		return copy();
	}
}
