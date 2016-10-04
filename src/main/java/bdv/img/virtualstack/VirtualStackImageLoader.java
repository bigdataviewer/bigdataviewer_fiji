package bdv.img.virtualstack;

import java.util.ArrayList;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.TypedBasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Fraction;

/**
 * ImageLoader backed by a ImagePlus. The ImagePlus may be virtual and in
 * contrast to the imglib2 wrappers, we do not try to load all slices into
 * memory. Instead slices are stored in {@link VolatileGlobalCellCache}.
 *
 * Use {@link #createFloatInstance(ImagePlus)},
 * {@link #createUnsignedByteInstance(ImagePlus)} or
 * {@link #createUnsignedShortInstance(ImagePlus)} depending on the ImagePlus
 * pixel type.
 *
 * When loading images ({@link #getSetupImgLoader(int)},
 * {@link BasicSetupImgLoader#getImage(int, ImgLoaderHint...)}) the provided
 * setup id is used as the channel index of the {@link ImagePlus}, the provided
 * timepoint id is used as the frame index of the {@link ImagePlus}.
 *
 * @param <T>
 *            (non-volatile) pixel type
 * @param <V>
 *            volatile pixel type
 * @param <A>
 *            volatile array access type
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public abstract class VirtualStackImageLoader< T extends NativeType< T >, V extends Volatile< T > & NativeType< V >, A extends VolatileAccess >
		implements ViewerImgLoader, TypedBasicImgLoader< T >
{
	public static VirtualStackImageLoader< FloatType, VolatileFloatType, VolatileFloatArray > createFloatInstance( final ImagePlus imp )
	{
		return new VirtualStackImageLoader< FloatType, VolatileFloatType, VolatileFloatArray >(
				imp, new VirtualStackVolatileFloatArrayLoader( imp ), new FloatType(), new VolatileFloatType() )
		{
			@Override
			protected void linkType( final CachedCellImg< FloatType, VolatileFloatArray > img )
			{
				img.setLinkedType( new FloatType( img ) );
			}

			@Override
			protected void linkVolatileType( final CachedCellImg< VolatileFloatType, VolatileFloatArray > img )
			{
				img.setLinkedType( new VolatileFloatType( img ) );
			}
		};
	}

	public static VirtualStackImageLoader< UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray > createUnsignedShortInstance( final ImagePlus imp )
	{
		return new VirtualStackImageLoader< UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray >(
				imp, new VirtualStackVolatileShortArrayLoader( imp ), new UnsignedShortType(), new VolatileUnsignedShortType() )
		{
			@Override
			protected void linkType( final CachedCellImg< UnsignedShortType, VolatileShortArray > img )
			{
				img.setLinkedType( new UnsignedShortType( img ) );
			}

			@Override
			protected void linkVolatileType( final CachedCellImg< VolatileUnsignedShortType, VolatileShortArray > img )
			{
				img.setLinkedType( new VolatileUnsignedShortType( img ) );
			}
		};
	}

	public static VirtualStackImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray > createUnsignedByteInstance( final ImagePlus imp )
	{
		return new VirtualStackImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray >(
				imp, new VirtualStackVolatileByteArrayLoader( imp ), new UnsignedByteType(), new VolatileUnsignedByteType() )
		{
			@Override
			protected void linkType( final CachedCellImg< UnsignedByteType, VolatileByteArray > img )
			{
				img.setLinkedType( new UnsignedByteType( img ) );
			}

			@Override
			protected void linkVolatileType( final CachedCellImg< VolatileUnsignedByteType, VolatileByteArray > img )
			{
				img.setLinkedType( new VolatileUnsignedByteType( img ) );
			}
		};
	}

	public static VirtualStackImageLoader< ARGBType, VolatileARGBType, VolatileIntArray > createARGBInstance( final ImagePlus imp )
	{
		return new VirtualStackImageLoader< ARGBType, VolatileARGBType, VolatileIntArray >(
				imp, new VirtualStackVolatileARGBArrayLoader( imp ), new ARGBType(), new VolatileARGBType() )
		{
			@Override
			protected void linkType( final CachedCellImg< ARGBType, VolatileIntArray > img )
			{
				img.setLinkedType( new ARGBType( img ) );
			}

			@Override
			protected void linkVolatileType( final CachedCellImg< VolatileARGBType, VolatileIntArray > img )
			{
				img.setLinkedType( new VolatileARGBType( img ) );
			}
		};
	}

	private static double[][] mipmapResolutions = new double[][] { { 1, 1, 1 } };

	private static AffineTransform3D[] mipmapTransforms = new AffineTransform3D[] { new AffineTransform3D() };

	private final CacheArrayLoader< A > loader;

	private final VolatileGlobalCellCache cache;

	private final long[] dimensions;

	private final int[] cellDimensions;

	private final ArrayList< SetupImgLoader > setupImgLoaders;

	protected VirtualStackImageLoader( final ImagePlus imp, final CacheArrayLoader< A > loader, final T type, final V volatileType )
	{
		this.loader = loader;
		dimensions = new long[] { imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		cellDimensions = new int[] { imp.getWidth(), imp.getHeight(), 1 };
		final int numTimepoints = imp.getNFrames();
		final int numSetups = imp.getNChannels();
		cache = new VolatileGlobalCellCache( numTimepoints, numSetups, 1, 1 );
		setupImgLoaders = new ArrayList<>();
		for ( int setupId = 0; setupId < numSetups; ++setupId )
			setupImgLoaders.add( new SetupImgLoader( setupId, type, volatileType ) );
	}

	protected abstract void linkType( CachedCellImg< T, A > img );

	protected abstract void linkVolatileType( CachedCellImg< V, A > img );

	@Override
	public VolatileGlobalCellCache getCache()
	{
		return cache;
	}

	@Override
	public SetupImgLoader getSetupImgLoader( final int setupId )
	{
		return setupImgLoaders.get( setupId );
	}

	public class SetupImgLoader extends AbstractViewerSetupImgLoader< T, V >
	{
		private final int setupId;

		protected SetupImgLoader( final int setupId, final T type, final V volatileType )
		{
			super( type, volatileType );
			this.setupId = setupId;
		}

		@Override
		public RandomAccessibleInterval< V > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
		{
			final ViewId view = new ViewId( timepointId, setupId );
			final CachedCellImg< V, A > img = prepareCachedImage( view, level, LoadingStrategy.BUDGETED );
			linkVolatileType( img );
			return img;
		}

		@Override
		public RandomAccessibleInterval< T > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
		{
			final ViewId view = new ViewId( timepointId, setupId );
			final CachedCellImg< T, A > img = prepareCachedImage( view, level, LoadingStrategy.BLOCKING );
			linkType( img );
			return img;
		}

		/**
		 * (Almost) create a {@link CachedCellImg} backed by the cache. The created
		 * image needs a {@link NativeImg#setLinkedType(net.imglib2.type.Type)
		 * linked type} before it can be used. The type should be either
		 * {@link ARGBType} and {@link VolatileARGBType}.
		 */
		protected < T extends NativeType< T > > CachedCellImg< T, A > prepareCachedImage( final ViewId view, final int level, final LoadingStrategy loadingStrategy )
		{
			final int priority = 0;
			final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
			final CellCache< A > c = cache.new VolatileCellCache<>( view.getTimePointId(), view.getViewSetupId(), level, cacheHints, loader );
			final VolatileImgCells< A > cells = new VolatileImgCells<>( c, new Fraction(), dimensions, cellDimensions );
			final CachedCellImg< T, A > img = new CachedCellImg<>( cells );
			return img;
		}

		@Override
		public double[][] getMipmapResolutions()
		{
			return mipmapResolutions;
		}

		@Override
		public AffineTransform3D[] getMipmapTransforms()
		{
			return mipmapTransforms;
		}

		@Override
		public int numMipmapLevels()
		{
			return 1;
		}
	}
}
