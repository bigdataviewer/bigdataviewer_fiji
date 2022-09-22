/*-
 * #%L
 * Fiji plugins for starting BigDataViewer and exporting data.
 * %%
 * Copyright (C) 2014 - 2022 BigDataViewer developers.
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
package bdv.ij.export;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewSetup;

/**
 * A copy of a {@link ViewSetup} with another id.
 * Stores the {@link ViewSetup setup}'s original id and {@link SequenceDescription}.
 * For example, this can be used to access the original {@link ImgLoader}.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
@Deprecated
public class ViewSetupWrapper extends BasicViewSetup
{
	private final AbstractSequenceDescription< ?, ?, ? > sourceSequence;

	private final int sourceSetupId;

	public ViewSetupWrapper( final int id, final AbstractSequenceDescription< ?, ?, ? > sourceSequence, final BasicViewSetup sourceSetup )
	{
		super( id, sourceSetup.getName(), sourceSetup.getSize(), sourceSetup.getVoxelSize() );
		this.sourceSequence = sourceSequence;
		this.sourceSetupId = sourceSetup.getId();
	}

	public AbstractSequenceDescription< ?, ?, ? > getSourceSequence()
	{
		return sourceSequence;
	}

	public int getSourceSetupId()
	{
		return sourceSetupId;
	}
}
