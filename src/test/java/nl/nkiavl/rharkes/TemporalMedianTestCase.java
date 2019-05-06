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
		assertArrayEquals(unrankArray,TM.denseRank(img));
	}
	
	@Test
	public void testSubtractMedian() {
		Img< UnsignedShortType > img = IO.openImgs( System.getProperty("user.dir")+"\\src\\test\\java\\testfile.tif", new ArrayImgFactory<>( new UnsignedShortType() ) ).get( 0 );
		Img< UnsignedShortType > res = IO.openImgs( System.getProperty("user.dir")+"\\src\\test\\java\\resultfile.tif", new ArrayImgFactory<>( new UnsignedShortType() ) ).get( 0 );
		long start = System.currentTimeMillis();
		int[] unrankArray = TM.denseRank(img);
		short window = 101;
		short offset = 100;
		long[] dims = {20,20,1000};
		SubtractMedian subMed = new SubtractMedian(img.randomAccess(),unrankArray,window,offset,dims);
		for (long x = 0; x < dims[0]; x++) {
			for (long y = 0; y<dims[1];y++) {
				subMed.setPosition(x, 0);
				subMed.setPosition(y, 1);
				subMed.run();
			}
		}
		long end = System.currentTimeMillis();
		long RunTime = end-start;
		System.out.println("DEBUG: subtractMedian took "+RunTime+" ms");
		//check equality
		final Cursor< UnsignedShortType > curI = img.cursor();
		final Cursor< UnsignedShortType > curR = res.cursor();
		
		while ( curI.hasNext() )
        {
			curI.fwd();
            curR.fwd();
            assertEquals(curR.get().getShort(),curI.get().getShort());
        }
	}
}
