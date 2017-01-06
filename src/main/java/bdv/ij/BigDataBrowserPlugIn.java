package bdv.ij;

import bdv.ij.util.SpringUtilities;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

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

	public static String domain = "/public";

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
			final JRadioButton publicButton = new JRadioButton( "Public" );
			publicButton.setActionCommand( "Public" );

			final JRadioButton privateButton = new JRadioButton( "Private" );
			privateButton.setActionCommand( "Private" );

			ButtonGroup group = new ButtonGroup();
			group.add( publicButton );
			group.add( privateButton );

			final JTextField id = new JTextField();
			final JPasswordField password = new JPasswordField();

			final JPanel userPanel = new JPanel( new SpringLayout() );
			userPanel.setBorder( BorderFactory.createTitledBorder( "" ) );

			userPanel.add( new JLabel( "Enter ID" ) );
			userPanel.add( id );
			userPanel.add( new JLabel( "Enter Password" ) );
			userPanel.add( password );

			SpringUtilities.makeCompactGrid( userPanel,
					2, 2, //rows, cols
					6, 6,        //initX, initY
					6, 6 );       //xPad, yPad

			for ( Component c : userPanel.getComponents() )
			{
				c.setEnabled( false );
			}

			Object[] inputFields = {
					publicButton,
					privateButton,
					userPanel,
					"Remote URL" };

			publicButton.addActionListener( new ActionListener()
			{
				@Override public void actionPerformed( ActionEvent e )
				{
					for ( Component c : userPanel.getComponents() )
					{
						c.setEnabled( !publicButton.isSelected() );
					}
				}
			} );

			privateButton.addActionListener( new ActionListener()
			{
				@Override public void actionPerformed( ActionEvent e )
				{
					for ( Component c : userPanel.getComponents() )
					{
						c.setEnabled( privateButton.isSelected() );
					}
				}
			} );

			publicButton.setSelected( true );

			final Object remoteUrl = JOptionPane.showInputDialog( null, inputFields, "BigDataServer",
					JOptionPane.QUESTION_MESSAGE, new ImageIcon( image ), null, serverUrl );

			if ( remoteUrl == null )
				return;

			serverUrl = remoteUrl.toString();

			if ( privateButton.isSelected() )
			{
				Authenticator.setDefault( new Authenticator()
				{
					protected PasswordAuthentication getPasswordAuthentication()
					{
						return new PasswordAuthentication( id.getText(), password.getPassword() );
					}
				} );
				domain = "/private";
			}
			else
			{
				domain = "/public";
			}
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

		String tag = "";
		try
		{
			// TODO: search datasets by tag name should be provided soon
			getDatasetList( serverUrl, nameList, tag );
		}
		catch ( final IOException e )
		{
			IJ.showMessage( "Error connecting to server at " + serverUrl );
			e.printStackTrace();
		}
		catch ( NoSuchAlgorithmException e )
		{
			e.printStackTrace();
		}
		catch ( KeyManagementException e )
		{
			e.printStackTrace();
		}
		createDatasetListUI( tag, serverUrl, nameList.toArray() );
	}

	class DataSet
	{
		private final String index;
		private final String name;
		private final String description;
		private final String tags;
		private final String sharedBy;
		private final Boolean isPublic;

		DataSet( String index, String name, String description, String tags, String sharedBy, Boolean isPublic )
		{
			this.index = index;
			this.name = name;
			this.description = description;
			this.tags = tags;
			this.sharedBy = sharedBy;
			this.isPublic = isPublic;
		}

		public String getIndex()
		{
			return index;
		}

		public String getName()
		{
			return name;
		}

		public String getDescription()
		{
			return description;
		}

		public String getTags()
		{
			return tags;
		}

		public String getSharedBy()
		{
			return sharedBy;
		}

		public Boolean isPublic()
		{
			return isPublic;
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

	private boolean getDatasetList( final String remoteUrl, final ArrayList< Object > nameList, final String searchTag ) throws IOException, NoSuchAlgorithmException, KeyManagementException
	{
		TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager()
				{
					public java.security.cert.X509Certificate[] getAcceptedIssuers()
					{
						return null;
					}

					public void checkClientTrusted( X509Certificate[] certs, String authType )
					{
					}

					public void checkServerTrusted( X509Certificate[] certs, String authType )
					{
					}

				}
		};

		SSLContext sc = SSLContext.getInstance( "SSL" );
		sc.init( null, trustAllCerts, new java.security.SecureRandom() );
		HttpsURLConnection.setDefaultSSLSocketFactory( sc.getSocketFactory() );

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier()
		{
			public boolean verify( String hostname, SSLSession session )
			{
				return true;
			}
		};
		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier( allHostsValid );

		String urlString = resolveRedirectedURL( remoteUrl + domain + "/tag/?name=" + URLEncoder.encode( searchTag, "UTF-8" ) );

		final URL url = new URL( urlString );

		URLConnection conn = url.openConnection();

		System.out.println( url );

		final InputStream is = conn.getInputStream();
		final JsonReader reader = new JsonReader( new InputStreamReader( is, "UTF-8" ) );

		reader.beginObject();

		while ( reader.hasNext() )
		{
			// skipping id
			reader.nextName();

			reader.beginObject();

			String id = null, datasetName = null, tags = null, description = null, thumbnailUrl = null, datasetUrl = null, sharedBy = null;
			Boolean isPublic = false;

			while ( reader.hasNext() )
			{
				final String name = reader.nextName();
				if ( name.equals( "name" ) )
					datasetName = reader.nextString();
				else if ( name.equals( "description" ) )
					description = reader.nextString();
				else if ( name.equals( "thumbnailUrl" ) )
					thumbnailUrl = reader.nextString();
				else if ( name.equals( "datasetUrl" ) )
					datasetUrl = reader.nextString();
				else if ( name.equals( "tags" ) )
					tags = reader.nextString();
				else if ( name.equals( "index" ) )
					id = reader.nextString();
				else if ( name.equals( "sharedBy" ) )
					sharedBy = reader.nextString();
				else if ( name.equals( "isPublic" ) )
					isPublic = reader.nextBoolean();
				else
					reader.skipValue();
			}

			if ( id != null )
			{
				nameList.add( new DataSet( id, datasetName, description, tags, sharedBy, isPublic ) );
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

	private String resolveRedirectedURL( String url )
	{
		try
		{
			URL obj = new URL( url );

			HttpURLConnection conn = ( HttpURLConnection ) obj.openConnection();
			conn.setReadTimeout( 5000 );
			conn.addRequestProperty( "Accept-Language", "en-US,en;q=0.8" );
			conn.addRequestProperty( "User-Agent", "Mozilla" );

			boolean redirect = false;

			// normally, 3xx is redirect
			int status = conn.getResponseCode();
			if ( status != HttpURLConnection.HTTP_OK )
			{
				if ( status == HttpURLConnection.HTTP_MOVED_TEMP
						|| status == HttpURLConnection.HTTP_MOVED_PERM
						|| status == HttpURLConnection.HTTP_SEE_OTHER )
					redirect = true;
			}

			if ( redirect )
			{

				// get redirect url from "location" header field
				String newUrl = conn.getHeaderField( "Location" );

				// get the cookie if need, for login
				String cookies = conn.getHeaderField( "Set-Cookie" );

				// open the new connnection again
				conn = ( HttpURLConnection ) new URL( newUrl ).openConnection();
				conn.setRequestProperty( "Cookie", cookies );
				conn.addRequestProperty( "Accept-Language", "en-US,en;q=0.8" );
				conn.addRequestProperty( "User-Agent", "Mozilla" );

				return newUrl;
			}

			return url;
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		return url;
	}

	private void createDatasetListUI( final String tag, final String remoteUrl, final Object[] values )
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
							BigDataViewer.view( ds.getName(), datasetUrlMap.get( ds.getIndex() ), new ProgressWriterIJ() );
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
		if ( tag.equals( "" ) )
			frame.setTitle( "BigDataServer Browser - " + remoteUrl );
		else
			frame.setTitle( "BigDataServer Browser - " + remoteUrl + " searched by " + tag );
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
				label.setIcon( imageMap.get( ds.getIndex() ) );

				StringBuilder sb = new StringBuilder( "<html><b>" + ds.getName() + "</b><br/> " );

				if ( domain.equals( "/private" ) )
				{
					sb.append( "<font color=green>Public: " + ds.isPublic() + "</font><br/>" );
					if ( !ds.sharedBy.isEmpty() )
						sb.append( "<font color=green>Shared by " + ds.getSharedBy() + "</font><br/>" );
				}

				sb.append( "Tags: <font color=blue>" + ds.getTags() + "</font><br/>" );
				sb.append( ds.getDescription() + "<br/></html>" );

				label.setText( sb.toString() );
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
