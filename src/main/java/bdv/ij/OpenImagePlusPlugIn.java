package bdv.ij;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.virtualstack.VirtualStackImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.WrapBasicImgLoader;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * ImageJ plugin to show the current image in BigDataViewer.
 *
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
public class OpenImagePlusPlugIn implements PlugIn
{
	public static void main( final String[] args )
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		new ImageJ();
		IJ.run("Confocal Series (2.2MB)");
//		IJ.run("Fly Brain (1MB)");
		new OpenImagePlusPlugIn().run( null );
	}

	@Override
	public void run( final String arg )
	{
		// get the current image
		final ImagePlus imp = WindowManager.getCurrentImage();

		// make sure there is one
		if ( imp == null )
		{
			IJ.showMessage( "Please open an image first." );
			return;
		}

		// check the image type
		switch ( imp.getType() )
		{
		case ImagePlus.GRAY8:
		case ImagePlus.GRAY16:
		case ImagePlus.GRAY32:
		case ImagePlus.COLOR_RGB:
			break;
		default:
			IJ.showMessage( "Only 8, 16, 32-bit images and RGB images are supported currently!" );
			return;
		}

		// check the image dimensionality
		if ( imp.getNDimensions() < 3 )
		{
			IJ.showMessage( "Image must be at least 3-dimensional!" );
			return;
		}

		// get calibration and image size
		final double pw = imp.getCalibration().pixelWidth;
		final double ph = imp.getCalibration().pixelHeight;
		final double pd = imp.getCalibration().pixelDepth;
		String punit = imp.getCalibration().getUnit();
		if ( punit == null || punit.isEmpty() )
			punit = "px";
		final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pw, ph, pd );
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getNSlices();
		final FinalDimensions size = new FinalDimensions( new int[] { w, h, d } );

		// propose reasonable mipmap settings
//		final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );

//		imp.getDisplayRangeMin();
//		imp.getDisplayRangeMax();

		// create ImgLoader wrapping the image
		final BasicImgLoader< ? > imgLoader;
		if ( imp.getStack().isVirtual() )
		{
			switch ( imp.getType() )
			{
			case ImagePlus.GRAY8:
				imgLoader = VirtualStackImageLoader.createUnsignedByteInstance( imp );
				break;
			case ImagePlus.GRAY16:
				imgLoader = VirtualStackImageLoader.createUnsignedShortInstance( imp );
				break;
			case ImagePlus.GRAY32:
				imgLoader = VirtualStackImageLoader.createFloatInstance( imp );
				break;
			case ImagePlus.COLOR_RGB:
			default:
				imgLoader = VirtualStackImageLoader.createARGBInstance( imp );
				break;
			}
		}
		else
		{
			switch ( imp.getType() )
			{
			case ImagePlus.GRAY8:
				imgLoader = ImagePlusImgLoader.createUnsignedByteInstance( imp );
				break;
			case ImagePlus.GRAY16:
				imgLoader = ImagePlusImgLoader.createUnsignedShortInstance( imp );
				break;
			case ImagePlus.GRAY32:
				imgLoader = ImagePlusImgLoader.createFloatInstance( imp );
				break;
			case ImagePlus.COLOR_RGB:
			default:
				imgLoader = ImagePlusImgLoader.createARGBInstance( imp );
				break;
			}
		}

		final int numTimepoints = imp.getNFrames();
		final int numSetups = imp.getNChannels();

		// create setups from channels
		final HashMap< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >( numSetups );
		for ( int s = 0; s < numSetups; ++s )
		{
			final BasicViewSetup setup = new BasicViewSetup( s, String.format( "channel %d", s + 1 ), size, voxelSize );
			setup.setAttribute( new Channel( s + 1 ) );
			setups.put( s, setup );
		}

		// create timepoints
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );

		// create ViewRegistrations from the images calibration
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );
		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();
		for ( int t = 0; t < numTimepoints; ++t )
			for ( int s = 0; s < numSetups; ++s )
				registrations.add( new ViewRegistration( t, s, sourceTransform ) );

		final File basePath = new File(".");
		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations( registrations ) );
		WrapBasicImgLoader.wrapImgLoaderIfNecessary( spimData );

		try
		{
			new BigDataViewer( spimData, "BigDataViewer", new ProgressWriterIJ() );
		}
		catch ( final SpimDataException e )
		{
			throw new RuntimeException( e );
		}
	}

	private static abstract class ImagePlusImgLoader< T extends NumericType< T > & NativeType< T >, A extends ArrayDataAccess< A > > implements BasicImgLoader< T >
	{
		private final T type;

		private final ImagePlus imp;

		private final long[] dim;

		public ImagePlusImgLoader( final T type, final ImagePlus imp )
		{
			this.type = type;
			this.imp = imp;
			this.dim = new long[] { imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		}

		protected abstract A wrap( Object array );

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
						mirror.set( slice - 1, wrap( imp.getStack().getPixels( imp.getStackIndex( channel, slice, frame ) ) ) );
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

		public static ImagePlusImgLoader< UnsignedByteType, ByteArray > createUnsignedByteInstance( final ImagePlus imp )
		{
			return new ImagePlusImgLoader< UnsignedByteType, ByteArray >( new UnsignedByteType(), imp )
			{
				@Override
				protected ByteArray wrap( final Object array )
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

		public static ImagePlusImgLoader< UnsignedShortType, ShortArray > createUnsignedShortInstance( final ImagePlus imp )
		{
			return new ImagePlusImgLoader< UnsignedShortType, ShortArray >( new UnsignedShortType(), imp )
			{
				@Override
				protected ShortArray wrap( final Object array )
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

		public static ImagePlusImgLoader< FloatType, FloatArray > createFloatInstance( final ImagePlus imp )
		{
			return new ImagePlusImgLoader< FloatType, FloatArray >( new FloatType(), imp )
			{
				@Override
				protected FloatArray wrap( final Object array )
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

		public static ImagePlusImgLoader< ARGBType, IntArray > createARGBInstance( final ImagePlus imp )
		{
			return new ImagePlusImgLoader< ARGBType, IntArray >( new ARGBType(), imp )
			{
				@Override
				protected IntArray wrap( final Object array )
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
	}
}
