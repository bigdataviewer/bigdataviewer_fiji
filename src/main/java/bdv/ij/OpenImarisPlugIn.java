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

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.imaris.Imaris;
import bdv.spimdata.SpimDataMinimal;
import bdv.viewer.ViewerOptions;
import ij.ImageJ;
import ij.Prefs;

@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Open Imaris")
public class OpenImarisPlugIn implements Command
{
	static String lastDatasetPath = "";

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		new OpenImarisPlugIn().run();
	}
	@Override
	public void run()
	{
		if ( ij.Prefs.setIJMenuBar )
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		File file = null;

		if ( Prefs.useJFileChooser )
		{
			final JFileChooser fileChooser = new JFileChooser();
			fileChooser.setSelectedFile( new File( lastDatasetPath ) );
			fileChooser.setFileFilter( new FileFilter()
			{
				@Override
				public String getDescription()
				{
					return "ims files";
				}

				@Override
				public boolean accept( final File f )
				{
					if ( f.isDirectory() )
						return true;
					if ( f.isFile() )
					{
				        final String s = f.getName();
				        final int i = s.lastIndexOf('.');
				        if (i > 0 &&  i < s.length() - 1) {
				            final String ext = s.substring(i+1).toLowerCase();
				            return ext.equals( "ims" );
				        }
					}
					return false;
				}
			} );

			final int returnVal = fileChooser.showOpenDialog( null );
			if ( returnVal == JFileChooser.APPROVE_OPTION )
				file = fileChooser.getSelectedFile();
		}
		else // use FileDialog
		{
			final FileDialog fd = new FileDialog( ( Frame ) null, "Open", FileDialog.LOAD );
			fd.setDirectory( new File( lastDatasetPath ).getParent() );
			fd.setFile( new File( lastDatasetPath ).getName() );
			final AtomicBoolean workedWithFilenameFilter = new AtomicBoolean( false );
			fd.setFilenameFilter( new FilenameFilter()
			{
				private boolean firstTime = true;

				@Override
				public boolean accept( final File dir, final String name )
				{
					if ( firstTime )
					{
						workedWithFilenameFilter.set( true );
						firstTime = false;
					}

					final int i = name.lastIndexOf( '.' );
					if ( i > 0 && i < name.length() - 1 )
					{
						final String ext = name.substring( i + 1 ).toLowerCase();
						return ext.equals( "ims" );
					}
					return false;
				}
			} );
			fd.setVisible( true );
			if ( !workedWithFilenameFilter.get() )
			{
				fd.setFilenameFilter( null );
				fd.setVisible( true );
			}
			final String filename = fd.getFile();
			if ( filename != null )
			{
				file = new File( fd.getDirectory() + filename );
			}
		}

		if ( file != null )
		{
			try
			{
				lastDatasetPath = file.getAbsolutePath();
				final SpimDataMinimal spimData = Imaris.openIms( file.getAbsolutePath() );
				BigDataViewer.open( spimData, file.getName(), new ProgressWriterIJ(), ViewerOptions.options() );
			}
			catch ( final IOException e )
			{
				throw new RuntimeException( e );
			}
		}
	}
}
