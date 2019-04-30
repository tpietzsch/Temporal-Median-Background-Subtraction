package nl.nkiavl.rharkes;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

public class testMedianFindingHistogram {

	@Test
	public void testMedianFindingHistogramBasic1(){
		//generate {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22} median=11; aux=1;
		short values = 23; 
		short[] data1 = new short[values];
		Short[] data2 = new Short[values];
		for (int i = 0;i<data1.length;i++) {
			data1[i]=(short)i;
			data2[i]=(short)i;
		}
		short M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)11,M2);
		medianFindingHistogram medFind = new medianFindingHistogram(data1,values);

		assertEquals("medianFindingHistogram Fails",(short)11,medFind.median);
		
		data2[22]=0; //remove a 22, add a 0
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)10,M2);
		medFind.addRemoveValues((short)0, (short)22);
		assertEquals("medianFindingHistogram Fails",(short)10,medFind.median);
		data2[21]=0; //remove a 21, add a 0
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)9,M2);
		medFind.addRemoveValues((short)0, (short)21);
		assertEquals("medianFindingHistogram Fails",(short)9,medFind.median);
	}
	@Test
	public void testMedianFindingHistogramBasic2(){
		//generate {0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11}  median=5; aux=2;
		short values = 23; 
		short[] data1 = new short[values];
		Short[] data2 = new Short[values];
		for (int i = 0;i<data1.length;i++) {
			data1[i]=(short)(i/2);
			data2[i]=(short)(i/2);
		}
		short M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)5,M2);
		medianFindingHistogram medFind = new medianFindingHistogram(data1,values);
		assertEquals("medianFindingHistogram Fails",(short)5,medFind.median);
		
		data2[22]=0; //remove a 11, add a 0
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)5,M2);
		medFind.addRemoveValues((short)0, (short)11);
		assertEquals("medianFindingHistogram Fails",(short)5,medFind.median);
		data2[21]=0; //remove a 10, add a 0
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)4,M2);
		medFind.addRemoveValues((short)0, (short)10);
		assertEquals("medianFindingHistogram Fails",(short)4,medFind.median);
	}
	@Test
	public void testMedianFindingHistogramBasic3(){
		//generate {0,0,0,1,1,1,2,2,2,3,3,3,4,4,4,5,5,5,6,6,6,7,7}  median=3; aux=3;
		short values = 23; 
		short[] data1 = new short[values];
		Short[] data2 = new Short[values];
		for (int i = 0;i<data1.length;i++) {
			data1[i]=(short)(i/3);
			data2[i]=(short)(i/3);
		}
		short M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)3,M2);
		medianFindingHistogram medFind = new medianFindingHistogram(data1,values);
		assertEquals("medianFindingHistogram Fails",(short)3,medFind.median);
		
		data2[22]=0; //remove a 7, add a 0
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)3,M2);
		medFind.addRemoveValues((short)0, (short)7);
		assertEquals("medianFindingHistogram Fails",(short)3,medFind.median);
		data2[21]=0; //remove a 7, add a 0
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)3,M2);
		medFind.addRemoveValues((short)0, (short)7);
		assertEquals("medianFindingHistogram Fails",(short)3,medFind.median);
	}
	@Test
	public void testMedianFindingHistogramBasic4(){
		//generate {0,0,0,1,1,1,2,2,2,3,3,3,4,4,4,5,5,5,6,6,6,7,7}  median=3; aux=3;
		short values = 23; 
		short[] data1 = new short[values];
		Short[] data2 = new Short[values];
		for (int i = 0;i<data1.length;i++) {
			data1[i]=(short)(i/3);
			data2[i]=(short)(i/3);
		}
		short M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)3,M2);
		medianFindingHistogram medFind = new medianFindingHistogram((short) values,(short)data1.length);
		medFind.initializeHistogram(data1);
		assertEquals("medianFindingHistogram med fails",(short)3,medFind.median);
		
		data2[0]=7; //remove a 0, add a 3
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)4,M2);
		medFind.addRemoveValues((short)7, (short)0);
		assertEquals("medianFindingHistogram Fails",(short)4,medFind.median);
		data2[1]=7; //remove a 0, add a 3
		M2 = quickSelect.select(data2, data2.length/2);
		assertEquals("quickSelect Fails",(short)4,M2);
		medFind.addRemoveValues((short)7, (short)0);
		assertEquals("medianFindingHistogram Fails",(short)4,medFind.median);
	}
	@Test
	public void testMedianFindingHistogram1(){
		for (int repeats = 0;repeats<1000;repeats++) {
			//initialize histogram
			int values = 1000;
			int dataLength = 30001;
			Random generator=new Random();
			Short[] data = new Short[dataLength];
			short[] data2 = new short[dataLength];
			for (int i = 0;i<data.length;i++) {
				data[i] = (short) generator.nextInt(values);
				data2[i] = data[i];
			}
			short M = quickSelect.select(data, data.length/2);
			medianFindingHistogram medFind = new medianFindingHistogram((short) values,(short)dataLength);
			medFind.initializeHistogram(data2);
			assertEquals(M,medFind.median);
			//add and remove value and recalculate median
			short rep = (short) generator.nextInt(dataLength);
			short removeValue = data[rep];
			short addValue = (short) generator.nextInt(values);
			data[rep] = (Short) addValue;
			
			short M2 = quickSelect.select(data, data.length/2);
			medFind.addRemoveValues(addValue, removeValue);
			assertEquals(M2,medFind.median);
		}
	}
	@Test
	public void testMedianFindingHistogram2(){
			//initialize histogram
			int values = 20000;
			int dataLength = 3001;
			Random generator=new Random();
			Short[] data = new Short[dataLength];
			short[] data2 = new short[dataLength];
			for (int i = 0;i<data.length;i++) {
				data[i] = (short) generator.nextInt(values);
				data2[i] = data[i];
			}
			short M = quickSelect.select(data, data.length/2);
			medianFindingHistogram medFind = new medianFindingHistogram(data2,(short) values);
			assertEquals(M,medFind.median);
		for (int repeats = 0;repeats<1000;repeats++) {
			//add and remove value and recalculate median
			short rep = (short) generator.nextInt(dataLength);
			short removeValue = data[rep];
			short addValue = (short) generator.nextInt(values);
			data[rep] = (Short) addValue;
			
			short M2 = quickSelect.select(data, data.length/2);
			medFind.addRemoveValues(addValue, removeValue);
			assertEquals(M2,medFind.median);
		}
	}
	@Test
	public void timeMedianFindingHistogram() {
		int values = 1000;      //unique intensities
		int histLength = 50000; //frames
		short window = 101;     //window size
		Random generator=new Random();
		//generate data
		Short[] data = new Short[histLength];
		short[] data2 = new short[histLength];
		for (int i = 0;i<data.length;i++) {
			data[i] = (short) generator.nextInt(values);
			data2[i] = data[i];
		}
		short[] medians1 = new short[histLength-window];
		short[] medians2 = new short[histLength-window];
		//Time quickSelect
		long start = System.currentTimeMillis();
		Short[] dataWindow = new Short[window]; 
		for (int i = 0;i<medians1.length;i++) {
			for (int j = i;j<(i+window);j++) {
				dataWindow[j-i]=data[j];
			}
			medians1[i]=quickSelect.select(dataWindow, dataWindow.length/2);
		}
		long end = System.currentTimeMillis();
		long runTimeQuickSelect = end-start;
		System.out.println("DEBUG: quickSelect took " + runTimeQuickSelect + " MilliSeconds");
		//Time medianFindingHistogram
		start = System.currentTimeMillis();
		short[] dataWindow2 = new short[window]; 
		for (int j = 0;j<window;j++) {
			dataWindow2[j]=data2[j];
		}
		medianFindingHistogram medFind = new medianFindingHistogram(dataWindow2,(short) values);
		medians2[0]=medFind.median;
		for (int i=1;i<medians2.length;i++) {
			medFind.addRemoveValues(data2[i+window-1], data2[i-1]);
			medians2[i]=medFind.median;
		}
		end = System.currentTimeMillis();
		long runTimeMedianFindingHistogram = end-start;
		System.out.println("DEBUG: medianFindingHistogram took " + runTimeMedianFindingHistogram+ " MilliSeconds");
		System.out.println("DEBUG: medianFindingHistogram " + runTimeQuickSelect/runTimeMedianFindingHistogram+ " times faster");
		
		assertArrayEquals(medians1,medians2);
	}

}
