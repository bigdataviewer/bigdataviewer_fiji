/*-
 * #%L
 * Fiji plugins for starting BigDataViewer and exporting data.
 * %%
 * Copyright (C) 2014 - 2022 BigDataViewer developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package bdv.img.virtualstack;

import java.util.HashMap;
import java.util.function.Function;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.TypedBasicImgLoader;

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
public class VirtualStackImageLoader< T extends NativeType< T >, V extends Volatile< T > & NativeType< V >, A extends VolatileAccess & DataAccess >
		implements ViewerImgLoader, TypedBasicImgLoader< T >
{
	public static VirtualStackImageLoader< FloatType, VolatileFloatType, VolatileFloatArray > createFloatInstance( final ImagePlus imp )
	{
		return createFloatInstance( imp, 0 );
	}

	public static VirtualStackImageLoader< FloatType, VolatileFloatType, VolatileFloatArray > createFloatInstance( final ImagePlus imp, final int offset )
	{
		return new VirtualStackImageLoader<>( imp,array -> new VolatileFloatArray( ( float[] ) array, true ), new FloatType(), new VolatileFloatType(), offset );
	}

	public static VirtualStackImageLoader< UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray > createUnsignedShortInstance( final ImagePlus imp )
	{
		return createUnsignedShortInstance( imp, 0 );
	}

	public static VirtualStackImageLoader< UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray > createUnsignedShortInstance( final ImagePlus imp, final int offset )
	{
		return new VirtualStackImageLoader<>( imp, array -> new VolatileShortArray( ( short[] ) array, true ), new UnsignedShortType(), new VolatileUnsignedShortType(), offset );
	}

	public static VirtualStackImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray > createUnsignedByteInstance( final ImagePlus imp )
	{
		return createUnsignedByteInstance( imp, 0 );
	}

	public static VirtualStackImageLoader< UnsignedByteType, VolatileUnsignedByteType, VolatileByteArray > createUnsignedByteInstance( final ImagePlus imp, final int offset )
	{
		return new VirtualStackImageLoader<>( imp, array -> new VolatileByteArray( ( byte[] ) array, true ), new UnsignedByteType(), new VolatileUnsignedByteType(), offset );
	}

	public static VirtualStackImageLoader< ARGBType, VolatileARGBType, VolatileIntArray > createARGBInstance( final ImagePlus imp )
	{
		return createARGBInstance( imp, 0 );
	}

	public static VirtualStackImageLoader< ARGBType, VolatileARGBType, VolatileIntArray > createARGBInstance( final ImagePlus imp, final int offset )
	{
		return new VirtualStackImageLoader<>( imp, array -> new VolatileIntArray( ( int[] ) array, true ), new ARGBType(), new VolatileARGBType(), offset );
	}

	private static double[][] mipmapResolutions = new double[][] { { 1, 1, 1 } };

	private static AffineTransform3D[] mipmapTransforms = new AffineTransform3D[] { new AffineTransform3D() };

	private final CacheArrayLoader< A > loader;

	private final VolatileGlobalCellCache cache;

	private final long[] dimensions;

	private final int[] cellDimensions;

	private final HashMap< Integer, SetupImgLoader > setupImgLoaders;

	private static int getByteCount( final PrimitiveType primitiveType )
	{
		// TODO: PrimitiveType.getByteCount() should be public, then we wouldn't have to do this...
		switch ( primitiveType )
		{
		case BYTE:
			return 1;
		case SHORT:
			return 2;
		case INT:
		case FLOAT:
		default:
			return 4;
		}
	}

	protected VirtualStackImageLoader( final ImagePlus imp, final Function< Object, A > wrapPixels, final T type, final V volatileType, final int setupOffset )
	{
		this.loader = new VirtualStackArrayLoader<>( imp, wrapPixels, getByteCount( type.getNativeTypeFactory().getPrimitiveType() ) );
		dimensions = new long[] { imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		cellDimensions = new int[] { imp.getWidth(), imp.getHeight(), 1 };
		final int numSetups = imp.getNChannels();
		cache = new VolatileGlobalCellCache( 1, 1 );
		setupImgLoaders = new HashMap<>();
		for ( int setupId = 0; setupId < numSetups; ++setupId )
			setupImgLoaders.put( setupOffset + setupId, new SetupImgLoader( setupId, type, volatileType ) );
	}

	protected VirtualStackImageLoader( final ImagePlus imp, final Function< Object, A > wrapPixels, final T type, final V volatileType )
	{
		this( imp, wrapPixels, type, volatileType, 0 );
	}

	@Override
	public VolatileGlobalCellCache getCacheControl()
	{
		return cache;
	}

	@Override
	public SetupImgLoader getSetupImgLoader( final int setupId )
	{
		return setupImgLoaders.get( setupId );
	}

	static class VirtualStackArrayLoader< A > implements CacheArrayLoader< A >
	{
		private final ImagePlus imp;

		private final Function< Object, A > wrapPixels;

		private final int bytesPerElement;

		public VirtualStackArrayLoader( final ImagePlus imp, final Function< Object, A > wrapPixels, final int bytesPerElement )
		{
			this.imp = imp;
			this.wrapPixels = wrapPixels;
			this.bytesPerElement = bytesPerElement;
		}

		@Override
		public A loadArray( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
		{
			final int channel = setup + 1;
			final int slice = ( int ) min[ 2 ] + 1;
			final int frame = timepoint + 1;
			return wrapPixels.apply( imp.getStack().getProcessor( imp.getStackIndex( channel, slice, frame ) ).getPixels() );
		}

		@Override
		public int getBytesPerElement()
		{
			return bytesPerElement;
		}
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
			return prepareCachedImage( timepointId, level, LoadingStrategy.BUDGETED, volatileType );
		}

		@Override
		public RandomAccessibleInterval< T > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
		{
			return prepareCachedImage( timepointId, level, LoadingStrategy.BLOCKING, type );
		}

		/**
		 * Create a {@link CachedCellImg} backed by the cache.
		 */
		protected < T extends NativeType< T > > AbstractCellImg< T, A, ?, ? > prepareCachedImage( final int timepointId, final int level, final LoadingStrategy loadingStrategy, final T type )
		{
			final int priority = 0;
			final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
			final CellGrid grid = new CellGrid( dimensions, cellDimensions );
			return cache.createImg( grid, timepointId, setupId, level, cacheHints, loader, type );
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
