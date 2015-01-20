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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BigDataBrowserPlugin implements PlugIn
{
	Map< String, ImageIcon > imageMap = new HashMap< String, ImageIcon >();
	Map< String, String > datasetUrlMap = new HashMap< String, String >();

	@Override
	public void run( final String arg )
	{
		BufferedImage image = null;
		try
		{
			image = ImageIO.read( new URL( "http://fiji.sc/_images/a/ae/Fiji-icon.png" ) );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}

		String serverUrl = "http://scicomp-pc-1-10gb:8070";
//		Clipboard access for getting bigdataserver url
//		Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
//		Transferable t = c.getContents( this );
//		if ( t != null )
//		{
//			try
//			{
//				serverUrl = ( String ) t.getTransferData( DataFlavor.stringFlavor );
//			}
//			catch ( Exception e )
//			{
//				e.printStackTrace();
//			}
//
//			if ( !serverUrl.startsWith( "http://" ) )
//			{
//				serverUrl = "http://" + serverUrl;
//			}
//		}

		boolean ret;
		do
		{
			Object remoteUrl = JOptionPane.showInputDialog( null, "Enter BigDataServer Remote URL:", "BigDataServer",
					JOptionPane.QUESTION_MESSAGE, new ImageIcon( image ), null, serverUrl );

			if ( remoteUrl == null )
			{
				IJ.showMessage( "The given URL is null. Please, check it again." );
				break;
			}

			ArrayList< String > nameList = new ArrayList< String >();
			ret = getDatasetList( remoteUrl.toString(), nameList );
			if ( !ret )
				IJ.showMessage( "The server is not available." );
			else
				createDatasetListUI( nameList.toArray() );

		} while ( !ret );

	}

	private boolean getDatasetList( String remoteUrl, ArrayList< String > nameList )
	{
		try
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

				String name = reader.nextName();
				String id = null, description = null, thumbnailUrl = null, datasetUrl = null;

				if ( name.equals( "id" ) )
				{
					id = reader.nextString();
					nameList.add( id );
				}

				name = reader.nextName();
				if ( name.equals( "description" ) )
				{
					description = reader.nextString();
				}

				name = reader.nextName();
				if ( name.equals( "thumbnailUrl" ) )
				{
					thumbnailUrl = reader.nextString();
					if ( StringUtils.isNotEmpty( thumbnailUrl ) )
						imageMap.put( id, new ImageIcon( new URL( thumbnailUrl ) ) );
				}

				name = reader.nextName();
				if ( name.equals( "datasetUrl" ) )
				{
					datasetUrl = reader.nextString();
					datasetUrlMap.put( id, datasetUrl );
				}

				reader.endObject();
			}

			reader.endObject();

		}
		catch ( IOException exception )
		{
			exception.printStackTrace();
			return false;
		}

		return true;
	}

	private void createDatasetListUI( Object[] values )
	{
		JList list = new JList( values );
		list.setCellRenderer( new ThumbnailListRenderer() );
		list.addMouseListener( new MouseAdapter()
		{
			public void mouseClicked( MouseEvent evt )
			{
				JList list = ( JList ) evt.getSource();
				if ( evt.getClickCount() == 2 )
				{
					int index = list.locationToIndex( evt.getPoint() );
					String key = String.valueOf( list.getModel().getElementAt( index ) );
					System.out.println( key );
					try
					{
						BigDataViewer.view( datasetUrlMap.get( key ), new ProgressWriterIJ() );
					}
					catch ( SpimDataException e )
					{
						e.printStackTrace();
					}
				}
			}
		} );

		JScrollPane scroll = new JScrollPane( list );
		scroll.setPreferredSize( new Dimension( 300, 400 ) );

		JFrame frame = new JFrame();
		frame.add( scroll );
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.pack();
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );
	}

	public class ThumbnailListRenderer extends DefaultListCellRenderer
	{

		Font font = new Font( "helvitica", Font.BOLD, 12 );

		@Override
		public Component getListCellRendererComponent(
				JList list, Object value, int index,
				boolean isSelected, boolean cellHasFocus )
		{

			JLabel label = ( JLabel ) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus );
			label.setIcon( imageMap.get( ( String ) value ) );
			label.setHorizontalTextPosition( JLabel.RIGHT );
			label.setFont( font );
			return label;
		}
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new BigDataBrowserPlugin().run( null );
	}
}
