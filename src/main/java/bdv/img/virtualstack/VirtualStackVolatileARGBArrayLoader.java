package bdv.img.virtualstack;

import bdv.img.cache.CacheArrayLoader;
import ij.ImagePlus;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;

public class VirtualStackVolatileARGBArrayLoader implements CacheArrayLoader< VolatileIntArray >
{
	private final ImagePlus imp;

	public VirtualStackVolatileARGBArrayLoader( final ImagePlus imp )
	{
		this.imp = imp;
	}

	@Override
	public VolatileIntArray loadArray( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		final int channel = setup + 1;
		final int slice = ( int ) min[ 2 ] + 1;
		final int frame = timepoint + 1;
		final int[] data = ( int[] ) imp.getStack().getProcessor( imp.getStackIndex( channel, slice, frame ) ).getPixels();
		return new VolatileIntArray( data, true );
	}

	@Override
	public int getBytesPerElement()
	{
		return 4;
	}
}
