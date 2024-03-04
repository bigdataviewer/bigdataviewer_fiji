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
