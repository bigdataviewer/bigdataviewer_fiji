/*-
 * #%L
 * Fiji plugins for starting BigDataViewer and exporting data.
 * %%
 * Copyright (C) 2014 - 2024 BigDataViewer developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package bdv.ij;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.io.File;
import java.util.List;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.NumericType;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;
import org.scijava.log.LogService;

import bdv.ViewerImgLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.ImageJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicMultiResolutionImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.VoxelDimensions;

/**
 * ImageJ plugin to import a raw image from xml/hdf5.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
@Plugin(type = Command.class, menuPath = "File>Import>BigDataViewer...",
        name = "BDV_extractImage", headless = true,
        description = "Extracts one image from BigDataViewer HDF/XML dataset.")
public class ImportPlugIn implements Command
{
	@Parameter
	private LogService log;

	///input filename
	@Parameter(label = "import from BigDataViewer XML file:",
		style = FileWidget.OPEN_STYLE,
		callback = "fetchMaxValuesFromXML")
	public File xmlFile = null;

	//input timepoint index
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false,
		label = "available timepoint index")
	private String timepointHint = "range: 0-?";
	//
	@Parameter(label = "selected timepoint index:",
		min="0",
		callback="enforceMaxTimepoint")
	public int timepointVal = 0;
	private int timepointMax = 0;

	//input setup index
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false,
		label = "available setup index")
	private String setupHint = "range: 0-?";
	//
	@Parameter(label = "selected setup index:",
		min="0",
		callback="enforceMaxSetup")
	public int setupVal = 0;
	private int setupMax = 0;

	//input mipmap index
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false,
		label = "available resolution index")
	private String mipmapHint = "range: 0-?";
	//
	@Parameter(label = "selected resolution level:",
		min="0",
		callback="enforceMaxMipmap")
	public int mipmapVal = 0;
	private int mipmapMax = 0;

	//input checkbox
	@Parameter(label = "open as virtual stack:")
	public boolean openAsVirtualStack = false;


	//output image
	@Parameter(type = ItemIO.OUTPUT)
	public ImagePlus imp = null;


	//helper flag to make sure fetchMaxValuesFromXML() is called (if not ever
	//called before) with the change of any dialog attribute, this especially
	//assures that an attempt to fetch updated values is made
	private boolean wasFetcherCalledAlready = false;

	//helper flag to enable/disable bounds checking of some dialog attribute
	private boolean gaveFetcherSomeValues = false;

	///make sure timepointVal is not larger than timepointMax
	private
	void enforceMaxTimepoint()
	{
		if (!wasFetcherCalledAlready) fetchMaxValuesFromXML();
		//enforce...
		if (gaveFetcherSomeValues && timepointVal > timepointMax) timepointVal = timepointMax;
	}

	///make sure setupVal is not larger than setupMax
	private
	void enforceMaxSetup()
	{
		if (!wasFetcherCalledAlready) fetchMaxValuesFromXML();
		if (gaveFetcherSomeValues && setupVal > setupMax) setupVal = setupMax;
	}

	///make sure mipmapVal is not larger than mipmapMax
	private
	void enforceMaxMipmap()
	{
		if (!wasFetcherCalledAlready) fetchMaxValuesFromXML();
		if (gaveFetcherSomeValues && mipmapVal > mipmapMax) mipmapVal = mipmapMax;
	}


	/**
	 * Tries to open this.xmlFile and updates the remaining
	 * attributes from it, especially their "max" variants.
	 */
	private
	void fetchMaxValuesFromXML()
	{
		//first, read-out proper upper bounds...
		wasFetcherCalledAlready = true;
		gaveFetcherSomeValues = false;
		try
		{
			final SequenceDescriptionMinimal seq = openSequence();
			if ( seq != null )
			{
				log.info("reading metadata from "+xmlFile.getName());
				timepointMax = seq.getTimePoints().size();
				setupMax = seq.getViewSetupsOrdered().size();

				if ( seq.getImgLoader() instanceof ViewerImgLoader )
				{
					final ViewerImgLoader vil = ( ViewerImgLoader ) seq.getImgLoader();
					mipmapMax = vil.getSetupImgLoader( seq.getViewSetupsOrdered().get( 0 ).getId() ).numMipmapLevels();
				}
				else
				{
					mipmapMax = 0;
				}
				gaveFetcherSomeValues = true;
			}
			else
			{
				//couldn't load input file, returning to the initial values
				timepointMax = 0;
				setupMax = 0;
				mipmapMax = 0;
			}
		}
		catch ( final Exception ex )
		{
			log.error( ex.getMessage() );
			ex.printStackTrace();
		}

		//...and update hint messages
		if (gaveFetcherSomeValues)
		{
			timepointHint = String.format("range: 0-%d",timepointMax);
			setupHint     = String.format("range: 0-%d",setupMax);
			mipmapHint    = String.format("range: 0-%d",mipmapMax);
		}
		else
		{
			timepointHint = "range: 0-?";
			setupHint     = timepointHint;
			mipmapHint    = timepointHint;
		}

		//second, enforce them
		enforceMaxTimepoint();
		enforceMaxSetup();
		enforceMaxMipmap();
	}

	///file-system oriented checks and loads in metadata
	private
	SequenceDescriptionMinimal openSequence()
	throws SpimDataException
	{
		if ( xmlFile != null && xmlFile.exists() && xmlFile.isFile() && xmlFile.getName().endsWith( ".xml" ) )
		{
			final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFile.getAbsolutePath() );
			return spimData.getSequenceDescription();
		}
		else
			return null;
	}


	@Override
	//NB: this function intentionally ignores *Max attributes and does its
	//    own checking for proper/allowed values because non-GUI usage is
	//    not (should not) be aware of the *Max siblings (which are helpers
	//    for GUI only), and because GUI dialog injects last used values after
	//    the callbacks are called and so the *Max values won't get updated
	//    (while xmlFile might be pointing good/at existing file).
	public void run()
	{
		if (xmlFile != null)
			System.out.println( xmlFile.getName() + " " + timepointVal + " " + setupVal );
		try
		{
			final SequenceDescriptionMinimal seq = openSequence();
			if ( seq != null )
			{
				final List< TimePoint > timepointsOrdered = seq.getTimePoints().getTimePointsOrdered();
				final List< BasicViewSetup > setupsOrdered = seq.getViewSetupsOrdered();
				final int numTimepoints = timepointsOrdered.size();
				final int numSetups = setupsOrdered.size();
				timepointVal = Math.max( Math.min( timepointVal, numTimepoints - 1 ), 0 );
				setupVal = Math.max( Math.min( setupVal, numSetups - 1 ), 0 );
				final int timepointId = timepointsOrdered.get( timepointVal ).getId();
				final int setupId = setupsOrdered.get( setupVal ).getId();

				final BasicImgLoader il = seq.getImgLoader();
				final boolean duplicateImp = !openAsVirtualStack;
				final ImgLoaderHint[] hints = openAsVirtualStack ? new ImgLoaderHint[ 0 ] : new ImgLoaderHint[] { LOAD_COMPLETELY };

				final RandomAccessibleInterval< ? > img;
				final double[] mipmapResolution;
				if ( il instanceof BasicMultiResolutionImgLoader )
				{
					final BasicMultiResolutionImgLoader mil = ( BasicMultiResolutionImgLoader ) il;
					final int numMipmapLevels = mil.getSetupImgLoader( setupId ).numMipmapLevels();
					if ( mipmapVal >= numMipmapLevels )
						mipmapVal = numMipmapLevels - 1;
					img = mil.getSetupImgLoader( setupId ).getImage( timepointId, mipmapVal, hints );
					mipmapResolution = mil.getSetupImgLoader( setupId ).getMipmapResolutions()[ mipmapVal ];
				}
				else
				{
					img = il.getSetupImgLoader( setupId ).getImage( timepointId, hints );
					mipmapResolution = new double[] { 1, 1, 1 };
				}

				@SuppressWarnings({ "unchecked", "rawtypes" })
				ImagePlus iimp = net.imglib2.img.display.imagej.ImageJFunctions.wrap( ( RandomAccessibleInterval< NumericType > ) img, "" );
				imp = iimp;
				//NB: terrible workaround to have SuppressWarnings local...
				//    (and imp global to this class)

				imp.setDimensions( 1, imp.getImageStackSize(), 1 );
				if ( duplicateImp )
					imp = imp.duplicate();
				imp.setTitle( xmlFile.getName() + " " + timepointVal + " " + setupVal );
				final VoxelDimensions voxelSize = setupsOrdered.get( setupVal ).getVoxelSize();
				if ( voxelSize != null )
				{
					final Calibration calibration = imp.getCalibration();
					calibration.setUnit( voxelSize.unit() );
					calibration.pixelWidth = voxelSize.dimension( 0 ) * mipmapResolution[ 0 ];
					calibration.pixelHeight = voxelSize.dimension( 1 ) * mipmapResolution[ 1 ];
					calibration.pixelDepth = voxelSize.dimension( 2 ) * mipmapResolution[ 2 ];
				}
				//let the user decice what to do with the extracted image
				//NB: default plugin action for output images is just to display them...
				//imp.show();
			}
		}
		catch ( final Exception ex )
		{
			log.error( ex.getMessage() );
			ex.printStackTrace();
		}
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		//new ImportPlugIn().run();
	}
}
