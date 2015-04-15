package bdv.ij.export.imgloader;

import java.util.ArrayList;

import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.TypedBasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.imagestack.ImageStackImageLoader;
import bdv.img.virtualstack.VirtualStackImageLoader;

/**
 * This {@link BasicImgLoader} implementation returns a wrapped, converted
 * {@link ImagePlus}. It is used for exporting {@link ImagePlus} to hdf5.
 *
 * Internally it relies on {@link VirtualStackImageLoader} to be able to handle
 * large virtual stacks.
 *
 * When {@link #getImage(ViewId) loading images}, the provided setup id is used
 * as the channel index of the {@link ImagePlus}, the provided timepoint id is
 * used as the frame index of the {@link ImagePlus}.
 *
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
public class ImagePlusImgLoader implements TypedBasicImgLoader< UnsignedShortType >
{
	public static enum MinMaxOption
	{
		SET,
		COMPUTE,
		TAKE_FROM_IMAGEPROCESSOR
	}

	public static ImagePlusImgLoader createGray8( final ImagePlus imp, final MinMaxOption minMaxOption, final double min, final double max )
	{
		if( imp.getType() != ImagePlus.GRAY8 )
			throw new RuntimeException( "expected ImagePlus type GRAY8" );
		if ( imp.getStack() != null && imp.getStack().isVirtual() )
			return new ImagePlusImgLoader( imp, VirtualStackImageLoader.createUnsignedByteInstance( imp ), minMaxOption, min, max );
		else
			return new ImagePlusImgLoader( imp, ImageStackImageLoader.createUnsignedByteInstance( imp ), minMaxOption, min, max );
	}

	public static ImagePlusImgLoader createGray16( final ImagePlus imp, final MinMaxOption minMaxOption, final double min, final double max )
	{
		if( imp.getType() != ImagePlus.GRAY16 )
			throw new RuntimeException( "expected ImagePlus type GRAY16" );
		if ( imp.getStack() != null && imp.getStack().isVirtual() )
			return new ImagePlusImgLoader( imp, VirtualStackImageLoader.createUnsignedShortInstance( imp ), minMaxOption, min, max );
		else
			return new ImagePlusImgLoader( imp, ImageStackImageLoader.createUnsignedShortInstance( imp ), minMaxOption, min, max );
	}

	public static ImagePlusImgLoader createGray32( final ImagePlus imp, final MinMaxOption minMaxOption, final double min, final double max )
	{
		if( imp.getType() != ImagePlus.GRAY32 )
			throw new RuntimeException( "expected ImagePlus type GRAY32" );
		if ( imp.getStack() != null && imp.getStack().isVirtual() )
			return new ImagePlusImgLoader( imp, VirtualStackImageLoader.createFloatInstance( imp ), minMaxOption, min, max );
		else
			return new ImagePlusImgLoader( imp, ImageStackImageLoader.createFloatInstance( imp ), minMaxOption, min, max );
	}

	protected final ImagePlus imp;

	protected final BasicImgLoader loader;

	protected VolatileGlobalCellCache loadercache;

	protected final ArrayList< SetupImgLoader< ? > > setupImgLoaders;

	protected double impMin;

	protected double impMax;

	@SuppressWarnings( "unchecked" )
	protected< T extends RealType< T > & NativeType< T > > ImagePlusImgLoader( final ImagePlus imp,
			final TypedBasicImgLoader< T > loader,
			final MinMaxOption minMaxOption,
			final double min,
			final double max )
	{
		this.imp = imp;
		this.loader = loader;

		final int numSetups = imp.getNChannels();
		setupImgLoaders = new ArrayList< SetupImgLoader< ? > >();
		for ( int setupId = 0; setupId < numSetups; ++setupId )
			setupImgLoaders.add( new SetupImgLoader< T >( loader.getSetupImgLoader( setupId ) ) );

		if ( loader instanceof VirtualStackImageLoader )
			this.loadercache = ( ( VirtualStackImageLoader< ?, ?, ? > ) loader ).getCache();
		else
			this.loadercache = null;

		if ( minMaxOption == MinMaxOption.COMPUTE )
		{
			impMin = Double.POSITIVE_INFINITY;
			impMax = Double.NEGATIVE_INFINITY;
			final T minT = loader.getSetupImgLoader( 0 ).getImageType().createVariable();
			final T maxT = minT.createVariable();
			final int numTimepoints = imp.getNFrames();
			for ( int t = 0; t < numTimepoints; t++ )
				for ( int s = 0; s < numSetups; ++s )
				{
					ComputeMinMax.computeMinMax( loader.getSetupImgLoader( s ).getImage( t ), minT, maxT );
					impMin = Math.min( minT.getRealDouble(), impMin );
					impMax = Math.max( maxT.getRealDouble(), impMax );
					if ( loadercache != null )
						loadercache.clearCache();
				}
			System.out.println( "COMPUTE" );
			System.out.println( impMin + "  " + impMax );
		}
		else if ( minMaxOption == MinMaxOption.TAKE_FROM_IMAGEPROCESSOR )
		{
			impMin = imp.getDisplayRangeMin();
			impMax = imp.getDisplayRangeMax();
			System.out.println( "TAKE_FROM_IMAGEPROCESSOR" );
			System.out.println( impMin + "  " + impMax );
		}
		else
		{
			impMin = min;
			impMax = max;
			System.out.println( "SET" );
			System.out.println( impMin + "  " + impMax );
		}
	}

	public void clearCache()
	{
		if ( loadercache != null )
		{
			loadercache.clearCache();
			System.runFinalization();
			System.gc();
		}
	}

	public class SetupImgLoader< T extends RealType< T > & NativeType< T > > implements BasicSetupImgLoader< UnsignedShortType >
	{
		final BasicSetupImgLoader< T > loader;

		protected SetupImgLoader( final BasicSetupImgLoader< T > loader )
		{
			this.loader = loader;
		}

		@Override
		public RandomAccessibleInterval< UnsignedShortType > getImage( final int timepointId )
		{
			if ( loadercache != null )
				loadercache.clearCache();
			final RandomAccessibleInterval< T > img = loader.getImage( timepointId );
			return Converters.convert( img, new RealUnsignedShortConverter< T >( impMin, impMax ), new UnsignedShortType() );
		}

		@Override
		public UnsignedShortType getImageType()
		{
			return new UnsignedShortType();
		}
	}

	@Override
	public SetupImgLoader< ? > getSetupImgLoader( final int setupId )
	{
		return setupImgLoaders.get( setupId );
	}
}
