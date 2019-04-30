package nl.nkiavl.rharkes;

import net.imglib2.Localizable;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class subtractMedian implements Positionable {
	private final int[] unrankArray;
	private final RandomAccess< UnsignedShortType > randA;
	private final short window;
	private final short windowC;
	private final short offset;
	private final long[] dims;
	private final short[] data;
	private final int[] medianValues;
	private final short[] datastart;
	private final medianFindingHistogram medFindHist;
	public subtractMedian(RandomAccess< UnsignedShortType > randA, int[] unrankArray,short window,short offset,long[] dims) {
		this.unrankArray=unrankArray;
		this.randA = randA;
		this.window = window;
		this.windowC = (short)((window - 1) / 2);
		this.offset = offset;
		this.dims = dims;
		this.data = new short[(int)dims[2]];
		this.medianValues = new int[(int) dims[2]-window+1];
		this.datastart = new short[window];
		this.medFindHist = new medianFindingHistogram((short) unrankArray.length, window);
	}
	public void run() {
		randA.setPosition(0,2);
        for (int i=0;i<data.length;i++) {
        	data[i]=randA.get().getShort();
        	randA.fwd(2);
        }
		for (int i=0;i<window;i++) {
			datastart[i]=data[i];
		}
		medFindHist.initializeHistogram(datastart);
		medianValues[0] = unrankArray[medFindHist.median];
		for (int t = 0; t < (dims[2] - window); t++) { //over all timepoints
				medFindHist.addRemoveValues(data[window+t], data[t]);
				medianValues[t+1] = unrankArray[medFindHist.median];
		}
		// now convert this pixel back from rank to original data and subtract the median
		// this must be done AFTER median calculation. Otherwise we mix ranked and unranked data.

		randA.setPosition(0,2);
		for (int t = 0; t < dims[2]; t++) {
			//take a step in time
			if (t <= windowC) {            //Apply first median to frame 0->windowC
				randA.get().setShort((short) (offset + unrankArray[randA.get().getShort()] - medianValues[0]));
			} else if (t<(dims[2] - windowC)) {  //Apply median from windowC back to the current frame 
				randA.get().setShort((short) (offset + unrankArray[randA.get().getShort()] - medianValues[t-windowC]));
			} else {                       //Apply last median to frame (T-windowC)->T
				randA.get().setShort((short) (offset + unrankArray[randA.get().getShort()] - medianValues[medianValues.length-1]));
			}
			randA.fwd(2);
		}
	}
	@Override
	public int numDimensions() {
		return randA.numDimensions();
	}

	@Override
	public void fwd(int d) {
		randA.fwd(d);
	}

	@Override
	public void bck(int d) {
		randA.bck(d);
	}

	@Override
	public void move(int distance, int d) {
		randA.move(distance,d);
	}

	@Override
	public void move(long distance, int d) {
		randA.move(distance, d);
	}

	@Override
	public void move(Localizable distance) {
		randA.move(distance);		
	}

	@Override
	public void move(int[] distance) {
		randA.move(distance);
	}

	@Override
	public void move(long[] distance) {
		randA.move(distance);
	}

	@Override
	public void setPosition(Localizable position) {
		randA.setPosition(position);
	}

	@Override
	public void setPosition(int[] position) {
		randA.setPosition(position);
	}

	@Override
	public void setPosition(long[] position) {
		randA.setPosition(position);
	}

	@Override
	public void setPosition(int position, int d) {
		randA.setPosition(position, d);
	}

	@Override
	public void setPosition(long position, int d) {
		randA.setPosition(position, d);
	}

}
