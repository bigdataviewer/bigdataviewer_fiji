package bdv.ij.export.imgloader;

import java.util.HashMap;
import java.util.Map.Entry;

import ij.ImagePlus;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * This {@link ImgLoader} loads images that represent a 3D stack as a sequence
 * of slice with one image file per slice, such as created by Stephan
 * Preibisch's Multi-view fusion plugin. It is constructed with the pattern of
 * the image filenames. Then, to laod the image for a given
 * {@link ViewDescription}, its TODO timepoint? index?, channel, and slice
 * indices are filled into the template to get the slice filenames.
 *
 * This {@link ImgLoader} is used for exporting spim sequences to hdf5. Only the
 * {@link BasicSetupImgLoader#getImage(int, ImgLoaderHint...)} method is
 * implemented because this is the only method required for exporting to hdf5.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class FusionImageLoader< T extends RealType< T > > implements ImgLoader
{
	private final String pattern;

	private final int numSlices;

	private final SliceLoader< T > sliceLoader;

	private final RealUnsignedShortConverter< T > converter;

	private final ImgFactory< UnsignedShortType > factory;

	private final UnsignedShortType type;

	private final HashMap< Integer, SetupLoader > setupIdToSetupImgLoader;

	public FusionImageLoader( final String pattern, final HashMap< Integer, Integer > setupIdToChannelId, final int numSlices, final SliceLoader< T > sliceLoader, final double sliceValueMin, final double sliceValueMax )
	{
		this( pattern, setupIdToChannelId, numSlices, sliceLoader, sliceValueMin, sliceValueMax, new PlanarImgFactory< UnsignedShortType >() );
	}

	public FusionImageLoader( final String pattern, final HashMap< Integer, Integer > setupIdToChannelId, final int numSlices, final SliceLoader< T > sliceLoader, final double sliceValueMin, final double sliceValueMax, final ImgFactory< UnsignedShortType > factory )
	{
		this.pattern = pattern;
		this.numSlices = numSlices;
		this.sliceLoader = sliceLoader;
		converter = new RealUnsignedShortConverter<>( sliceValueMin, sliceValueMax );
		this.factory = factory;
		type = new UnsignedShortType();
		setupIdToSetupImgLoader = new HashMap<>();
		for ( final Entry< Integer, Integer > entry : setupIdToChannelId.entrySet() )
			setupIdToSetupImgLoader.put( entry.getKey(), new SetupLoader( entry.getValue() ) );
	}

	public static interface SliceLoader< T >
	{
		public RandomAccessibleInterval< T > load( String fn );
	}

	public static class ArrayImgLoader< T extends RealType< T > & NativeType< T > > implements SliceLoader< T >
	{
		final ImgOpener opener;

		final ArrayImgFactory< T > factory;

		public ArrayImgLoader( final T type )
		{
			opener = new ImgOpener();
			factory = new ArrayImgFactory<>( type );
		}

		@Override
		public RandomAccessibleInterval< T > load( final String fn )
		{
			try
			{
				System.out.println( fn );
				return opener.openImgs( fn, factory ).get( 0 );
			}
			catch ( final ImgIOException e )
			{
				e.printStackTrace();
			}
			return null;
		}
	}

	public static class Gray32ImagePlusLoader implements SliceLoader< FloatType >
	{
		@Override
		public RandomAccessibleInterval< FloatType > load( final String fn )
		{
			return ImageJFunctions.wrapFloat( new ImagePlus( fn ) );
		}
	}

	public static class Gray16ImagePlusLoader implements SliceLoader< UnsignedShortType >
	{
		@Override
		public RandomAccessibleInterval< UnsignedShortType > load( final String fn )
		{
			return ImageJFunctions.wrapShort( new ImagePlus( fn ) );
		}
	}

	public static class Gray8ImagePlusLoader implements SliceLoader< UnsignedByteType >
	{
		@Override
		public RandomAccessibleInterval< UnsignedByteType > load( final String fn )
		{
			return ImageJFunctions.wrapByte( new ImagePlus( fn ) );
		}
	}

	@Override
	public SetupLoader getSetupImgLoader( final int setupId )
	{
		return setupIdToSetupImgLoader.get( setupId );
	}

	public class SetupLoader implements SetupImgLoader< UnsignedShortType >
	{
		private final int channelId;

		protected SetupLoader( final int channelId )
		{
			this.channelId = channelId;
		}

		@Override
		public RandomAccessibleInterval< UnsignedShortType > getImage( final int timepointId, final ImgLoaderHint... hints )
		{
			final Dimensions dimensions = getImageSize( timepointId );
			final Img< UnsignedShortType > img = factory.create( dimensions, type );

			for ( int z = 0; z < numSlices; ++z )
			{
				final RandomAccessibleInterval< T > slice = sliceLoader.load( String.format( pattern, timepointId, channelId, z ) );

				final Cursor< UnsignedShortType > d = Views.flatIterable( Views.hyperSlice( img, 2, z ) ).cursor();
				for ( final UnsignedShortType t : Converters.convert( Views.flatIterable( slice ), converter, type ) )
					d.next().set( t );
			}
			return img;
		}

		@Override
		public UnsignedShortType getImageType()
		{
			return type;
		}

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, final boolean normalize, final ImgLoaderHint... hints )
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Dimensions getImageSize( final int timepointId )
		{
			final RandomAccessibleInterval< T > slice = sliceLoader.load( String.format( pattern, timepointId, channelId, 0 ) );
			return new FinalDimensions(
					slice.dimension( 0 ),
					slice.dimension( 1 ),
					numSlices );
		}

		@Override
		public VoxelDimensions getVoxelSize( final int timepointId )
		{
			return new FinalVoxelDimensions( "px", 1, 1, 1 );
		}
	}
}
