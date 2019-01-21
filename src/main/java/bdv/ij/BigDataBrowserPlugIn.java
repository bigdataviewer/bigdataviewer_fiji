package bdv.ij;

import com.google.gson.stream.JsonReader;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;

import net.imagej.ImageJ;
import org.apache.commons.lang.StringUtils;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * @author HongKee Moon &lt;moon@mpi-cbg.de&gt;
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Browse BigDataServer")
public class BigDataBrowserPlugIn implements Command
{
	private final Map< String, ImageIcon > imageMap = new HashMap<>();

	private final Map< String, String > datasetUrlMap = new HashMap<>();

	@Parameter(required = false)
	String serverUrl = "http://";

	@Parameter
	CommandService cs;

	@Parameter
	LogService ls;

	@Override
	public void run()
	{
		final ArrayList< String > nameList = new ArrayList<>();
		try
		{
			getDatasetList( serverUrl, nameList );
		}
		catch ( final IOException e )
		{
			ls.error("Error connecting to server at " + serverUrl);
			e.printStackTrace();
			return;
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
					final String filename = datasetUrlMap.get( key );
					final String title = new File( filename ).getName();

					cs.run(BigDataServerPlugIn.class,true,
							"urlServer",remoteUrl,
							"datasetName", title);
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
		final net.imagej.ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(BigDataBrowserPlugIn.class, true);
	}
}
