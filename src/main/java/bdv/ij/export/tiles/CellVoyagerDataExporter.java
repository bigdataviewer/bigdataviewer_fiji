package bdv.ij.export.tiles;

import ij.IJ;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import bdv.export.ProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.ij.util.PluginHelper;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;

public class CellVoyagerDataExporter
{

	// private static final String SETTINGS_FILENAME = "MeasurementSetting.xml";

	private static final String CHANNELS_ELEMENT = "Channels";

	private Document document;

	private final File measurementSettingFile;

	private final File imageIndexFile;

	/**
	 * Creates a new exporter that will browse the specified measurement folder
	 * to build the data to export.
	 *
	 * @param measurementSettingFile
	 *            the CellVoyager measurement file to parse. It must be a XML
	 *            file containing the information on the acquisition to export.
	 *            This file is typically named
	 *            <code>MeasurementSetting.xml</code>
	 * @param imageIndexFile
	 *            the CellVoyager image index file. This XML file contains the
	 *            path to and information on each individual image needed to
	 *            rebuild the whole dataset. The file is generally in the same
	 *            folder that og the measurement setting file and named
	 *            <code>ImageIndex.xml</code>.
	 *
	 */
	public CellVoyagerDataExporter( final File measurementSettingFile, final File imageIndexFile )
	{
		this.measurementSettingFile = measurementSettingFile;
		this.imageIndexFile = imageIndexFile;

		if ( !measurementSettingFile.exists() ) { throw new IllegalArgumentException( "The target file " + measurementSettingFile + " does not exist." ); }
		if ( !measurementSettingFile.isFile() ) { throw new IllegalArgumentException( "The target file " + measurementSettingFile + " is not a file." ); }

		final SAXBuilder builder = new SAXBuilder();
		try
		{
			document = builder.build( measurementSettingFile );
		}
		catch ( final JDOMException e )
		{
			throw new IllegalArgumentException( "The target file " + measurementSettingFile + " is malformed:\n" + e.getMessage() );
		}
		catch ( final IOException e )
		{
			throw new IllegalArgumentException( "Trouble reading " + measurementSettingFile + ":\n" + e.getMessage() );
		}

		if ( !document.getRootElement().getName().equals( "MeasurementSetting" ) ) { throw new IllegalArgumentException( "The target file " + measurementSettingFile + " is not a CellVoyager Measurement Setting file." ); }
	}

	public List< ChannelInfo > readInfo()
	{
		final List< ChannelInfo > channels = new ArrayList< ChannelInfo >();

		final Element root = document.getRootElement();

		/*
		 * Magnification
		 */

		final double objectiveMagnification = Double.parseDouble( root.getChild( "SelectedObjectiveLens" ).getChildText( "Magnification" ) );
		final double zoomLensMagnification = Double.parseDouble( root.getChild( "ZoomLens" ).getChild( "Magnification" ).getChildText( "Value" ) );
		final double magnification = objectiveMagnification * zoomLensMagnification;

		/*
		 * Channels
		 */

		final Element channelsEl = root.getChild( CHANNELS_ELEMENT );
		final List< Element > channelElements = channelsEl.getChildren();

		for ( final Element channelElement : channelElements )
		{
			final boolean isEnabled = Boolean.parseBoolean( channelElement.getChild( "IsEnabled" ).getText() );
			if ( !isEnabled )
			{
				continue;
			}

			final ChannelInfo ci = new ChannelInfo();
			channels.add( ci );

			ci.isEnabled = true;

			ci.channelNumber = Integer.parseInt( channelElement.getChild( "Number" ).getText() );

			final Element acquisitionSettings = channelElement.getChild( "AcquisitionSetting" );

			final Element cameraEl = acquisitionSettings.getChild( "Camera" );
			ci.tileWidth = Integer.parseInt( cameraEl.getChildText( "EffectiveHorizontalPixels_pixel" ) );
			ci.tileHeight = Integer.parseInt( cameraEl.getChildText( "EffectiveVerticalPixels_pixel" ) );

			ci.unmagnifiedPixelWidth = Double.parseDouble( cameraEl.getChildText( "HorizonalCellSize_um" ) );
			ci.unmagnifiedPixelHeight = Double.parseDouble( cameraEl.getChildText( "VerticalCellSize_um" ) );

			final Element colorElement = channelElement.getChild( "ContrastEnhanceParam" ).getChild( "Color" );
			final int r = Integer.parseInt( colorElement.getChildText( "R" ) );
			final int g = Integer.parseInt( colorElement.getChildText( "G" ) );
			final int b = Integer.parseInt( colorElement.getChildText( "B" ) );
			final int a = Integer.parseInt( colorElement.getChildText( "A" ) );
			ci.channelColor = new Color( r, g, b, a );

			ci.bitDepth = channelElement.getChild( "ContrastEnhanceParam" ).getChildText( "BitDepth" );
			ci.pixelWidth = ci.unmagnifiedPixelWidth / magnification;
			ci.pixelHeight = ci.unmagnifiedPixelWidth / magnification;

		}

		/*
		 * Fields, for each channel
		 */

		for ( final ChannelInfo channelInfo : channels )
		{

			final List< Element > fieldElements = root.getChild( "Wells" ).getChild( "Well" ).getChild( "Areas" ).getChild( "Area" ).getChild( "Fields" ).getChildren( "Field" );

			// Read field position in um
			double xmin = Double.POSITIVE_INFINITY;
			double ymin = Double.POSITIVE_INFINITY;
			double xmax = Double.NEGATIVE_INFINITY;
			double ymax = Double.NEGATIVE_INFINITY;
			final ArrayList< double[] > offsetsUm = new ArrayList< double[] >();
			for ( final Element fieldElement : fieldElements )
			{

				final double xum = Double.parseDouble( fieldElement.getChildText( "StageX_um" ) );
				if ( xum < xmin )
				{
					xmin = xum;
				}
				if ( xum > xmax )
				{
					xmax = xum;
				}

				/*
				 * Careful! For the fields to be padded correctly, we need to
				 * invert their Y position, so that it matches the pixel
				 * orientation.
				 */
				final double yum = -Double.parseDouble( fieldElement.getChildText( "StageY_um" ) );
				if ( yum < ymin )
				{
					ymin = yum;
				}
				if ( yum > ymax )
				{
					ymax = yum;
				}

				offsetsUm.add( new double[] { xum, yum } );
			}

			// Convert in pixel position
			final List< long[] > offsets = new ArrayList< long[] >();
			for ( final double[] offsetUm : offsetsUm )
			{
				final long x = ( long ) ( ( offsetUm[ 0 ] - xmin ) / ( channelInfo.unmagnifiedPixelWidth / magnification ) );
				final long y = ( long ) ( ( offsetUm[ 1 ] - ymin ) / ( channelInfo.unmagnifiedPixelHeight / magnification ) );

				offsets.add( new long[] { x, y } );
			}

			channelInfo.offsets = offsets;

			final int width = 1 + ( int ) ( ( xmax - xmin ) / ( channelInfo.unmagnifiedPixelWidth / magnification ) );
			final int height = 1 + ( int ) ( ( ymax - ymin ) / ( channelInfo.unmagnifiedPixelWidth / magnification ) );
			channelInfo.width = width + channelInfo.tileWidth;
			channelInfo.height = height + channelInfo.tileHeight;

		}

		/*
		 * Z range
		 */

		final int nZSlices = Integer.parseInt( root.getChild( "ZRange" ).getChildText( "NumberOfSlices" ) );
		final double zStroke = Double.parseDouble( root.getChild( "ZRange" ).getChildText( "Stroke_um" ) );
		final double pixelDepth = zStroke / ( nZSlices - 1 );

		for ( final ChannelInfo channelInfo : channels )
		{
			channelInfo.nZSlices = nZSlices;
			channelInfo.pixelDepth = pixelDepth;
			channelInfo.spaceUnits = "µm";
		}

		return channels;
	}

	public TimePoints readTimePoints()
	{
		final Element root = document.getRootElement();
		final int nTimePoints = Integer.parseInt( root.getChild( "TimelapsCondition" ).getChildText( "Iteration" ) );

		final List< TimePoint > timepoints = new ArrayList< TimePoint >( nTimePoints );
		for ( int i = 0; i < nTimePoints; i++ )
		{
			timepoints.add( new TimePoint( Integer.valueOf( i ) ) );
		}
		return new TimePoints( timepoints );
	}

	public double readFrameInterval()
	{
		final Element root = document.getRootElement();
		final double dt = Double.parseDouble( root.getChild( "TimelapsCondition" ).getChildText( "Interval" ) );
		return dt;
	}

	/**
	 * Export the target dataset to a xml/hd5 file couple.
	 *
	 * @param seqFile
	 *            the path to the target XML file to write.
	 * @param hdf5File
	 *            the path to the target HDF5 file to write.
	 * @param resolutions
	 *            the resolution definition for each level.
	 * @param chunks
	 *            the chunck size definition for each level.
	 * @param progressWriter
	 *            a {@link ProgressWriter} that will advance from 0 to 1 while
	 *            this method executes.
	 */
	public void export( final File seqFile, final File hdf5File, final int[][] resolutions, final int[][] chunks, final ProgressWriter progressWriter )
	{

		progressWriter.setProgress( 0d );

		final List< ChannelInfo > channelInfos = readInfo();
		/*
		 * Create view setups
		 */

		final List< BasicViewSetup > setups = new ArrayList< BasicViewSetup >( channelInfos.size() );
		int viewSetupIndex = 0;
		for ( final ChannelInfo channelInfo : channelInfos )
		{
			final Channel channel = new Channel( channelInfo.channelNumber );
			final Dimensions size = new FinalDimensions( new int[] {
					channelInfo.width,
					channelInfo.height,
					channelInfo.nZSlices } );
			final VoxelDimensions voxelSize = new FinalVoxelDimensions(
					channelInfo.spaceUnits,
					channelInfo.pixelWidth,
					channelInfo.pixelHeight,
					channelInfo.pixelDepth );
			final BasicViewSetup viewSetup = new BasicViewSetup( viewSetupIndex++, null, size, voxelSize );
			viewSetup.setAttribute( channel );
			setups.add( viewSetup );
		}

		/*
		 * Instantiate the tile loader
		 */

		final TileImgLoader imgLoader = new TileImgLoader( imageIndexFile, channelInfos );

		/*
		 * Time points
		 */

		final TimePoints timePoints = readTimePoints();

		/*
		 * Sequence description
		 */

		final SequenceDescriptionMinimal sequenceDescriptionHDF5 = new SequenceDescriptionMinimal( timePoints, Entity.idMap( setups ), imgLoader, null );

		/*
		 * Write to HDF5
		 */

		final int numCellCreatorThreads = Math.max( 1, PluginHelper.numThreads() - 1 );
		WriteSequenceToHdf5.writeHdf5File( sequenceDescriptionHDF5, resolutions, chunks, true, hdf5File, null, null, numCellCreatorThreads, progressWriter );

		/*
		 * write XML sequence description
		 */

		final SequenceDescriptionMinimal sequenceDescriptionXML = sequenceDescriptionHDF5;
		sequenceDescriptionXML.setImgLoader( new Hdf5ImageLoader( hdf5File, null, sequenceDescriptionXML, false ) );

		/*
		 * Build views
		 */

		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();

		for ( int setupIndex = 0; setupIndex < setups.size(); setupIndex++ )
		{
			final BasicViewSetup viewSetup = setups.get( setupIndex );
			final int setupId = viewSetup.getId();

			// A single transform for all the time points of a view
			final VoxelDimensions voxelSize = viewSetup.getVoxelSize();
			final double pw = voxelSize.dimension( 0 );
			final double ph = voxelSize.dimension( 1 );
			final double pd = voxelSize.dimension( 2 );
			final AffineTransform3D sourceTransform = new AffineTransform3D();
			sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );

			for ( final TimePoint timepoint : timePoints.getTimePointsOrdered() )
			{
				final int timepointId = timepoint.getId();
				final ViewRegistration view = new ViewRegistration( timepointId, setupId, sourceTransform );
				registrations.add( view );
			}
		}

		final ViewRegistrations viewRegistrations = new ViewRegistrations( registrations );
		final SpimDataMinimal spimData = new SpimDataMinimal( seqFile.getParentFile(), sequenceDescriptionXML, viewRegistrations );
		try
		{
			new XmlIoSpimDataMinimal().save( spimData, seqFile.getAbsolutePath() );
			IJ.showProgress( 1 );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}

		progressWriter.setProgress( 1d );

	}

	public static final class ChannelInfo
	{

		public int height;

		public int width;

		public int nZSlices;

		public String spaceUnits;

		public double pixelHeight;

		public double pixelWidth;

		public double pixelDepth;

		public List< long[] > offsets;

		public boolean isEnabled;

		public String bitDepth;

		public Color channelColor;

		public double unmagnifiedPixelHeight;

		public double unmagnifiedPixelWidth;

		public int tileHeight;

		public int tileWidth;

		public int channelNumber;

		@Override
		public String toString()
		{
			final StringBuffer str = new StringBuffer();
			str.append( "Channel " + channelNumber + ": \n" );
			str.append( " - isEnabled: " + isEnabled + "\n" );
			str.append( " - width: " + width + "\n" );
			str.append( " - height: " + height + "\n" );
			str.append( " - tile width: " + tileWidth + "\n" );
			str.append( " - tile height: " + tileHeight + "\n" );
			str.append( " - NZSlices: " + nZSlices + "\n" );
			str.append( " - unmagnifiedPixelWidth: " + unmagnifiedPixelWidth + "\n" );
			str.append( " - unmagnifiedPixelHeight: " + unmagnifiedPixelHeight + "\n" );
			str.append( " - color: " + channelColor + "\n" );
			str.append( " - bitDepth: " + bitDepth + "\n" );
			str.append( " - has " + offsets.size() + " fields:\n" );
			int index = 1;
			for ( final long[] offset : offsets )
			{
				str.append( "    " + index++ + ": x = " + offset[ 0 ] + ", y = " + offset[ 1 ] + "\n" );
			}
			str.append( " - spatial calibration:\n" );
			str.append( "    dx = " + pixelWidth + " " + spaceUnits + "\n" );
			str.append( "    dy = " + pixelHeight + " " + spaceUnits + "\n" );
			str.append( "    dz = " + pixelDepth + " " + spaceUnits + "\n" );
			return str.toString();
		}

	}
}
