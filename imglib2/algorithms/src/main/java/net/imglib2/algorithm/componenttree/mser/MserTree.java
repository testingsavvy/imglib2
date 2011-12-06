package net.imglib2.algorithm.componenttree.mser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.componenttree.Component;
import net.imglib2.algorithm.componenttree.ComponentTree;
import net.imglib2.algorithm.componenttree.pixellist.PixelList;
import net.imglib2.algorithm.componenttree.pixellist.PixelListComponent;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;

/**
 * MSER tree of an image stored as a tree of {@link PixelListComponent}s. This
 * class is used both to represent and build the tree. For building the tree
 * {@link Component.Handler} is implemented to gather
 * {@link MserComponentIntermediate} emitted by {@link ComponentTree}.
 *
 * <p>
 * Maximally Stable Extremal Regions (MSER) are selected from the component tree
 * as follows. For each component, an instability score is computed as
 * <!-- |R_i - R_{i-\Delta}| / |R_i| -->
 * <math style="display:block"><mi>s</mi><mfenced><msub><mi>R</mi><mi>i</mi></msub></mfenced><mo>=</mo><mfrac><mfenced open="|" close="|"><mrow><msub><mi>R</mi><mi>i</mi></msub><mo lspace=mediummathspace rspace=mediummathspace>\</mo><msub><mi>R</mi><mrow><mi>i</mi><mo>-</mo><mi>&Delta;</mi></mrow></msub></mrow></mfenced><mfenced open="|" close="|"><msub><mi>R</mi><mi>i</mi></msub></mfenced></mfrac></math>
 * </p>
 *
 * <p>
 * Regions whose score is a local minimum are selected as MSER candidates.
 * </p>
 *
 * <p>
 * A candidate region is discarded if its size (number of pixels) is smaller
 * than <em>minSize</em> or larger than <em>maxSize</em>. A candidate region is
 * discarded if its instability score is greater than <em>maxVar</em>.
 * </p>
 *
 * <p>
 * A tree is build of the remaining candidates. Finally, candidates are pruned
 * from the tree, if they are too similar to their parent: Let <em>A</em>,
 * <em>B</em> be a region and its parent. Then <em>A</em> is discarded if
 * <!-- |B - A| / |B| <= minDiversity -->
 * <math style="display:block"><mfrac><mfenced open="|" close="|"><mrow><mi>B</mi><mo lspace=mediummathspace rspace=mediummathspace>\</mo><mi>A</mi></mrow></mfenced><mfenced open="|" close="|"><mi>B</mi></mfenced></mfrac><mo>&le;</mo><mi>minDiversity</mi></math>
 * </p>
 *
 * <p>
 * <strong>TODO</strong> Add support for non-zero-min RandomAccessibleIntervals.
 * (Currently, we assume that the input image is a <em>zero-min</em> interval.)
 * </p>
 *
 * @author Tobias Pietzsch
 *
 * @param <T>
 *            value type of the input image.
 */
public final class MserTree< T extends Type< T > > implements Component.Handler< MserComponentIntermediate< T > >, Iterable< Mser< T > >
{
	/**
	 * Build a MSER tree from an input image. Calls
	 * {@link #buildMserTree(RandomAccessibleInterval, RealType, long, long, double, double, ImgFactory, boolean)}
	 * using an {@link ArrayImgFactory} or {@link CellImgFactory} depending on
	 * input image size.
	 *
	 * @param input
	 *            the input image.
	 * @param delta
	 *            delta for computing instability score.
	 * @param minSize
	 *            minimum size (in pixels) of accepted MSER.
	 * @param maxSize
	 *            maximum size (in pixels) of accepted MSER.
	 * @param maxVar
	 *            maximum instability score of accepted MSER.
	 * @param minDiversity
	 *            minimal diversity of adjacent accepted MSER.
	 * @param darkToBright
	 *            whether to apply thresholds from dark to bright (true) or
	 *            bright to dark (false)
	 * @return MSER tree of the image.
	 */
	public static < T extends RealType< T > > MserTree< T > buildMserTree( final RandomAccessibleInterval< T > input, final T delta, final long minSize, final long maxSize, final double maxVar, final double minDiversity, boolean darkToBright )
	{
		final int numDimensions = input.numDimensions();
		long size = 1;
		for ( int d = 0; d < numDimensions; ++d )
			size *= input.dimension( d );
		if( size > Integer.MAX_VALUE ) {
			int cellSize = ( int ) Math.pow( Integer.MAX_VALUE / new LongType().getEntitiesPerPixel(), 1.0 / numDimensions );
			return buildMserTree( input, delta, minSize, maxSize, maxVar, minDiversity, new CellImgFactory< LongType >( cellSize ), darkToBright );
		} else
			return buildMserTree( input, delta, minSize, maxSize, maxVar, minDiversity, new ArrayImgFactory< LongType >(), darkToBright );
	}

	/**
	 * Build a MSER tree from an input image.
	 *
	 * @param input
	 *            the input image.
	 * @param delta
	 *            delta for computing instability score.
	 * @param minSize
	 *            minimum size (in pixels) of accepted MSER.
	 * @param maxSize
	 *            maximum size (in pixels) of accepted MSER.
	 * @param maxVar
	 *            maximum instability score of accepted MSER.
	 * @param minDiversity
	 *            minimal diversity of adjacent accepted MSER.
	 * @param imgFactory
	 *            used for creating the {@link PixelList} image {@see
	 *            MserComponentGenerator}.
	 * @param darkToBright
	 *            whether to apply thresholds from dark to bright (true) or
	 *            bright to dark (false)
	 * @return MSER tree of the image.
	 */
	public static < T extends RealType< T > > MserTree< T > buildMserTree( final RandomAccessibleInterval< T > input, final T delta, final long minSize, final long maxSize, final double maxVar, final double minDiversity, final ImgFactory< LongType > imgFactory, boolean darkToBright )
	{
		final T max = delta.createVariable();
		max.setReal( darkToBright ? delta.getMaxValue() : delta.getMinValue() );
		final MserComponentGenerator< T > generator = new MserComponentGenerator< T >( max, input, imgFactory );
		final Comparator< T > comparator = darkToBright ? new ComponentTree.DarkToBright< T >() : new ComponentTree.BrightToDark< T >();
		final ComputeDelta< T > computeDelta = darkToBright ? new ComputeDeltaDarkToBright< T >( delta ) : new ComputeDeltaBrightToDark< T >( delta ); 
		final MserTree< T > tree = new MserTree< T >( comparator, computeDelta, minSize, maxSize, maxVar, minDiversity );
		ComponentTree.buildComponentTree( input, generator, tree, comparator );
		tree.pruneDuplicates();
		return tree;
	}

	/**
	 * Build a MSER tree from an input image. Calls
	 * {@link #buildMserTree(RandomAccessibleInterval, ComputeDelta, long, long, double, double, ImgFactory, Type, Comparator)}
	 * using an {@link ArrayImgFactory} or {@link CellImgFactory} depending on
	 * input image size.
	 *
	 * @param input
	 *            the input image.
	 * @param computeDelta
	 *            to compute (value - delta).
	 * @param minSize
	 *            minimum size (in pixels) of accepted MSER.
	 * @param maxSize
	 *            maximum size (in pixels) of accepted MSER.
	 * @param maxVar
	 *            maximum instability score of accepted MSER.
	 * @param minDiversity
	 *            minimal diversity of adjacent accepted MSER.
	 * @param maxValue
	 *            a value (e.g., grey-level) greater than any occurring in the
	 *            input image.
	 * @param comparator
	 *            determines ordering of threshold values.
	 * @return MSER tree of the image.
	 */
	public static < T extends Type< T > > MserTree< T > buildMserTree( final RandomAccessibleInterval< T > input, final ComputeDelta< T > computeDelta, final long minSize, final long maxSize, final double maxVar, final double minDiversity, final T maxValue, final Comparator< T > comparator )
	{
		final int numDimensions = input.numDimensions();
		long size = 1;
		for ( int d = 0; d < numDimensions; ++d )
			size *= input.dimension( d );
		if( size > Integer.MAX_VALUE ) {
			int cellSize = ( int ) Math.pow( Integer.MAX_VALUE / new LongType().getEntitiesPerPixel(), 1.0 / numDimensions );
			return buildMserTree( input, computeDelta, minSize, maxSize, maxVar, minDiversity, new CellImgFactory< LongType >( cellSize ), maxValue, comparator );
		} else
			return buildMserTree( input, computeDelta, minSize, maxSize, maxVar, minDiversity, new ArrayImgFactory< LongType >(), maxValue, comparator );
	}

	/**
	 * Build a MSER tree from an input image.
	 *
	 * @param input
	 *            the input image.
	 * @param computeDelta
	 *            to compute (value - delta).
	 * @param minSize
	 *            minimum size (in pixels) of accepted MSER.
	 * @param maxSize
	 *            maximum size (in pixels) of accepted MSER.
	 * @param maxVar
	 *            maximum instability score of accepted MSER.
	 * @param minDiversity
	 *            minimal diversity of adjacent accepted MSER.
	 * @param imgFactory
	 *            used for creating the {@link PixelList} image {@see
	 *            MserComponentGenerator}.
	 * @param maxValue
	 *            a value (e.g., grey-level) greater than any occurring in the
	 *            input image.
	 * @param comparator
	 *            determines ordering of threshold values.
	 * @return MSER tree of the image.
	 */
	public static < T extends Type< T > > MserTree< T > buildMserTree( final RandomAccessibleInterval< T > input, final ComputeDelta< T > computeDelta, final long minSize, final long maxSize, final double maxVar, final double minDiversity, final ImgFactory< LongType > imgFactory, final T maxValue, final Comparator< T > comparator )
	{
		final MserComponentGenerator< T > generator = new MserComponentGenerator< T >( maxValue, input, imgFactory );
		final MserTree< T > tree = new MserTree< T >( comparator, computeDelta, minSize, maxSize, maxVar, minDiversity );
		ComponentTree.buildComponentTree( input, generator, tree, comparator );
		tree.pruneDuplicates();
		return tree;
	}

	private final HashSet< Mser< T > > roots;

	private final ArrayList< Mser< T > > nodes;

	private final Comparator< T > comparator;

	private final ComputeDelta< T > delta;

	/**
	 * Minimum size (in pixels) of accepted MSER.
	 */
	private final long minSize;

	/**
	 * Maximum size (in pixels) of accepted MSER.
	 */
	private final long maxSize;

	/**
	 * Maximum instability score of accepted MSER.
	 */
	private final double maxVar;

	/**
	 * Minimal diversity of adjacent accepted MSER.
	 */
	private final double minDiversity;
	
	private MserTree( final Comparator< T > comparator, final ComputeDelta< T > delta, final long minSize, final long maxSize, final double maxVar, final double minDiversity )
	{
		roots = new HashSet< Mser< T > >();
		nodes = new ArrayList< Mser< T > >();
		this.comparator = comparator;
		this.delta = delta;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.maxVar = maxVar;
		this.minDiversity = minDiversity;
	}

	/**
	 * Remove from the tree candidates which are too similar to their parent.
	 * Let <em>A</em>, <em>B</em> be a region and its parent.
	 * Then <em>A</em> is discarded if |B - A| / |B| <= minDiversity.
	 */
	private void pruneDuplicates()
	{
		nodes.clear();
		for ( Mser< T > mser : roots )
			pruneChildren ( mser );
		nodes.addAll( roots );
	}

	private void pruneChildren( Mser< T > mser )
	{
		final ArrayList< Mser< T > > validChildren = new ArrayList< Mser< T > >();
		for ( int i = 0; i < mser.children.size(); ++i )
		{
			Mser< T > m = mser.children.get( i );
			double div = ( mser.size() - m.size() ) / (double) mser.size();
			if ( div > minDiversity )
			{
				validChildren.add( m );
				pruneChildren( m );
			}
			else
			{
				mser.children.addAll( m.children );
				for ( Mser< T > m2 : m.children )
					m2.parent = mser;
			}
		}
		mser.children.clear();
		mser.children.addAll( validChildren );
		nodes.addAll( validChildren );
	}

	@Override
	public void emit( MserComponentIntermediate< T > component )
	{
		new MserEvaluationNode< T >( component, comparator, delta, this );
		component.children.clear();
	}

	/**
	 * Called when a local minimal {@link MserEvaluationNode} (a MSER candidate)
	 * is found.
	 *
	 * @param node
	 *            MSER candidate.
	 */
	void foundNewMinimum( MserEvaluationNode< T > node )
	{
		if ( node.size >= minSize && node.size <= maxSize && node.score <= maxVar )
		{
			Mser< T > mser = new Mser< T >( node );
			for ( Mser< T > m : node.mserThisOrChildren )
				mser.children.add( m );
			node.mserThisOrChildren.clear();
			node.mserThisOrChildren.add( mser );
			
			for ( Mser< T > m : mser.children )
				roots.remove( m );
			roots.add( mser );
			nodes.add( mser );
		}
	}

	/**
	 * Get number of detected MSERs.
	 *
	 * @return number of detected MSERs.
	 */
	public int size()
	{
		return nodes.size();
	}

	/**
	 * Returns an iterator over all MSERs in the tree.
	 *
	 * @return iterator over all MSERss in the tree.
	 */
	@Override
	public Iterator< Mser< T > > iterator()
	{
		return nodes.iterator();
	}

	/**
	 * Get the set of roots of the MSER tree (respectively forest...).
	 *
	 * @return set of roots.
	 */
	public HashSet< Mser< T > > roots()
	{
		return roots;
	}
}
