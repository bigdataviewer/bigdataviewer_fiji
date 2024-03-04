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

import com.google.gson.stream.JsonReader;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

import org.apache.commons.lang.StringUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;
import bdv.viewer.ViewerOptions;
import ij.IJ;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;

/**
 * @author HongKee Moon &lt;moon@mpi-cbg.de&gt;
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Browse BigDataServer")
public class BigDataBrowserPlugIn implements Command
{
	private final Map< String, ImageIcon > imageMap = new HashMap<>();

	private final Map< String, String > datasetUrlMap = new HashMap<>();

	public static String serverUrl = "http://";

	@Override
	public void run()
	{
		if ( ij.Prefs.setIJMenuBar )
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		BufferedImage image = null;
		try
		{
			image = ImageIO.read( getClass().getResourceAsStream( "/fiji.png" ) );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}

		final Object remoteUrl = JOptionPane.showInputDialog( null, "Enter BigDataServer Remote URL:", "BigDataServer",
				JOptionPane.QUESTION_MESSAGE, new ImageIcon( image ), null, serverUrl );

		if ( remoteUrl == null )
			return;

		serverUrl = remoteUrl.toString();

		final ArrayList< String > nameList = new ArrayList<>();
		try
		{
			getDatasetList( serverUrl, nameList );
		}
		catch ( final IOException e )
		{
			IJ.showMessage( "Error connecting to server at " + serverUrl );
			e.printStackTrace();
		}
		createDatasetListUI( serverUrl, nameList.toArray() );
	}

	private boolean getDatasetList( final String remoteUrl, final ArrayList< String > nameList ) throws IOException
	{
		// Get JSON string from the server
		final URL url = new URL( remoteUrl + "/json/" );

		final InputStream is = url.openStream();
		final JsonReader reader = new JsonReader( new InputStreamReader( is, "UTF-8" ) );

		reader.beginObject();

		while ( reader.hasNext() )
		{
			// skipping id
			reader.nextName();

			reader.beginObject();

			String id = null, description = null, thumbnailUrl = null, datasetUrl = null;
			while ( reader.hasNext() )
			{
				final String name = reader.nextName();
				if ( name.equals( "id" ) )
					id = reader.nextString();
				else if ( name.equals( "description" ) )
					description = reader.nextString();
				else if ( name.equals( "thumbnailUrl" ) )
					thumbnailUrl = reader.nextString();
				else if ( name.equals( "datasetUrl" ) )
					datasetUrl = reader.nextString();
				else
					reader.skipValue();
			}

			if ( id != null )
			{
				nameList.add( id );
				if ( thumbnailUrl != null && StringUtils.isNotEmpty( thumbnailUrl ) )
					imageMap.put( id, new ImageIcon( new URL( thumbnailUrl ) ) );
				if ( datasetUrl != null )
					datasetUrlMap.put( id, datasetUrl );
			}

			reader.endObject();
		}

		reader.endObject();

		reader.close();

		return true;
	}

	private void createDatasetListUI( final String remoteUrl, final Object[] values )
	{
		final JList< ? > list = new JList<>( values );
		list.setCellRenderer( new ThumbnailListRenderer() );
		list.addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked( final MouseEvent evt )
			{
				final JList< ? > list = ( JList< ? > ) evt.getSource();
				if ( evt.getClickCount() == 2 )
				{
					final int index = list.locationToIndex( evt.getPoint() );
					final String key = String.valueOf( list.getModel().getElementAt( index ) );
					System.out.println( key );
					try
					{
						final String filename = datasetUrlMap.get( key );
						final String title = new File( filename ).getName();
						BigDataViewer.open( filename, title, new ProgressWriterIJ(), ViewerOptions.options() );
					}
					catch ( final SpimDataException e )
					{
						e.printStackTrace();
					}
				}
			}
		} );

		final JScrollPane scroll = new JScrollPane( list );
		scroll.setPreferredSize( new Dimension( 600, 800 ) );

		final JFrame frame = new JFrame();
		frame.setTitle( "BigDataServer Browser - " + remoteUrl );
		frame.add( scroll );
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.pack();
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );
	}

	public class ThumbnailListRenderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;

		Font font = new Font( "helvetica", Font.BOLD, 12 );

		@Override
		public Component getListCellRendererComponent(
				final JList< ? > list, final Object value, final int index,
				final boolean isSelected, final boolean cellHasFocus )
		{

			final JLabel label = ( JLabel ) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus );
			label.setIcon( imageMap.get( value ) );
			label.setHorizontalTextPosition( JLabel.RIGHT );
			label.setFont( font );
			return label;
		}
	}

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		new BigDataBrowserPlugIn().run();
	}
}
