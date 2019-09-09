package bdv.ij;

import bdv.export.ExportMipmapInfo;
import bdv.export.ExportScalePyramid.AfterEachPlane;
import bdv.export.ExportScalePyramid.LoopbackHeuristic;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.SubTaskProgressWriter;
import bdv.export.n5.WriteSequenceToN5;
import bdv.ij.util.PluginHelper;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.imagestack.ImageStackImageLoader;
import bdv.img.n5.N5ImageLoader;
import bdv.img.virtualstack.VirtualStackImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import java.awt.Checkbox;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.TypedBasicImgLoader;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.Lz4Compression;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

/**
 * ImageJ plugin to export the current image to xml/n5.
 *
 * @author Tobias Pietzsch
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Export Current Image as XML/N5")
public class ExportImagePlusAsN5PlugIn implements Command
{
	public static void main( final String[] args )
	{
		new ImageJ();
		final ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/data/confocal-series.tif" );
		imp.show();
		new ExportImagePlusAsN5PlugIn().run();
	}

	@Override
	public void run()
	{
		if ( ij.Prefs.setIJMenuBar )
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

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
			break;
		default:
			IJ.showMessage( "Only 8, 16, 32-bit images are supported currently!" );
			return;
		}

		// check the image dimensionality
		if ( imp.getNDimensions() < 2 )
		{
			IJ.showMessage( "Image must be at least 2-dimensional!" );
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
		final FinalDimensions size = new FinalDimensions( w, h, d );

		// propose reasonable mipmap settings
		final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );

		// show dialog to get output paths, resolutions, subdivisions, min-max option
		final Parameters params = getParameters( autoMipmapSettings );
		if ( params == null )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// create ImgLoader wrapping the image
		final TypedBasicImgLoader< ? > imgLoader;
		final Runnable clearCache;
		final boolean isVirtual = imp.getStack() != null && imp.getStack().isVirtual();
		if ( isVirtual )
		{
			final VirtualStackImageLoader< ?, ?, ? > il;
			switch ( imp.getType() )
			{
			case ImagePlus.GRAY8:
				il = VirtualStackImageLoader.createUnsignedByteInstance( imp );
				break;
			case ImagePlus.GRAY16:
				il = VirtualStackImageLoader.createUnsignedShortInstance( imp );
				break;
			case ImagePlus.GRAY32:
			default:
				il = VirtualStackImageLoader.createFloatInstance( imp );
				break;
			}
			imgLoader = il;
			clearCache = il.getCacheControl()::clearCache;
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
			default:
				imgLoader = ImageStackImageLoader.createFloatInstance( imp );
				break;
			}
			clearCache = () -> {};
		}

		final int numTimepoints = imp.getNFrames();
		final int numSetups = imp.getNChannels();

		// create SourceTransform from the images calibration
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );

		// write n5
		final HashMap< Integer, BasicViewSetup > setups = new HashMap<>( numSetups );
		for ( int s = 0; s < numSetups; ++s )
		{
			final BasicViewSetup setup = new BasicViewSetup( s, String.format( "channel %d", s + 1 ), size, voxelSize );
			setup.setAttribute( new Channel( s + 1 ) );
			setups.put( s, setup );
		}
		final ArrayList< TimePoint > timepoints = new ArrayList<>( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( t ) );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );

		Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo;
		perSetupExportMipmapInfo = new HashMap<>();
		final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( params.resolutions, params.subdivisions );
		for ( final BasicViewSetup setup : seq.getViewSetupsOrdered() )
			perSetupExportMipmapInfo.put( setup.getId(), mipmapInfo );

		// LoopBackHeuristic:
		// - If saving more than 8x on pixel reads use the loopback image over
		//   original image
		// - For virtual stacks also consider the cache size that would be
		//   required for all original planes contributing to a "plane of
		//   blocks" at the current level. If this is more than 1/4 of
		//   available memory, use the loopback image.
		final long planeSizeInBytes = imp.getWidth() * imp.getHeight() * imp.getBytesPerPixel();
		final long ijMaxMemory = IJ.maxMemory();
		final int numCellCreatorThreads = Math.max( 1, PluginHelper.numThreads() - 1 );
		final LoopbackHeuristic loopbackHeuristic = new LoopbackHeuristic()
		{
			@Override
			public boolean decide( final RandomAccessibleInterval< ? > originalImg, final int[] factorsToOriginalImg, final int previousLevel, final int[] factorsToPreviousLevel, final int[] chunkSize )
			{
				if ( previousLevel < 0 )
					return false;

				if ( Intervals.numElements( factorsToOriginalImg ) / Intervals.numElements( factorsToPreviousLevel ) >= 8 )
					return true;

				if ( isVirtual )
				{
					final long requiredCacheSize = planeSizeInBytes * factorsToOriginalImg[ 2 ] * chunkSize[ 2 ];
					if ( requiredCacheSize > ijMaxMemory / 4 )
						return true;
				}

				return false;
			}
		};

		final AfterEachPlane afterEachPlane = new AfterEachPlane()
		{
			@Override
			public void afterEachPlane( final boolean usedLoopBack )
			{
				if ( !usedLoopBack && isVirtual )
				{
					final long free = Runtime.getRuntime().freeMemory();
					final long total = Runtime.getRuntime().totalMemory();
					final long max = Runtime.getRuntime().maxMemory();
					final long actuallyFree = max - total + free;

					if ( actuallyFree < max / 2 )
						clearCache.run();
				}
			}

		};

		try
		{
			Compression compression = params.deflate ? new Lz4Compression() : new RawCompression();
			WriteSequenceToN5.writeN5File( seq, perSetupExportMipmapInfo, compression, params.n5File, loopbackHeuristic, afterEachPlane, numCellCreatorThreads, new SubTaskProgressWriter( progressWriter, 0, 0.95 ) );

			// write xml sequence description
			final N5ImageLoader n5Loader = new N5ImageLoader( params.n5File, null );
			final SequenceDescriptionMinimal seqh5 = new SequenceDescriptionMinimal( seq, n5Loader );

			final ArrayList< ViewRegistration > registrations = new ArrayList<>();
			for ( int t = 0; t < numTimepoints; ++t )
				for ( int s = 0; s < numSetups; ++s )
					registrations.add( new ViewRegistration( t, s, sourceTransform ) );

			final File basePath = params.seqFile.getParentFile();
			final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seqh5, new ViewRegistrations( registrations ) );

			new XmlIoSpimDataMinimal().save( spimData, params.seqFile.getAbsolutePath() );
			progressWriter.setProgress( 1.0 );
		}
		catch ( final SpimDataException | IOException e )
		{
			throw new RuntimeException( e );
		}
		progressWriter.out().println( "done" );
	}

	protected static class Parameters
	{
		final boolean setMipmapManual;

		final int[][] resolutions;

		final int[][] subdivisions;

		final File seqFile;

		final File n5File;

		final boolean deflate;

		public Parameters(
				final boolean setMipmapManual, final int[][] resolutions, final int[][] subdivisions,
				final File seqFile, final File n5File,
				final boolean deflate )
		{
			this.setMipmapManual = setMipmapManual;
			this.resolutions = resolutions;
			this.subdivisions = subdivisions;
			this.seqFile = seqFile;
			this.n5File = n5File;
			this.deflate = deflate;
		}
	}

	static boolean lastSetMipmapManual = false;

	static String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";

	static String lastChunkSizes = "{32,32,4}, {16,16,8}, {8,8,8}";

	static boolean lastDeflate = true;

	static String lastExportPath = "./export.xml";

	protected Parameters getParameters( final ExportMipmapInfo autoMipmapSettings  )
	{
		while ( true )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "Export for BigDataViewer as XML/N5" );

			gd.addCheckbox( "manual_mipmap_setup", lastSetMipmapManual );
			final Checkbox cManualMipmap = ( Checkbox ) gd.getCheckboxes().lastElement();
			gd.addStringField( "Subsampling_factors", lastSubsampling, 25 );
			final TextField tfSubsampling = ( TextField ) gd.getStringFields().lastElement();
			gd.addStringField( "N5_chunk_sizes", lastChunkSizes, 25 );
			final TextField tfChunkSizes = ( TextField ) gd.getStringFields().lastElement();

			gd.addMessage( "" );
			gd.addCheckbox( "use_deflate_compression", lastDeflate );

			gd.addMessage( "" );
			PluginHelper.addSaveAsFileField( gd, "Export_path", lastExportPath, 25 );

			final String autoSubsampling = ProposeMipmaps.getArrayString( autoMipmapSettings.getExportResolutions() );
			final String autoChunkSizes = ProposeMipmaps.getArrayString( autoMipmapSettings.getSubdivisions() );
			gd.addDialogListener( ( dialog, e ) -> {
				gd.getNextBoolean();
				gd.getNextString();
				gd.getNextString();
				gd.getNextBoolean();
				gd.getNextString();
				if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == cManualMipmap )
				{
					final boolean useManual = cManualMipmap.getState();
					tfSubsampling.setEnabled( useManual );
					tfChunkSizes.setEnabled( useManual );
					if ( !useManual )
					{
						tfSubsampling.setText( autoSubsampling );
						tfChunkSizes.setText( autoChunkSizes );
					}
				}
				return true;
			} );

			tfSubsampling.setEnabled( lastSetMipmapManual );
			tfChunkSizes.setEnabled( lastSetMipmapManual );
			if ( !lastSetMipmapManual )
			{
				tfSubsampling.setText( autoSubsampling );
				tfChunkSizes.setText( autoChunkSizes );
			}

			gd.showDialog();
			if ( gd.wasCanceled() )
				return null;

			lastSetMipmapManual = gd.getNextBoolean();
			lastSubsampling = gd.getNextString();
			lastChunkSizes = gd.getNextString();
			lastDeflate = gd.getNextBoolean();
			lastExportPath = gd.getNextString();

			// parse mipmap resolutions and cell sizes
			final int[][] resolutions = PluginHelper.parseResolutionsString( lastSubsampling );
			final int[][] subdivisions = PluginHelper.parseResolutionsString( lastChunkSizes );
			if ( resolutions.length == 0 )
			{
				IJ.showMessage( "Cannot parse subsampling factors " + lastSubsampling );
				continue;
			}
			if ( subdivisions.length == 0 )
			{
				IJ.showMessage( "Cannot parse n5 chunk sizes " + lastChunkSizes );
				continue;
			}
			else if ( resolutions.length != subdivisions.length )
			{
				IJ.showMessage( "subsampling factors and n5 chunk sizes must have the same number of elements" );
				continue;
			}

			String seqFilename = lastExportPath;
			if ( !seqFilename.endsWith( ".xml" ) )
				seqFilename += ".xml";
			final File seqFile = new File( seqFilename );
			final File parent = seqFile.getParentFile();
			if ( parent == null || !parent.exists() || !parent.isDirectory() )
			{
				IJ.showMessage( "Invalid export filename " + seqFilename );
				continue;
			}
			final String n5Filename = seqFilename.substring( 0, seqFilename.length() - 4 ) + ".n5";
			final File n5File = new File( n5Filename );

			return new Parameters( lastSetMipmapManual, resolutions, subdivisions, seqFile, n5File, lastDeflate );
		}
	}
}
