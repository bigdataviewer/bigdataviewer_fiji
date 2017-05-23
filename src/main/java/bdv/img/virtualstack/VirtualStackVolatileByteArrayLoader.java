package bdv.img.virtualstack;

import bdv.img.cache.CacheArrayLoader;
import ij.ImagePlus;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;

public class VirtualStackVolatileByteArrayLoader implements CacheArrayLoader< VolatileByteArray >
{
	private final ImagePlus imp;

	public VirtualStackVolatileByteArrayLoader( final ImagePlus imp )
	{
		this.imp = imp;
	}

	@Override
	public VolatileByteArray loadArray( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		final int channel = setup + 1;
		final int slice = ( int ) min[ 2 ] + 1;
		final int frame = timepoint + 1;
		final byte[] data = ( byte[] ) imp.getStack().getProcessor( imp.getStackIndex( channel, slice, frame ) ).getPixels();
		return new VolatileByteArray( data, true );
	}

	@Override
	public int getBytesPerElement()
	{
		return 1;
	}
}
