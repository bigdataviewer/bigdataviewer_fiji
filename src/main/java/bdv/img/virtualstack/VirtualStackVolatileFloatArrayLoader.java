package bdv.img.virtualstack;

import bdv.img.cache.CacheArrayLoader;
import ij.ImagePlus;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;

public class VirtualStackVolatileFloatArrayLoader implements CacheArrayLoader< VolatileFloatArray >
{
	private final ImagePlus imp;

	public VirtualStackVolatileFloatArrayLoader( final ImagePlus imp )
	{
		this.imp = imp;
	}

	@Override
	public VolatileFloatArray loadArray( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		final int channel = setup + 1;
		final int slice = ( int ) min[ 2 ] + 1;
		final int frame = timepoint + 1;
		final float[] data = ( float[] ) imp.getStack().getProcessor( imp.getStackIndex( channel, slice, frame ) ).getPixels();
		return new VolatileFloatArray( data, true );
	}

	@Override
	public int getBytesPerElement()
	{
		return 4;
	}
}
