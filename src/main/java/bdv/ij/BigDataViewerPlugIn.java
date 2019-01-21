package bdv.ij;

import java.io.File;

import net.imagej.ImageJ;
import mpicbg.spim.data.SpimDataException;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import bdv.BigDataViewer;
import bdv.ij.util.ProgressWriterIJ;
import bdv.viewer.ViewerOptions;

@Plugin(type = Command.class, menuPath = "Plugins>BigDataViewer>Open XML/HDF5")
public class BigDataViewerPlugIn implements Command
{
	@Parameter(label = "XML File", type = ItemIO.INPUT)
	File file;

	@Parameter(type = ItemIO.OUTPUT)
	BigDataViewer bdv;

	@Override
	public void run()
	{
		try {
			bdv = BigDataViewer.open( file.getAbsolutePath(), file.getName(), new ProgressWriterIJ(), ViewerOptions.options() );
		} catch (SpimDataException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.get(CommandService.class).run(BigDataViewerPlugIn.class,true);
	}

}
