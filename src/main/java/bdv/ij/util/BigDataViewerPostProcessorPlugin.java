package bdv.ij.util;

import bdv.BigDataViewer;
import org.scijava.Priority;
import org.scijava.module.Module;
import org.scijava.module.process.AbstractPostprocessorPlugin;
import org.scijava.module.process.PostprocessorPlugin;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;


@Plugin(type = PostprocessorPlugin.class, priority = Priority.VERY_LOW - 1)
public class BigDataViewerPostProcessorPlugin extends AbstractPostprocessorPlugin {
    @Parameter(required = false)
    private UIService ui;

    @Parameter
    ObjectService os;

    @Override
    public void process(Module module) {
        if (ui == null) {
            // no UIService available for display
            return;
        }
        // stores all output BigDataViewer objects into ObjectService
        module.getInfo().outputs().forEach(output -> {
            if (output.isOutput()) {
                final Object o = module.getOutput(output.getName());
                if (o instanceof BigDataViewer) {
                    os.addObject(o);
                }
            }
        });
    }

}
