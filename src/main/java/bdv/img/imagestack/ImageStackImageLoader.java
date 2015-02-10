package bdv.img.imagestack;

import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

public abstract class ImageStackImageLoader< T extends NumericType< T > & NativeType< T >, A extends ArrayDataAccess< A > > implements BasicImgLoader< T >
{
	public static ImageStackImageLoader< UnsignedByteType, ByteArray > createUnsignedByteInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader< UnsignedByteType, ByteArray >( new UnsignedByteType(), imp )
		{
			@Override
			protected ByteArray wrapPixels( final Object array )
			{
				return new ByteArray( ( byte[] ) array );
			}

			@Override
			protected void linkType( final PlanarImg< UnsignedByteType, ByteArray > img )
			{
				img.setLinkedType( new UnsignedByteType( img ) );
			}
		};
	}

	public static ImageStackImageLoader< UnsignedShortType, ShortArray > createUnsignedShortInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader< UnsignedShortType, ShortArray >( new UnsignedShortType(), imp )
		{
			@Override
			protected ShortArray wrapPixels( final Object array )
			{
				return new ShortArray( ( short[] ) array );
			}

			@Override
			protected void linkType( final PlanarImg< UnsignedShortType, ShortArray > img )
			{
				img.setLinkedType( new UnsignedShortType( img ) );
			}
		};
	}

	public static ImageStackImageLoader< FloatType, FloatArray > createFloatInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader< FloatType, FloatArray >( new FloatType(), imp )
		{
			@Override
			protected FloatArray wrapPixels( final Object array )
			{
				return new FloatArray( ( float[] ) array );
			}

			@Override
			protected void linkType( final PlanarImg< FloatType, FloatArray > img )
			{
				img.setLinkedType( new FloatType( img ) );
			}
		};
	}

	public static ImageStackImageLoader< ARGBType, IntArray > createARGBInstance( final ImagePlus imp )
	{
		return new ImageStackImageLoader< ARGBType, IntArray >( new ARGBType(), imp )
		{
			@Override
			protected IntArray wrapPixels( final Object array )
			{
				return new IntArray( ( int[] ) array );
			}

			@Override
			protected void linkType( final PlanarImg< ARGBType, IntArray > img )
			{
				img.setLinkedType( new ARGBType( img ) );
			}
		};
	}

	private final T type;

	private final ImagePlus imp;

	private final long[] dim;

	public ImageStackImageLoader( final T type, final ImagePlus imp )
	{
		this.type = type;
		this.imp = imp;
		this.dim = new long[] { imp.getWidth(), imp.getHeight(), imp.getNSlices() };
	}

	protected abstract A wrapPixels( Object array );

	protected abstract void linkType( PlanarImg< T, A > img );

	@Override
	public RandomAccessibleInterval< T > getImage( final ViewId view )
	{
		return new PlanarImg< T, A >( dim, type.getEntitiesPerPixel() )
		{
			private PlanarImg< T, A > init()
			{
				final int channel = view.getViewSetupId() + 1;
				final int frame = view.getTimePointId() + 1;
				for ( int slice = 1; slice <= dim[ 2 ]; ++slice )
					mirror.set( slice - 1, wrapPixels( imp.getStack().getPixels( imp.getStackIndex( channel, slice, frame ) ) ) );
				linkType( this );
				return this;
			}
		}.init();
	}

	@Override
	public T getImageType()
	{
		return type;
	}
}