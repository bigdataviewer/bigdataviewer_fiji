package bdv.ij;

import bdv.viewer.ConverterSetups;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerState;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.List;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.imagestack.ImageStackImageLoader;
import bdv.img.virtualstack.VirtualStackImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.process.LUT;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;

/**
 * ImageJ plugin to show the current image in BigDataViewer.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Open Current Image")
public class OpenImagePlusPlugIn implements Command
{
	public static void main( final String[] args )
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		new ImageJ();
		IJ.run( "Confocal Series (2.2MB)" );
//		IJ.run( "Fly Brain (1MB)" );
		new OpenImagePlusPlugIn().run();
	}

	@Override
	public void run()
	{
		if ( ij.Prefs.setIJMenuBar )
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		int nImages = WindowManager.getImageCount();
		// get the current image
		final ImagePlus curr = WindowManager.getCurrentImage();
		// make sure there is one
		if ( curr == null )
		{
			IJ.showMessage( "Please open an image first." );
			return;
		}

		ArrayList< ImagePlus > inputImgList = new ArrayList<>();
		if ( nImages > 1 )
		{
			final int[] idList = WindowManager.getIDList();
			final String[] nameList = new String[ nImages ];
			GenericDialog gd = new GenericDialog( "Images to open" );
			for ( int i = 0; i < nImages; i++ )
			{
				ImagePlus imp = WindowManager.getImage( idList[ i ] );
				nameList[ i ] = imp.getTitle();
				gd.addCheckbox( nameList[ i ], imp == curr );
			}

			gd.showDialog();
			if ( gd.wasCanceled() )
				return;

			for ( int i = 0; i < nImages; i++ )
			{
				if ( !gd.getNextBoolean() )
					continue;
				ImagePlus imp = WindowManager.getImage( idList[ i ] );
				inputImgList.add( imp );
			}
		}
		else
		{
			inputImgList.add( curr );
		}

		final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();

		CacheControl.CacheControls cache = new CacheControl.CacheControls();
		int nTimepoints = 1;
		int setup_id_offset = 0;
		ArrayList< ImagePlus > imgList = new ArrayList<>();
		for ( ImagePlus imp : inputImgList )
		{
			AbstractSpimData< ? > spimData = load( imp, converterSetups, sources, setup_id_offset );
			if ( spimData != null )
			{
				imgList.add( imp );
				cache.addCacheControl( ( ( ViewerImgLoader ) spimData.getSequenceDescription().getImgLoader() ).getCacheControl() );
				setup_id_offset += imp.getNChannels();
				nTimepoints = Math.max( nTimepoints, imp.getNFrames() );
			}
		}

		if ( !imgList.isEmpty() )
		{
			final BigDataViewer bdv = BigDataViewer.open( converterSetups, sources,
					nTimepoints, cache,
					"BigDataViewer", new ProgressWriterIJ(), ViewerOptions.options() );

			final SynchronizedViewerState state = bdv.getViewer().state();
			synchronized ( state )
			{
				int channelOffset = 0;
				int numActiveChannels = 0;
				for ( ImagePlus imp : imgList )
				{
					numActiveChannels += transferChannelVisibility( channelOffset, imp, state );
					transferChannelSettings( channelOffset, imp, state, bdv.getConverterSetups() );
					channelOffset += imp.getNChannels();
				}
				state.setDisplayMode( numActiveChannels > 1 ? DisplayMode.FUSED : DisplayMode.SINGLE );
			}
		}
	}

	protected AbstractSpimData< ? > load( ImagePlus imp, ArrayList< ConverterSetup > converterSetups, ArrayList< SourceAndConverter< ? > > sources,
			int setup_id_offset )
	{
		// check the image type
		switch ( imp.getType() )
		{
		case ImagePlus.GRAY8:
		case ImagePlus.GRAY16:
		case ImagePlus.GRAY32:
		case ImagePlus.COLOR_RGB:
			break;
		default:
			IJ.showMessage( imp.getShortTitle() + ": Only 8, 16, 32-bit images and RGB images are supported currently!" );
			return null;
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
		final FinalDimensions size = new FinalDimensions( w, h, d );

		// propose reasonable mipmap settings
//		final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );

		// create ImgLoader wrapping the image
		final BasicImgLoader imgLoader;
		if ( imp.getStack().isVirtual() )
		{
			switch ( imp.getType() )
			{
			case ImagePlus.GRAY8:
				imgLoader = VirtualStackImageLoader.createUnsignedByteInstance( imp, setup_id_offset );
				break;
			case ImagePlus.GRAY16:
				imgLoader = VirtualStackImageLoader.createUnsignedShortInstance( imp, setup_id_offset );
				break;
			case ImagePlus.GRAY32:
				imgLoader = VirtualStackImageLoader.createFloatInstance( imp, setup_id_offset );
				break;
			case ImagePlus.COLOR_RGB:
			default:
				imgLoader = VirtualStackImageLoader.createARGBInstance( imp, setup_id_offset );
				break;
			}
		}
		else
		{
			switch ( imp.getType() )
			{
			case ImagePlus.GRAY8:
				imgLoader = ImageStackImageLoader.createUnsignedByteInstance( imp, setup_id_offset );
				break;
			case ImagePlus.GRAY16:
				imgLoader = ImageStackImageLoader.createUnsignedShortInstance( imp, setup_id_offset );
				break;
			case ImagePlus.GRAY32:
				imgLoader = ImageStackImageLoader.createFloatInstance( imp, setup_id_offset );
				break;
			case ImagePlus.COLOR_RGB:
			default:
				imgLoader = ImageStackImageLoader.createARGBInstance( imp, setup_id_offset );
				break;
			}
		}

		final int numTimepoints = imp.getNFrames();
		final int numSetups = imp.getNChannels();

		// create setups from channels
		final HashMap< Integer, BasicViewSetup > setups = new HashMap<>( numSetups );
		for ( int s = 0; s < numSetups; ++s )
		{
			final BasicViewSetup setup = new BasicViewSetup( setup_id_offset + s, String.format( imp.getTitle() + " channel %d", s + 1 ), size, voxelSize );
			setup.setAttribute( new Channel( s + 1 ) );
			setups.put( setup_id_offset + s, setup );
		}

		// create timepoints
		final ArrayList< TimePoint > timepoints = new ArrayList<>( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );

		// create ViewRegistrations from the images calibration
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );
		final ArrayList< ViewRegistration > registrations = new ArrayList<>();
		for ( int t = 0; t < numTimepoints; ++t )
			for ( int s = 0; s < numSetups; ++s )
				registrations.add( new ViewRegistration( t, setup_id_offset + s, sourceTransform ) );

		final File basePath = new File( "." );
		final AbstractSpimData< ? > spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations( registrations ) );
		WrapBasicImgLoader.wrapImgLoaderIfNecessary( spimData );
		BigDataViewer.initSetups( spimData, converterSetups, sources );

		return spimData;
	}

	/**
	 * @return number of setups that were set active.
	 */
	protected int transferChannelVisibility( int channelOffset, final ImagePlus imp, final ViewerState state )
	{
		final int nChannels = imp.getNChannels();
		final CompositeImage ci = imp.isComposite() ? ( CompositeImage ) imp : null;
		final List< SourceAndConverter< ? > > sources = state.getSources();
		if ( ci != null && ci.getCompositeMode() == IJ.COMPOSITE )
		{
			final boolean[] activeChannels = ci.getActiveChannels();
			int numActiveChannels = 0;
			for ( int i = 0; i < Math.min( activeChannels.length, nChannels ); ++i )
			{
				final SourceAndConverter< ? > source = sources.get( channelOffset + i );
				state.setSourceActive( source, activeChannels[ i ] );
				state.setCurrentSource( source );
				numActiveChannels += activeChannels[ i ] ? 1 : 0;
			}
			return numActiveChannels;
		}
		else
		{
			final int activeChannel = imp.getChannel() - 1;
			for ( int i = 0; i < nChannels; ++i )
				state.setSourceActive( sources.get( channelOffset + i ), i == activeChannel );
			state.setCurrentSource( sources.get( channelOffset + activeChannel ) );
			return 1;
		}
	}

	protected void transferChannelSettings( int channelOffset, final ImagePlus imp, final ViewerState state, final ConverterSetups converterSetups )
	{
		final int nChannels = imp.getNChannels();
		final CompositeImage ci = imp.isComposite() ? ( CompositeImage ) imp : null;
		final List< SourceAndConverter< ? > > sources = state.getSources();
		if ( ci != null )
		{
			final int mode = ci.getCompositeMode();
			final boolean transferColor = mode == IJ.COMPOSITE || mode == IJ.COLOR;
			for ( int c = 0; c < nChannels; ++c )
			{
				final LUT lut = ci.getChannelLut( c + 1 );
				final ConverterSetup setup = converterSetups.getConverterSetup( sources.get( channelOffset + c ) );
				if ( transferColor )
					setup.setColor( new ARGBType( lut.getRGB( 255 ) ) );
				setup.setDisplayRange( lut.min, lut.max );
			}
		}
		else
		{
			final double displayRangeMin = imp.getDisplayRangeMin();
			final double displayRangeMax = imp.getDisplayRangeMax();
			for ( int i = 0; i < nChannels; ++i )
			{
				final ConverterSetup setup = converterSetups.getConverterSetup( sources.get( channelOffset + i ) );
				final LUT[] luts = imp.getLuts();
				if ( luts.length != 0 )
					setup.setColor( new ARGBType( luts[ 0 ].getRGB( 255 ) ) );
				setup.setDisplayRange( displayRangeMin, displayRangeMax );
			}
		}
	}
}
