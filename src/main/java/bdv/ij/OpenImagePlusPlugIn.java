package bdv.ij;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.imagestack.ImageStackImageLoader;
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
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;

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
				imgLoader = ImageStackImageLoader.createUnsignedByteInstance( imp );
				break;
			case ImagePlus.GRAY16:
				imgLoader = ImageStackImageLoader.createUnsignedShortInstance( imp );
				break;
			case ImagePlus.GRAY32:
				imgLoader = ImageStackImageLoader.createFloatInstance( imp );
				break;
			case ImagePlus.COLOR_RGB:
			default:
				imgLoader = ImageStackImageLoader.createARGBInstance( imp );
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
}
