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
