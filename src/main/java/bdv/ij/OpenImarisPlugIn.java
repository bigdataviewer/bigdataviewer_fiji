package bdv.ij;

import java.io.File;
import java.io.IOException;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.imaris.Imaris;
import bdv.spimdata.SpimDataMinimal;
import bdv.viewer.ViewerOptions;

@Plugin(type = Command.class,menuPath = "Plugins>BigDataViewer>Open Imaris (experimental)")
public class OpenImarisPlugIn implements Command
{
    @Parameter(label = "Imaris File", type = ItemIO.INPUT)
    File file;

    @Parameter(type = ItemIO.OUTPUT)
    BigDataViewer bdv;

	@Override
	public void run()
	{
	    try
		{
			final SpimDataMinimal spimData = Imaris.openIms( file.getAbsolutePath() );
			bdv = BigDataViewer.open( spimData, file.getName(), new ProgressWriterIJ(), ViewerOptions.options() );
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}
	}
}
