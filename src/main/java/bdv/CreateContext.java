package bdv;

import org.scijava.Context;

public class CreateContext
{
	public static void main( String[] args )
	{
		Context context = new Context();
		System.out.println( "context = " + context );
	}
}
