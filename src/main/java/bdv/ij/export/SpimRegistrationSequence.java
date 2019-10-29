package bdv.ij.export;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import bdv.ij.export.imgloader.HuiskenImageLoader;
import bdv.ij.export.imgloader.StackImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.fusion.SPIMImageFusion;
import mpicbg.spim.io.ConfigurationParserException;
import mpicbg.spim.io.SPIMConfiguration;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.ViewStructure;
import mpicbg.spim.registration.bead.BeadRegistration;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import spim.vecmath.Point3f;
import spimopener.SPIMExperiment;

@Deprecated
public class SpimRegistrationSequence
{
	private final SequenceDescriptionMinimal sequenceDescription;

	private final ViewRegistrations viewRegistrations;

	private final SPIMConfiguration conf;

	public SpimRegistrationSequence( final SPIMConfiguration conf )
	{
		this.conf = conf;
		final ArrayList< ViewSetup > setups = createViewSetups( conf );
//		final HashMap< Integer, ViewSetup > setups = Entity.idMap( createViewSetups( conf ) );
		final TimePoints timepoints = createTimePoints( conf );


		final BasicImgLoader imgLoader = createImageLoader( conf, setups );

		viewRegistrations = createViewRegistrations( conf, setups );
		sequenceDescription = new SequenceDescriptionMinimal( timepoints, Entity.idMap( createViewSetups( conf ) ), imgLoader, null );
	}

	public SpimRegistrationSequence( final String huiskenExperimentXmlFile, final String channels, final String angles, final String timepoints, final int referenceTimePoint ) throws ConfigurationParserException
	{
		this( initExperimentConfiguration( huiskenExperimentXmlFile, "", channels, angles, timepoints, referenceTimePoint, false, 0 ) );
	}

	public SpimRegistrationSequence( final String inputDirectory, final String inputFilePattern, final String channels, final String angles, final String timepoints, final int referenceTimePoint, final boolean overrideImageZStretching, final double zStretching ) throws ConfigurationParserException
	{
		this( initExperimentConfiguration( inputDirectory, inputFilePattern, channels, angles, timepoints, referenceTimePoint, overrideImageZStretching, zStretching ) );
	}

	public SequenceDescriptionMinimal getSequenceDescription()
	{
		return sequenceDescription;
	}

	public ViewRegistrations getViewRegistrations()
	{
		return viewRegistrations;
	}

	protected static BasicImgLoader createImageLoader( final SPIMConfiguration conf, final ArrayList< ViewSetup > setups )
	{
		final int numTimepoints = conf.timepoints.length;
		final HashMap< ViewId, String > filenames = new HashMap<>();
		for ( int timepoint = 0; timepoint < numTimepoints; ++timepoint )
		{
			final int timepointId = conf.timepoints[ timepoint ];
			final ViewStructure viewStructure = ViewStructure.initViewStructure( conf, timepoint, new mpicbg.models.AffineModel3D(), "ViewStructure Timepoint " + timepointId, conf.debugLevelInt );

			for ( final ViewDataBeads viewDataBeads : viewStructure.getViews() )
			{
				// get ViewId
				final int angle = viewDataBeads.getAcqusitionAngle();
				final int illumination = viewDataBeads.getIllumination();
				final int channel = viewDataBeads.getChannel();
				final int setupId = getViewSetupId( setups, angle, illumination, channel );
				filenames.put( new ViewId( timepointId, setupId ), viewDataBeads.getFileName() );
			}
		}
		if ( conf.isHuiskenFormat() )
		{
			final String exp = conf.inputdirectory.endsWith( "/" ) ? conf.inputdirectory.substring( 0, conf.inputdirectory.length() - 1 ) : conf.inputdirectory;
			return new HuiskenImageLoader( new File( exp + ".xml" ), Entity.idMap( setups ) );
		}
		else
		{
			final boolean useImageJOpener = conf.inputFilePattern.endsWith( ".tif" );
			return new StackImageLoader( filenames, useImageJOpener );
		}
	}

	/**
	 * Instantiate the SPIM configuration only with the necessary parameters
	 * @return
	 */
	protected static SPIMConfiguration initExperimentConfiguration( final String inputDirectory, final String inputFilePattern, final String angles, final String timepoints, final int referenceTimePoint, final boolean overrideImageZStretching, final double zStretching ) throws ConfigurationParserException
	{
		return initExperimentConfiguration( inputDirectory, inputFilePattern, "", angles, timepoints, referenceTimePoint, overrideImageZStretching, zStretching );
	}

	/**
	 * Instantiate the SPIM configuration only with the necessary parameters
	 * @return
	 */
	protected static SPIMConfiguration initExperimentConfiguration( final String inputDirectory, final String inputFilePattern, final String channels, final String angles, final String timepoints, final int referenceTimePoint, final boolean overrideImageZStretching, final double zStretching ) throws ConfigurationParserException
	{
		final SPIMConfiguration conf = new SPIMConfiguration();
		conf.timepointPattern = timepoints;
		conf.channelPattern = conf.channelsToRegister = conf.channelsToFuse = (channels == null) ? "" : channels;
		conf.anglePattern = angles;

		final File f = new File( inputDirectory );
		if ( f.exists() && f.isFile() && f.getName().endsWith( ".xml" ) )
		{
			conf.spimExperiment = new SPIMExperiment( f.getAbsolutePath() );
			conf.inputdirectory = f.getAbsolutePath().substring( 0, f.getAbsolutePath().length() - 4 ) + "/";
		}
		else
		{
			conf.inputdirectory = inputDirectory;
		}

		conf.inputFilePattern = inputFilePattern;

		if ( referenceTimePoint >= 0 )
			conf.timeLapseRegistration = true;
		conf.referenceTimePoint = referenceTimePoint;

		// check the directory string
		conf.inputdirectory = conf.inputdirectory.replace( '\\', '/' );
		conf.inputdirectory = conf.inputdirectory.replaceAll( "//", "/" );
		conf.inputdirectory = conf.inputdirectory.trim();
		if (conf.inputdirectory.length() > 0 && !conf.inputdirectory.endsWith("/"))
			conf.inputdirectory = conf.inputdirectory + "/";

		conf.outputdirectory = conf.inputdirectory + "output/";
		conf.registrationFiledirectory = conf.inputdirectory + "registration/";

		conf.overrideImageZStretching = overrideImageZStretching;
		conf.zStretching = zStretching;

		conf.fuseOnly= true; // this is to avoid an exception in the multi-channel case

		if ( conf.isHuiskenFormat() )
			conf.getFilenamesHuisken();
		else
			conf.getFileNames();

		return conf;
	}

	protected static ArrayList< ViewSetup > createViewSetups( final SPIMConfiguration conf )
	{
		final ArrayList< ViewSetup > setups = new ArrayList<>();
		int setup_id = 0;
		for ( int channelIndex = 0; channelIndex < conf.file[ 0 ].length; channelIndex++ )
			for ( int angleIndex = 0; angleIndex < conf.file[ 0 ][ channelIndex ].length; angleIndex++ )
				for ( int illuminationIndex = 0; illuminationIndex < conf.file[ 0 ][ channelIndex ][ angleIndex ].length; ++illuminationIndex )
				{
					String name = "";
					if ( conf.angles.length > 1 )
					{
						name += "a " + conf.angles[ angleIndex ];
					}
					if ( conf.channels.length > 1 )
					{
						name += ( name.isEmpty() ? "" : " " ) + "c " + conf.channels[ channelIndex ];
					}
					if ( conf.illuminations.length > 1 )
					{
						name += ( name.isEmpty() ? "" : " " ) + "i " + conf.illuminations[ illuminationIndex ];
					}
					final Channel channel = new Channel( conf.channels[ channelIndex ] );
					final Angle angle = new Angle( conf.angles[ angleIndex ] );
					final Illumination illumination = new Illumination( conf.illuminations[ illuminationIndex ] );

					Dimensions size = null;
					VoxelDimensions voxelSize = null;
					final ViewStructure viewStructure = ViewStructure.initViewStructure( conf, 0, new mpicbg.models.AffineModel3D(), "ViewStructure Timepoint " + 0, conf.debugLevelInt );
					for ( final ViewDataBeads viewDataBeads : viewStructure.getViews() )
					{
						if ( angle.getId() == viewDataBeads.getAcqusitionAngle() &&
								illumination.getId() == viewDataBeads.getIllumination() &&
								channel.getId() == viewDataBeads.getChannel() )
						{
							voxelSize = new FinalVoxelDimensions( "px", 1.0, 1.0, viewDataBeads.getZStretching() );
							size = new FinalDimensions( viewDataBeads.getImageSize() );
							break;
						}
					}

					setups.add( new ViewSetup( setup_id++, name, size, voxelSize, channel, angle, illumination ) );
				}
		return setups;
	}

	protected static TimePoints createTimePoints( final SPIMConfiguration conf )
	{
		final ArrayList< TimePoint > timepoints = new ArrayList<>();
		for ( final int tp : conf.timepoints )
			timepoints.add( new TimePoint( tp ) );
		return new TimePoints( timepoints );
	}

	public Map< Integer, AffineTransform3D > getFusionTransforms( final int cropOffsetX, final int cropOffsetY, final int cropOffsetZ, final int scale )
	{
		conf.cropOffsetX = cropOffsetX;
		conf.cropOffsetY = cropOffsetY;
		conf.cropOffsetZ = cropOffsetZ;
		conf.scale = scale;

		final HashMap< Integer, AffineTransform3D > transforms = new HashMap<>();
		if ( conf.timeLapseRegistration )
		{
			SPIMConfiguration refconf = conf;
			if ( conf.getTimePointIndex( conf.referenceTimePoint ) < 0 )
			{
				try
				{
					final String inputdirectory;
					if ( conf.isHuiskenFormat() )
						inputdirectory = conf.inputdirectory.substring( 0, conf.inputdirectory.length() - 1 ) + ".xml";
					else
						inputdirectory = conf.inputdirectory;
					refconf = initExperimentConfiguration( inputdirectory, conf.inputFilePattern, conf.anglePattern, "" + conf.referenceTimePoint, conf.referenceTimePoint, conf.overrideImageZStretching, conf.zStretching );
					refconf.cropOffsetX = cropOffsetX;
					refconf.cropOffsetY = cropOffsetY;
					refconf.cropOffsetZ = cropOffsetZ;
					refconf.scale = scale;
				}
				catch ( final ConfigurationParserException e )
				{
					e.printStackTrace();
				}
			}
			final RealInterval interval = getFusionBoundingBox( refconf, 0 );
			final double tx = interval.realMin( 0 );
			final double ty = interval.realMin( 1 );
			final double tz = interval.realMin( 2 );
			final double s = scale;
			System.out.println( "tx = " + tx + " ty = " + ty + " tz = " + tz + " scale = " + scale );
			final AffineTransform3D transform = new AffineTransform3D();
			transform.set( s, 0, 0, tx, 0, s, 0, ty, 0, 0, s, tz );
			for ( final int tp : conf.timepoints )
				transforms.put( tp, transform );
		}
		else
		{
			for ( final int tp : conf.timepoints )
			{
				final RealInterval interval = getFusionBoundingBox( conf, tp );
				final double tx = interval.realMin( 0 );
				final double ty = interval.realMin( 1 );
				final double tz = interval.realMin( 2 );
				final double s = scale;
				System.out.println( "tx = " + tx + " ty = " + ty + " tz = " + tz + " scale = " + scale );
				final AffineTransform3D transform = new AffineTransform3D();
				transform.set( s, 0, 0, tx, 0, s, 0, ty, 0, 0, s, tz );
				transforms.put( tp, transform );
			}
		}
		return transforms;
	}

	protected static RealInterval getFusionBoundingBox( final SPIMConfiguration conf, final int timepointId )
	{
			final Point3f min = new Point3f();
			final Point3f max = new Point3f();
			final Point3f size = new Point3f();

			final int tp = conf.getTimePointIndex( conf.timeLapseRegistration ? conf.referenceTimePoint : timepointId );

			@SuppressWarnings( "unchecked" )
			final ViewStructure reference = ViewStructure.initViewStructure( conf, tp, conf.getModel(), "Reference ViewStructure Timepoint " + conf.referenceTimePoint, conf.debugLevelInt );
			for ( final ViewDataBeads viewDataBeads : reference.getViews() )
			{
				// coordinate system)
				if ( conf.timeLapseRegistration )
					viewDataBeads.loadRegistrationTimePoint( conf.referenceTimePoint );
				else
					viewDataBeads.loadRegistration();

				// apply the z-scaling to the transformation
				BeadRegistration.concatenateAxialScaling( viewDataBeads, reference.getDebugLevel() );
			}
			SPIMImageFusion.computeImageSize( reference.getViews(), min, max, size, conf.scale, conf.cropSizeX, conf.cropSizeY, conf.cropSizeZ, reference.getDebugLevel() );

			final int scale = conf.scale;
			final int cropOffsetX = conf.cropOffsetX;
			final int cropOffsetY = conf.cropOffsetY;
			final int cropOffsetZ = conf.cropOffsetZ;
			final int imgW;
			final int imgH;
			final int imgD;

			if (conf.cropSizeX == 0)
				imgW = (Math.round((float)Math.ceil(size.x)) + 1)/scale;
			else
				imgW = conf.cropSizeX/scale;

			if (conf.cropSizeY == 0)
				imgH = (Math.round((float)Math.ceil(size.y)) + 1)/scale;
			else
				imgH = conf.cropSizeY/scale;

			if (conf.cropSizeZ == 0)
				imgD = (Math.round((float)Math.ceil(size.z)) + 1)/scale;
			else
				imgD = conf.cropSizeZ/scale;

			return FinalRealInterval.createMinMax(
					( int ) min.x + cropOffsetX,
					( int ) min.y + cropOffsetY,
					( int ) min.z + cropOffsetZ,
					( int ) min.x + cropOffsetX + imgW - 1,
					( int ) min.y + cropOffsetY + imgH - 1,
					( int ) min.z + cropOffsetZ + imgD - 1 );
	}

	protected static ViewRegistrations createViewRegistrations( final SPIMConfiguration conf, final ArrayList< ViewSetup > setups )
	{
		final ArrayList< ViewRegistration > regs = new ArrayList<>();

		// for each time-point initialize the view structure, load&apply
		// registrations, instantiate the View objects for Tracking
		for ( int i = 0; i < conf.timepoints.length; ++i )
		{
			final int timepointId = conf.timepoints[ i ];
			final ViewStructure viewStructure = ViewStructure.initViewStructure( conf, i, new mpicbg.models.AffineModel3D(), "ViewStructure Timepoint " + timepointId, conf.debugLevelInt );

			for ( final ViewDataBeads viewDataBeads : viewStructure.getViews() )
			{
				// load time-point registration (to map into the global
				// coordinate system)
				if ( conf.timeLapseRegistration )
					viewDataBeads.loadRegistrationTimePoint( conf.referenceTimePoint );
				else
					viewDataBeads.loadRegistration();

				// apply the z-scaling to the transformation
				BeadRegistration.concatenateAxialScaling( viewDataBeads, viewStructure.getDebugLevel() );

				final int angle = viewDataBeads.getAcqusitionAngle();
				final int illumination = viewDataBeads.getIllumination();
				final int channel = viewDataBeads.getChannel();
				final AffineTransform3D model = new AffineTransform3D();
				final double[][] tmp = new double[3][4];
				( ( mpicbg.models.AffineModel3D ) viewDataBeads.getTile().getModel() ).toMatrix( tmp );
				model.set( tmp );

				// get corresponding setup id
				final int setupId = getViewSetupId( setups, angle, illumination, channel );

				// create ViewRegistration
				regs.add( new ViewRegistration( timepointId, setupId, model ) );
			}
		}

		return new ViewRegistrations( regs );
	}

	protected static ArrayList< Integer > makeList( final int[] ints )
	{
		final ArrayList< Integer > list = new ArrayList<>( ints.length );
		for ( final int i : ints )
			list.add( i );
		return list;
	}

	/**
	 * find ViewSetup index corresponding to given (angle, illumination,
	 * channel) triple.
	 *
	 * @return setup index or -1 if no corresponding setup was found.
	 */
	protected static int getViewSetupId( final ArrayList< ViewSetup > setups, final int angle, final int illumination, final int channel )
	{
		for ( final ViewSetup s : setups )
			if ( s.getAngle().getId() == angle && s.getIllumination().getId() == illumination && s.getChannel().getId() == channel )
				return s.getId();
		return -1;
	}

	public SPIMConfiguration getConf()
	{
		return conf;
	}
}

