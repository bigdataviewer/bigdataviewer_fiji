package bdv.ij;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.io.File;
import java.util.List;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import bdv.ViewerImgLoader;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;

/**
 * ImageJ plugin to import a raw image from xml/hdf5.
 *
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
public class ImportPlugIn implements PlugIn
{
	public static String xmlFile = "";
	public static int timepoint = 0;
	public static int setup = 0;
	public static int mipmap = 0;
	public static boolean openAsVirtualStack = false;

	private static SequenceDescriptionMinimal openSequence( final String xmlFilename ) throws SpimDataException
	{
		final File f = new File( xmlFilename );
		if ( f.exists() && f.isFile() && f.getName().endsWith( ".xml" ) )
		{
			final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
			return spimData.getSequenceDescription();
		}
		else
			return null;
	}

	@Override
	public void run( final String arg0 )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Import from BigDataViewer file" );
		gd.addFileField( "xml file", xmlFile );
		final TextField tfXmlFile = (TextField) gd.getStringFields().lastElement();
		gd.addSlider( "timepoint index", 0, 0, timepoint );
		final Scrollbar slTimepoint = (Scrollbar) gd.getSliders().lastElement();
		final TextField tfTimepoint = (TextField) gd.getNumericFields().lastElement();
		gd.addSlider( "setup index", 0, 0, setup );
		final Scrollbar slSetup = (Scrollbar) gd.getSliders().lastElement();
		final TextField tfSetup = (TextField) gd.getNumericFields().lastElement();
		gd.addSlider( "resolution level", 0, 0, setup );
		final Scrollbar slMipmap = (Scrollbar) gd.getSliders().lastElement();
		final TextField tfMipmap = (TextField) gd.getNumericFields().lastElement();
		gd.addCheckbox( "open as virtual stack", openAsVirtualStack );
		final Checkbox cVirtual = (Checkbox) gd.getCheckboxes().lastElement();

		class TryOpen
		{
			void check( final String xmlFilename )
			{
				boolean enable = false;
				boolean enableMipmap = false;
				try
				{
					final SequenceDescriptionMinimal seq = openSequence( xmlFilename );
					if ( seq != null )
					{
						final int numTimepoints = seq.getTimePoints().size();
						final int numSetups = seq.getViewSetupsOrdered().size();

						slTimepoint.setMaximum( numTimepoints );
						slSetup.setMaximum( numSetups );
						enable = true;

						if ( seq.getImgLoader() instanceof ViewerImgLoader )
						{
							final ViewerImgLoader< ?, ? > vil = ( ViewerImgLoader< ?, ? > ) seq.getImgLoader();
							final int numMipmapLevels = vil.numMipmapLevels( seq.getViewSetupsOrdered().get( 0 ).getId() );

							slMipmap.setMaximum( numMipmapLevels );
							enableMipmap = true;
						}
						else
						{
							enableMipmap = false;
						}
					}
				}
				catch ( final Exception ex )
				{
					IJ.error( ex.getMessage() );
					ex.printStackTrace();
				}
				slTimepoint.setEnabled( enable );
				tfTimepoint.setEnabled( enable );
				slSetup.setEnabled( enable );
				tfSetup.setEnabled( enable );
				slMipmap.setEnabled( enableMipmap );
				tfMipmap.setEnabled( enableMipmap );
				cVirtual.setEnabled( enable );
			}
		}
		final TryOpen tryOpen = new TryOpen();
		tryOpen.check( xmlFile );

		gd.addDialogListener( new DialogListener()
		{
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				gd.getNextString();
				gd.getNextNumber();
				gd.getNextNumber();
				gd.getNextNumber();
				gd.getNextBoolean();
				if ( e instanceof TextEvent && e.getID() == TextEvent.TEXT_VALUE_CHANGED && e.getSource() == tfXmlFile )
				{
					final TextField tf = ( TextField ) e.getSource();
					final String xmlFilename = tf.getText();
					tryOpen.check( xmlFilename );
				}
				return true;
			}
		} );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return;

		xmlFile = gd.getNextString();
		timepoint = ( int ) gd.getNextNumber();
		setup = ( int ) gd.getNextNumber();
		mipmap = ( int ) gd.getNextNumber();
		openAsVirtualStack = gd.getNextBoolean();

		System.out.println( xmlFile + " " + timepoint + " " + setup );
		try
		{
			final SequenceDescriptionMinimal seq = openSequence( xmlFile );
			if ( seq != null )
			{
				final List< TimePoint > timepointsOrdered = seq.getTimePoints().getTimePointsOrdered();
				final List< BasicViewSetup > setupsOrdered = seq.getViewSetupsOrdered();
				final int numTimepoints = timepointsOrdered.size();
				final int numSetups = setupsOrdered.size();
				timepoint = Math.max( Math.min( timepoint, numTimepoints - 1 ), 0 );
				setup = Math.max( Math.min( setup, numSetups - 1 ), 0 );
				final int timepointId = timepointsOrdered.get( timepoint ).getId();
				final int setupId = setupsOrdered.get( setup ).getId();

				@SuppressWarnings( "unchecked" )
				BasicImgLoader< UnsignedShortType > il = ( BasicImgLoader< UnsignedShortType > ) seq.getImgLoader();
				boolean duplicateImp = !openAsVirtualStack;
				if ( !openAsVirtualStack && il instanceof Hdf5ImageLoader )
				{
					final Hdf5ImageLoader h5il = ( Hdf5ImageLoader ) il;
					il = h5il.getMonolithicImageLoader();
					duplicateImp = false;
				}

				final RandomAccessibleInterval< UnsignedShortType > img;
				if ( il instanceof ViewerImgLoader )
				{
					final ViewerImgLoader< UnsignedShortType, ? > vil = ( ViewerImgLoader< UnsignedShortType, ? > ) seq.getImgLoader();
					final int numMipmapLevels = vil.numMipmapLevels( setupId );
					if ( mipmap >= numMipmapLevels )
						mipmap = numMipmapLevels - 1;
					img = vil.getImage( new ViewId( timepointId, setupId ), mipmap );
				}
				else
					img = il.getImage( new ViewId( timepointId, setupId ) );

				ImagePlus imp = net.imglib2.img.display.imagej.ImageJFunctions.wrap( img, "" );
				imp.setDimensions( 1, imp.getImageStackSize(), 1 );
				if ( duplicateImp )
					imp = imp.duplicate();
				imp.setTitle( new File( xmlFile ).getName() + " " + timepoint + " " + setup );
				final VoxelDimensions voxelSize = setupsOrdered.get( setup ).getVoxelSize();
				if ( voxelSize != null )
				{
					final Calibration calibration = imp.getCalibration();
					calibration.setUnit( voxelSize.unit() );
					calibration.pixelWidth = voxelSize.dimension( 0 );
					calibration.pixelHeight = voxelSize.dimension( 1 );
					calibration.pixelDepth = voxelSize.dimension( 2 );
				}
				imp.show();
			}
		}
		catch ( final Exception ex )
		{
			IJ.error( ex.getMessage() );
			ex.printStackTrace();
		}
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new ImportPlugIn().run( null );
	}
}
