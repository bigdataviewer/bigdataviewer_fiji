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
package bdv.ij.export.imgloader;

import java.util.HashMap;

import org.scijava.Context;
import org.scijava.app.AppService;
import org.scijava.app.StatusService;

import ij.ImagePlus;
import io.scif.SCIFIOService;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import mpicbg.spim.data.legacy.LegacyBasicImgLoader;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.meta.ImgPlus;
import net.imglib2.type.numeric.integer.UnsignedByteType;
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
public class LegacyStackImageLoader implements LegacyBasicImgLoader< UnsignedShortType >
{
	private final ImgOpener opener;

	private final ArrayImgFactory< UnsignedShortType > factory;

	private final UnsignedShortType type;

	final HashMap< ViewId, String > filenames;

	private boolean useImageJOpener;

	public LegacyStackImageLoader( final HashMap< ViewId, String > filenames, final boolean useImageJOpener )
	{
		this.filenames = filenames;
		this.useImageJOpener = useImageJOpener;
		opener = useImageJOpener ? null : new ImgOpener( new Context( SCIFIOService.class, AppService.class, StatusService.class ) );
		factory = new ArrayImgFactory<>();
		type = new UnsignedShortType();
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		final String fn = filenames.get( view );
		if ( useImageJOpener )
		{
			final ImagePlus imp = new ImagePlus( fn );
			if ( imp.getType() == ImagePlus.GRAY16 )
				return new ImgPlus<>( ImageJFunctions.wrapShort( imp ) );
			else if ( imp.getType() == ImagePlus.GRAY8 )
			{
				System.out.println( "wrapping" );
				return new ImgPlus<>(
					new ImgView<>(
							Converters.convert(
									( RandomAccessibleInterval<UnsignedByteType> ) ImageJFunctions.wrapByte( imp ),
									new Converter< UnsignedByteType, UnsignedShortType >() {
										@Override
										public void convert( final UnsignedByteType input, final UnsignedShortType output )
										{
											output.set( input.get() );
										}
									},
									new UnsignedShortType()
							), null ) );
			}
			else
				useImageJOpener = false;
		}

		try
		{
			return opener.openImg( fn, factory, type );
		}
		catch ( final ImgIOException e )
		{
			throw new RuntimeException( e );
		}
	}

	@Override
	public UnsignedShortType getImageType()
	{
		return type;
	}
}
