package bdv.ij.export.imgloader;

import java.util.ArrayList;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.imagestack.ImageStackImageLoader;
import bdv.img.virtualstack.VirtualStackImageLoader;
import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.TypedBasicImgLoader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.RealUnsignedByteConverter;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * This {@link BasicImgLoader} implementation returns a wrapped, converted
 * {@link ImagePlus}. It is used for exporting {@link ImagePlus} to hdf5.
 *
 * Internally it relies on {@link VirtualStackImageLoader} to be able to handle
 * large virtual stacks.
 *
 * When loading images ({@link #getSetupImgLoader(int)},
 * {@link BasicSetupImgLoader#getImage(int, ImgLoaderHint...)}) the provided
 * setup id is used as the channel index of the {@link ImagePlus}, the provided
 * timepoint id is used as the frame index of the {@link ImagePlus}.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class ImagePlusImgLoader< T extends Type< T > > implements TypedBasicImgLoader< T >
{
	public static enum MinMaxOption
	{
		SET,
		COMPUTE,
		TAKE_FROM_IMAGEPROCESSOR
	}

	public static ImagePlusImgLoader< UnsignedByteType > createGray8( final ImagePlus imp, final MinMaxOption minMaxOption, final double min, final double max )
	{
		if( imp.getType() != ImagePlus.GRAY8 )
			throw new RuntimeException( "expected ImagePlus type GRAY8" );
		if ( imp.getStack() != null && imp.getStack().isVirtual() )
			return new ImagePlusImgLoader<>( imp, VirtualStackImageLoader.createUnsignedByteInstance( imp ), minMaxOption, min, max, new UnsignedByteType(), new RealUnsignedByteConverterFactory() );
		else
			return new ImagePlusImgLoader<>( imp, ImageStackImageLoader.createUnsignedByteInstance( imp ), minMaxOption, min, max, new UnsignedByteType(), new RealUnsignedByteConverterFactory() );
	}

	public static ImagePlusImgLoader< FloatType > createFloatFromGray8( final ImagePlus imp, final MinMaxOption minMaxOption, final double min, final double max )
	{
		if( imp.getType() != ImagePlus.GRAY8 )
			throw new RuntimeException( "expected ImagePlus type GRAY8" );
		if ( imp.getStack() != null && imp.getStack().isVirtual() )
			return new ImagePlusImgLoader<>( imp, VirtualStackImageLoader.createUnsignedByteInstance( imp ), minMaxOption, min, max, new FloatType(), new RealFloatConverterFactory() );
		else
			return new ImagePlusImgLoader<>( imp, ImageStackImageLoader.createUnsignedByteInstance( imp ), minMaxOption, min, max, new FloatType(), new RealFloatConverterFactory() );
	}

	public static ImagePlusImgLoader< UnsignedShortType > createGray16( final ImagePlus imp, final MinMaxOption minMaxOption, final double min, final double max )
	{
		if( imp.getType() != ImagePlus.GRAY16 )
			throw new RuntimeException( "expected ImagePlus type GRAY16" );
		if ( imp.getStack() != null && imp.getStack().isVirtual() )
			return new ImagePlusImgLoader<>( imp, VirtualStackImageLoader.createUnsignedShortInstance( imp ), minMaxOption, min, max, new UnsignedShortType(), new RealUnsignedShortConverterFactory() );
		else
			return new ImagePlusImgLoader<>( imp, ImageStackImageLoader.createUnsignedShortInstance( imp ), minMaxOption, min, max, new UnsignedShortType(), new RealUnsignedShortConverterFactory() );
	}

	public static ImagePlusImgLoader< FloatType > createGray32( final ImagePlus imp, final MinMaxOption minMaxOption, final double min, final double max )
	{
		if( imp.getType() != ImagePlus.GRAY32 )
			throw new RuntimeException( "expected ImagePlus type GRAY32" );
		if ( imp.getStack() != null && imp.getStack().isVirtual() )
			return new ImagePlusImgLoader<>( imp, VirtualStackImageLoader.createFloatInstance( imp ), minMaxOption, min, max, new FloatType(), new RealFloatConverterFactory() );
		else
			return new ImagePlusImgLoader<>( imp, ImageStackImageLoader.createFloatInstance( imp ), minMaxOption, min, max, new FloatType(), new RealFloatConverterFactory() );
	}

	protected final ImagePlus imp;

	protected final BasicImgLoader loader;

	protected VolatileGlobalCellCache loadercache;

	protected final ArrayList< SetupImgLoader< ? > > setupImgLoaders;

	protected double impMin;

	protected double impMax;

	protected final T type;
	public String reportPixelType()
	{ return type.getClass().getSimpleName(); }

	protected final ConverterFactory< T > converterFactory;

	public interface ConverterFactory< T >
	{
		public < S extends RealType< S > & NativeType< S > > Converter< S, T > create( double min, double max );
	}

	static class RealUnsignedByteConverterFactory implements ConverterFactory< UnsignedByteType >
	{
		@Override
		public < S extends RealType< S > & NativeType< S > > Converter< S, UnsignedByteType > create( final double min, final double max )
		{
			return new RealUnsignedByteConverter<>( min, max );
		}
	}

	static class RealUnsignedShortConverterFactory implements ConverterFactory< UnsignedShortType >
	{
		@Override
		public < S extends RealType< S > & NativeType< S > > Converter< S, UnsignedShortType > create( final double min, final double max )
		{
			return new RealUnsignedShortConverter<>( min, max );
		}
	}

	static class RealFloatConverterFactory implements ConverterFactory< FloatType >
	{
		@Override
		public < S extends RealType< S > & NativeType< S > > Converter< S, FloatType > create( final double min, final double max )
		{
			return new RealFloatConverter<>();
		}
	}

	protected < S extends RealType< S > & NativeType< S > > ImagePlusImgLoader( final ImagePlus imp,
			final TypedBasicImgLoader< S > loader,
			final MinMaxOption minMaxOption,
			final double min,
			final double max,
			final T type,
			final ConverterFactory< T > converterFactory )
	{
		this.imp = imp;
		this.loader = loader;
		this.type = type;
		this.converterFactory = converterFactory;

		final int numSetups = imp.getNChannels();
		setupImgLoaders = new ArrayList<>();
		for ( int setupId = 0; setupId < numSetups; ++setupId )
			setupImgLoaders.add( new SetupImgLoader<>( loader.getSetupImgLoader( setupId ) ) );

		if ( loader instanceof VirtualStackImageLoader )
			this.loadercache = ( ( VirtualStackImageLoader< ?, ?, ? > ) loader ).getCacheControl();
		else
			this.loadercache = null;

		if ( minMaxOption == MinMaxOption.COMPUTE )
		{
			impMin = Double.POSITIVE_INFINITY;
			impMax = Double.NEGATIVE_INFINITY;
			final S minT = loader.getSetupImgLoader( 0 ).getImageType().createVariable();
			final S maxT = minT.createVariable();
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

	public class SetupImgLoader< S extends RealType< S > & NativeType< S > > implements BasicSetupImgLoader< T >
	{
		final BasicSetupImgLoader< S > loader;

		protected SetupImgLoader( final BasicSetupImgLoader< S > loader )
		{
			this.loader = loader;
		}

		@Override
		public RandomAccessibleInterval< T > getImage( final int timepointId, final ImgLoaderHint... hints )
		{
			if ( loadercache != null )
				loadercache.clearCache();
			final RandomAccessibleInterval< S > img = loader.getImage( timepointId );
			return Converters.convert( img, converterFactory.< S >create( impMin, impMax ), type.createVariable() );
		}

		@Override
		public T getImageType()
		{
			return type;
		}
	}

	@Override
	public SetupImgLoader< ? > getSetupImgLoader( final int setupId )
	{
		return setupImgLoaders.get( setupId );
	}
}
