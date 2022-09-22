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
package bdv.ij.export.tiles;

import java.io.File;
import java.util.List;

import mpicbg.spim.data.legacy.LegacyBasicImgLoaderWrapper;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import bdv.ij.export.tiles.CellVoyagerDataExporter.ChannelInfo;

public class TileImgLoader extends LegacyBasicImgLoaderWrapper< UnsignedShortType, LegacyTileImgLoader >
{
	public TileImgLoader( final File imageIndexFile, final List< ChannelInfo > channelInfos )
	{
		super( new LegacyTileImgLoader( imageIndexFile, channelInfos ) );
	}
}
