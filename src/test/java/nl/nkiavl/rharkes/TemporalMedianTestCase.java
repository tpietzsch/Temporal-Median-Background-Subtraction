package nl.nkiavl.rharkes;
import static org.junit.Assert.*;
import org.junit.Test;

import io.scif.img.IO;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import nl.nkiavl.rharkes.TemporalMedian;

public class TemporalMedianTestCase {
	TemporalMedian TM = new TemporalMedian(); 
	@Test
	public void testDenseRank() {
		Img< UnsignedShortType > img = IO.openImgs( System.getProperty("user.dir")+"\\src\\test\\java\\testfile.tif", new ArrayImgFactory<>( new UnsignedShortType() ) ).get( 0 );
		int[] unrankArray = new int[1000];
		for (int i=0;i<1000;i++) {
			unrankArray[i] = i+1;
		}
		assertArrayEquals(unrankArray,TM.denseRank(img,65535));
	}
	@Test
	public void testSubtractMedian() {
		Img< UnsignedShortType > img = IO.openImgs( System.getProperty("user.dir")+"\\src\\test\\java\\testfile.tif", new ArrayImgFactory<>( new UnsignedShortType() ) ).get( 0 );
		Img< UnsignedShortType > res = IO.openImgs( System.getProperty("user.dir")+"\\src\\test\\java\\resultfile.tif", new ArrayImgFactory<>( new UnsignedShortType() ) ).get( 0 );
		
		int[] unrankArray = TM.denseRank(img,65535);
		short window = 101;
		short offset = 100;
		long[] dims = {20,20,1000};
		for (int i = 0;i<400;i++) {
			TM.subtractMedian(img,i,dims,window,offset,unrankArray);
		}
		
		//check equality
		final Cursor< UnsignedShortType > curI = img.cursor();
		final Cursor< UnsignedShortType > curR = res.cursor();
		
		while ( curI.hasNext() )
        {
			curI.fwd();
            curR.fwd();
            assertEquals(curI.get().getShort(),curR.get().getShort());
        }
	}
}
