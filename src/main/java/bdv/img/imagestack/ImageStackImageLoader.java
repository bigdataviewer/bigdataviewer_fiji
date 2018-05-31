package bdv.img.imagestack;

import java.util.ArrayList;
import java.util.function.Function;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.TypedBasicImgLoader;

public class ImageStackImageLoader< T extends NumericType< T > & NativeType< T >, A extends ArrayDataAccess< A > > implements BasicImgLoader, TypedBasicImgLoader< T >
{
	public static ImageStackImageLoader< UnsignedByteType, ByteArray > createUnsignedByteInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader<>( new UnsignedByteType(), imp, array -> new ByteArray( ( byte[] ) array ) );
	}

	public static ImageStackImageLoader< UnsignedShortType, ShortArray > createUnsignedShortInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader<>( new UnsignedShortType(), imp, array -> new ShortArray( ( short[] ) array ) );
	}

	public static ImageStackImageLoader< FloatType, FloatArray > createFloatInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader<>( new FloatType(), imp, array -> new FloatArray( ( float[] ) array ) );
	}

	public static ImageStackImageLoader< ARGBType, IntArray > createARGBInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader<>( new ARGBType(), imp, array -> new IntArray( ( int[] ) array ) );
	}

	private final T type;

	private final ImagePlus imp;

	private final long[] dim;

	private final ArrayList< SetupImgLoader > setupImgLoaders;

	private final Function< Object, A > wrapPixels;

	public ImageStackImageLoader( final T type, final ImagePlus imp, final Function< Object, A > wrapPixels )
	{
		this.type = type;
		this.imp = imp;
		this.wrapPixels = wrapPixels;
		this.dim = new long[] { imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		final int numSetups = imp.getNChannels();
		setupImgLoaders = new ArrayList<>();
		for ( int setupId = 0; setupId < numSetups; ++setupId )
			setupImgLoaders.add( new SetupImgLoader( setupId ) );
	}

	public class SetupImgLoader implements BasicSetupImgLoader< T >
	{
		private final int setupId;

		public SetupImgLoader( final int setupId )
		{
			this.setupId = setupId;
		}

		@Override
		public RandomAccessibleInterval< T > getImage( final int timepointId, final ImgLoaderHint... hints )
		{
			final int channel = setupId + 1;
			final int frame = timepointId + 1;
			final ArrayList< A > slices = new ArrayList<>();
			for ( int slice = 1; slice <= dim[ 2 ]; ++slice )
				slices.add( wrapPixels.apply( imp.getStack().getPixels( imp.getStackIndex( channel, slice, frame ) ) ) );
			final PlanarImg< T, A > img = new PlanarImg<>( slices, dim, type.getEntitiesPerPixel() );
			@SuppressWarnings( "unchecked" )
			final NativeTypeFactory< T, ? super A > typeFactory = ( NativeTypeFactory< T, ? super A > ) type.getNativeTypeFactory();
			img.setLinkedType( typeFactory.createLinkedType( img ) );
			return img;
		}

		@Override
		public T getImageType()
		{
			return type;
		}
	}

	@Override
	public SetupImgLoader getSetupImgLoader( final int setupId )
	{
		return setupImgLoaders.get( setupId );
	}
}
