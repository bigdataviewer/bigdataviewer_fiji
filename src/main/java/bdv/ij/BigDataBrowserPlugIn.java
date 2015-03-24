package bdv.ij;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;

import com.google.gson.stream.JsonReader;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;

import org.apache.commons.lang.StringUtils;

import javax.imageio.ImageIO;
import javax.swing.*;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author HongKee Moon <moon@mpi-cbg.de>
 */
public class BigDataBrowserPlugIn implements PlugIn
{
	private final Map< String, ImageIcon > imageMap = new HashMap< String, ImageIcon >();

	private final Map< String, String > datasetUrlMap = new HashMap< String, String >();

	public static String serverUrl = "http://";

	@Override
	public void run( final String arg )
	{
		BufferedImage image = null;
		try
		{
			image = ImageIO.read( new URL( "http://fiji.sc/_images/a/ae/Fiji-icon.png" ) );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}

		if ( null == arg )
		{
			final Object remoteUrl = JOptionPane.showInputDialog( null, "Enter BigDataServer Remote URL:", "BigDataServer",
					JOptionPane.QUESTION_MESSAGE, new ImageIcon( image ), null, serverUrl );

			if ( remoteUrl == null )
				return;

			serverUrl = remoteUrl.toString();
		}
		else
		{
			String line = null;
			FileReader fileReader = null;

			try
			{
				fileReader = new FileReader( new File( arg ) );
			}
			catch ( FileNotFoundException e )
			{
				e.printStackTrace();
			}

			BufferedReader br = new BufferedReader( fileReader );

			try
			{
				if ( ( line = br.readLine() ) != null )
				{
					serverUrl = line;
				}
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}

			if ( line == null )
				return;
		}

		final ArrayList< String > nameList = new ArrayList< String >();
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
		final JList list = new JList( values );
		list.setCellRenderer( new ThumbnailListRenderer() );
		list.addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked( final MouseEvent evt )
			{
				final JList list = ( JList ) evt.getSource();
				if ( evt.getClickCount() == 2 )
				{
					final int index = list.locationToIndex( evt.getPoint() );
					final String key = String.valueOf( list.getModel().getElementAt( index ) );
					System.out.println( key );
					try
					{
						BigDataViewer.view( datasetUrlMap.get( key ), new ProgressWriterIJ() );
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
				final JList list, final Object value, final int index,
				final boolean isSelected, final boolean cellHasFocus )
		{

			final JLabel label = ( JLabel ) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus );
			label.setIcon( imageMap.get( ( String ) value ) );
			label.setHorizontalTextPosition( JLabel.RIGHT );
			label.setFont( font );
			return label;
		}
	}

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		new BigDataBrowserPlugIn().run( "/Users/moon/Desktop/local.bdv" );
	}
}
