package bdv.ij;

import bdv.BigDataViewer;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.event.WindowEvent;

@Plugin(type = Command.class, menuPath = "Plugins>BigDataViewer>Close BigDataViewer")
public class BigDataViewerClosePlugin implements Command {

    @Parameter(label = "BigDataViewer window to close", type = ItemIO.INPUT)
    BigDataViewer bdv;

    @Override
    public void run() {
        JFrame frame = bdv.getViewerFrame();
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }
}
