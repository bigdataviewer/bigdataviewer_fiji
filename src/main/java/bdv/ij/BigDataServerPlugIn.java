package bdv.ij;

import bdv.BigDataViewer;
import bdv.ij.util.BigDataServerUtils;
import bdv.ij.util.ProgressWriterIJ;
import bdv.viewer.ViewerOptions;
import mdbtools.libmdb.file;
import mpicbg.spim.data.SpimDataException;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Plugin(type = Command.class, menuPath = "Plugins>BigDataViewer>Open Dataset from BigDataServer")
public class BigDataServerPlugIn implements Command
{
	@Parameter(label = "Big Data Server URL")
	String urlServer = "http://fly.mpi-cbg.de:8081";

	@Parameter(label = "Dataset Name")
	String datasetName = "Drosophila";

	@Parameter(type = ItemIO.OUTPUT)
	BigDataViewer bdv;

	@Override
	public void run()
	{
		try {
			Map<String,String> BDSList = BigDataServerUtils.getDatasetList(urlServer);
			final String urlString = BDSList.get(datasetName);
			bdv = BigDataViewer.open( urlString, urlServer+" - "+datasetName, new ProgressWriterIJ(), ViewerOptions.options() );
		} catch (SpimDataException | IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(BigDataServerPlugIn.class, true,
				"urlServer","http://fly.mpi-cbg.de:8081",
				"datasetName", "Drosophila");
	}

}
