package bdv.ij.export.imgloader;

import java.util.HashMap;

import mpicbg.spim.data.legacy.LegacyBasicImgLoaderWrapper;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.type.numeric.integer.UnsignedShortType;


/**
 * This {@link ImgLoader} loads images that represent a 3D stack in a single
 * file, for example in tif, lsm, or czi format. It is constructed with a list
 * of image filenames and the number of setups (e.g. angles). Then, to laod the
 * image for a given {@link ViewDescription}, its index in the filename list is computed as
 * <code>view.getSetupIndex() + numViewSetups * view.getTimepointIndex()</code>.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
@Deprecated
public class StackImageLoader extends LegacyBasicImgLoaderWrapper< UnsignedShortType, LegacyStackImageLoader >
{
	public StackImageLoader( final HashMap< ViewId, String > filenames, final boolean useImageJOpener )
	{
		super( new LegacyStackImageLoader( filenames, useImageJOpener ) );
	}
}
