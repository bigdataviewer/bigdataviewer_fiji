package bdv.ij;

import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
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

import mpicbg.spim.data.SpimDataException;

import org.apache.commons.lang.StringUtils;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;

import com.google.gson.stream.JsonReader;

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
			image = ImageIO.read( getClass().getResourceAsStream( "/fiji.png" ) );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}

		if ( null == arg || arg == "" )
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

		final ArrayList< Object > nameList = new ArrayList< Object >();
		String category = "";
		try
		{

			if ( serverUrl.indexOf( "/category/" ) > -1 )
			{
				String[] tokens = serverUrl.split( "/category/" );
				serverUrl = tokens[ 0 ];
				category = tokens[ 1 ];
			}
			getDatasetList( serverUrl, nameList, category );
		}
		catch ( final IOException e )
		{
			IJ.showMessage( "Error connecting to server at " + serverUrl );
			e.printStackTrace();
		}
		createDatasetListUI( category, serverUrl, nameList.toArray() );
	}

	class DataSet
	{
		private final String name;
		private final String description;
		private final String category;

		DataSet( String name, String description, String category )
		{

			this.name = name;
			this.description = description;
			this.category = category;
		}

		public String getName()
		{
			return name;
		}

		public String getDescription()
		{
			return description;
		}

		public String getCategory()
		{
			return category;
		}
	}

	class Category
	{
		private final String name;

		Category( String name )
		{
			this.name = name;
		}

		public String getName()
		{
			return name;
		}
	}

	private boolean getDatasetList( final String remoteUrl, final ArrayList< Object > nameList, final String searchCategory ) throws IOException
	{
		// Get JSON string from the server
		final URL url = new URL( remoteUrl + "/json/?category=" + URLEncoder.encode( searchCategory, "UTF-8" ) );

		final InputStream is = url.openStream();
		final JsonReader reader = new JsonReader( new InputStreamReader( is, "UTF-8" ) );

		String prevCategory = null;

		reader.beginObject();

		while ( reader.hasNext() )
		{
			// skipping id
			reader.nextName();

			reader.beginObject();

			String id = null, category = null, description = null, thumbnailUrl = null, datasetUrl = null;
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
				else if ( name.equals( "category" ) )
					category = reader.nextString();
				else if ( name.equals( "index" ) )
					reader.nextString();
				else
					reader.skipValue();
			}

			if ( prevCategory == null || !prevCategory.equals( category ) )
			{
				prevCategory = category;
				nameList.add( new Category( prevCategory ) );
			}

			if ( id != null )
			{
				nameList.add( new DataSet( id, description, category ) );
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

	private void createDatasetListUI( final String category, final String remoteUrl, final Object[] values )
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
					final Object cell = list.getModel().getElementAt( index );

					if ( cell instanceof DataSet )
					{
						DataSet ds = ( DataSet ) cell;

						try
						{
							BigDataViewer.view( datasetUrlMap.get( ds.getName() ), new ProgressWriterIJ() );
						}
						catch ( final SpimDataException e )
						{
							e.printStackTrace();
						}
					}
				}
			}
		} );

		final JScrollPane scroll = new JScrollPane( list );
		scroll.setPreferredSize( new Dimension( 600, 800 ) );

		final JFrame frame = new JFrame();
		if ( category.equals( "" ) )
			frame.setTitle( "BigDataServer Browser - " + remoteUrl );
		else
			frame.setTitle( "BigDataServer Browser - " + remoteUrl + "/category/" + category );
		frame.add( scroll );
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.pack();
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );
	}

	public class ThumbnailListRenderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;

		Font font = new Font( "helvetica", Font.PLAIN, 12 );

		@Override
		public Component getListCellRendererComponent(
				final JList list, final Object value, final int index,
				final boolean isSelected, final boolean cellHasFocus )
		{

			final JLabel label = ( JLabel ) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus );

			if ( value instanceof Category )
			{
				Category category = ( Category ) value;
				label.setText( category.getName() );
				label.setFont( label.getFont().deriveFont( Font.BOLD, 26 ) );
				label.setBorder( BorderFactory.createEmptyBorder( 0, 5, 0, 0 ) );
			}
			else
			{
				DataSet ds = ( DataSet ) value;
				label.setIcon( imageMap.get( ds.getName() ) );
				label.setText( "<html><b>" + ds.getName() + "</b><br/> " + ds.getDescription() + "</html>" );
				label.setHorizontalTextPosition( JLabel.RIGHT );
				label.setFont( font );
			}

			return label;
		}
	}

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		//new BigDataBrowserPlugIn().run( "/Users/moon/Desktop/local.bdv" );
		new BigDataBrowserPlugIn().run( null );
	}
}
