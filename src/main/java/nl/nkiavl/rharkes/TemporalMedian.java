package nl.nkiavl.rharkes;
/* Fast Temporal Median filter 
(c) 2017 Rolf Harkes and Bram van den Broek, Netherlands Cancer Institute.
Based on the Fast Temporal Median Filter for ImageJ by the Milstein Lab.
It implementes the T.S.Huang algorithm in a maven .jar for easy deployment in Fiji (ImageJ2)
Calculating the median from the ranked data, and processing each pixel in parallel. 
The filter is intended for pre-processing of single molecule localization data.
v2.3

Used articles:
T.S.Huang et al. 1979 - Original algorithm for median calculation

This software is released under the GPL v3. You may copy, distribute and modify 
the software as long as you track changes/dates in source files. Any 
modifications to or software including (via compiler) GPL-licensed code 
must also be made available under the GPL along with build & install instructions.
https://www.gnu.org/licenses/gpl-3.0.en.html

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
import static java.lang.System.arraycopy;

import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Subtracts the temporal median
 */
@Plugin(type = Command.class, headless = true,
menuPath = "Plugins>Process>Temporal Median Background Subtraction")
public class TemporalMedian implements Command, Previewable {

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter(label = "Select image", description = "the image field")
	private Img<UnsignedShortType> img;
	int values;

	@Parameter(label = "Median window", description = "the frames for medan")
	private short window;

	@Parameter(label = "Added offset", description = "offset added to the new image")
	private short offset;

	public static void main(final String... args) throws Exception {

	}

	@Override
	public void run() {
		UnsignedShortType temp = img.firstElement();
    	values = 1 << temp.getBitsPerPixel();
		final long[] dims = new long[img.numDimensions()];
		img.dimensions(dims);
		long pixels = dims[0] * dims[1];
		//sanity check on the input
		if (window >= dims[2]) {
			window = (short) dims[2];
			if (window % 2 == 0) {window--;}
			log.warn("Window is larger than largest dimension. Reducing window to " + window);
		} else if (window % 2 == 0) {
			window++;
			log.warn("No support for even windows. Window = " + window);
		}
		
		int unrankArray[] = denseRank(img,values);
		//do calculation in parallel per pixel
		final AtomicInteger ai = new AtomicInteger(0); //special unqique int for each thread
		final Thread[] threads = newThreadArray(); //all threads
		for (int ithread = 0; ithread < threads.length; ithread++) {
			threads[ithread] = new Thread() { //make threads
				{
					setPriority(Thread.NORM_PRIORITY);
				}
				public void run() {
					for (int i = ai.getAndIncrement(); i < pixels; i = ai.getAndIncrement()) { //get unique i
						subtractMedian(img ,i ,dims, window, offset, unrankArray);
						if ((i%2000)==0){
							statusService.showStatus("Pixel ("+i+"/" +pixels+")");
							statusService.showProgress(i, (int)pixels);
						}
					}
				}
			}; //end of thread creation
		}
		startAndJoin(threads);
		//check for zeros to warn for underflow
		statusService.showStatus(1, 1, "FINISHED");
		//refresh imagej
	}

	@Override
	public void cancel() {
		log.debug("Cancelled");
	}

	@Override
	public void preview() {
		log.debug("previews median");
	}

	//two methods for concurrent calculation
	private Thread[] newThreadArray() {
		int n_cpus = Runtime.getRuntime().availableProcessors();
		return new Thread[n_cpus];
	}

	public static void startAndJoin(Thread[] threads) {
		for (int ithread = 0; ithread < threads.length; ++ithread) {
			threads[ithread].setPriority(Thread.NORM_PRIORITY);
			threads[ithread].start();
		}
		try {
			for (int ithread = 0; ithread < threads.length; ++ithread) {
				threads[ithread].join();
			}
		} catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		}
	}

	/**
	 * Dense ranking of the input image. 
	 * It returns a decompression array to go from rank to value that has the 
	 * length of the total number of unique values.
	 * Example: [0 5 6 5 10 6] becomes [0 1 2 1 3 2]
	 *          The decompression array is [0 5 6 10] 
	 * @param img input image
	 * @param values maximum possible values
	 * @return integer array to convert the ranked image back
	 * @see <a href = "https://en.wikipedia.org/wiki/Ranking#Dense_ranking_(%221223%22_ranking)">Dense ranking</a>
	 * */ 
	public int[] denseRank(Img< UnsignedShortType > img, int values) {
		boolean[] doesValueExist = new boolean[values];
		Cursor<UnsignedShortType> cursor = img.cursor();
		//go over all pixels to see what values exist
		if (statusService != null) {statusService.showStatus("ranking image (1/3)...");}
		while (cursor.hasNext()) {
			doesValueExist[cursor.next().getInteger()]=true;
		}
		//create the unrank array and subtract array. 
		int subtract[] = new int[values];
		int unrankArrayFull[] = new int[values]; //unrank array unrankArray[data] --> original data (int, since short cannot handle >2^15)
		int idx = 0;
		int subtractvalue = 0;
		if (statusService != null) {statusService.showStatus("ranking image (2/3)...");}
		for (int i = 0; i < values; i++) {
			if (doesValueExist[i]) {
				subtract[i] = subtractvalue;
				unrankArrayFull[idx] = subtractvalue + idx;
				idx++;
			} else {
				subtractvalue++;
			}
		}
		//trim the unranking array
		int unrankArray[] = new int[idx];
		arraycopy(unrankArrayFull, 0, unrankArray, 0, idx);
		//rank data
		if (statusService != null) {statusService.showStatus("ranking image (3/3)...");}
		cursor.reset();
		while (cursor.hasNext()) {
			UnsignedShortType t = cursor.next();
			t.setInteger(t.getInteger()-subtract[t.getInteger()]);
		}
		if (statusService != null) {statusService.showStatus("Finished!");}
		return unrankArray;
	}

	/**
	 * Subtract the temporal median from a single pixel
	 *
	 * @param img         Input image ; pixels will be altered.
	 * @param i           Pixel to analyse
	 * @param dims        Dimensions of the image
	 * @param window      temporal window (must be odd)
	 * @param offset      added to the each pixel before subtraction to prevent underflow
	 * @param unrankArray to convert rank values back to pixel values
	 */
	public void subtractMedian(Img< UnsignedShortType > img,int i, long[] dims, short window, short offset,int[] unrankArray) {
		RandomAccess<UnsignedShortType> randA1 = img.randomAccess();
		RandomAccess<UnsignedShortType> randA2 = img.randomAccess();
		int T = (int) dims[2];
		long x = i%dims[0];
		long y = (i-x)/dims[0];
		long[] startposition = {x,y,0};
		randA1.setPosition(startposition);
		randA2.setPosition(startposition);
		int windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
		int MedVals[] = new int[T-window+1]; //store unranked medians for subtraction after the loop
		short pixel;
		short pixel2;
		short hist[] = new short[unrankArray.length]; //Gray-level histogram init at 0
		short median = 0;//The median of this pixel
		short aux = 0;   //Marks the position of the median pixel in the column of the histogram, starting with 1
		for (int t = 0; t <= (T - window); t++) { //over all timepoints
			if (t == 0) //Building the first histogram
			{
				for (int t2 = 0; t2 < window; t2++) //For each frame inside the window
				{
					hist[randA1.get().getShort()]++; //Add it to the histogram
					randA1.fwd(2); //take a step in time
				}
				short count = 0, j = -1;
				while (count <= windowC) //Counting the histogram, until it reaches the median
				{
					j++;
					count += hist[j];
				}
				aux = (short) (count - (int) windowC); //position in the bin. 1 is lowest.
				median = j;
			} else {
				pixel = randA2.get().getShort(); //Old pixel remove from the histogram
				randA2.fwd(2);
				pixel2 = randA1.get().getShort(); //New pixel, add to the histogram
				randA1.fwd(2);

				hist[pixel]--; //Removing old pixel
				hist[pixel2]++; //Adding new pixel
				if (!(((pixel > median)
						&& (pixel2 > median))
						|| ((pixel < median)
								&& (pixel2 < median))
						|| ((pixel == median)
								&& (pixel2 == median)))) //Add and remove the same pixel, or pixel from the same side, the median doesn't change
				{
					int j = median;
					if ((pixel2 > median) && (pixel < median)) //The median goes right
					{
						if (hist[median] == aux) //The previous median was the last pixel of its column in the histogram, so it changes
						{
							j++;
							while (hist[j] == 0) //Searching for the next pixel
							{
								j++;
							}
							median = (short) (j);
							aux = 1; //The median is the first pixel of its column
						} else {
							aux++; //The previous median wasn't the last pixel of its column, so it doesn't change, just need to mark its new position
						}
					} else if ((pixel > median) && (pixel2 < median)) //The median goes left
					{
						if (aux == 1) //The previous median was the first pixel of its column in the histogram, so it changes
						{
							j--;
							while (hist[j] == 0) //Searching for the next pixel
							{
								j--;
							}
							median = (short) (j);
							aux = hist[j]; //The median is the last pixel of its column
						} else {
							aux--; //The previous median wasn't the first pixel of its column, so it doesn't change, just need to mark its new position
						}
					} else if (pixel2 == median) //new pixel = last median
					{
						if (pixel < median) //old pixel < last median, the median goes right
						{
							aux++; //There is at least one pixel above the last median (the one that was just added), so the median doesn't change, just need to mark its new position
						}								//else, absolutely nothing changes
					} else //pixel==median, old pixel = last median
					{
						if (pixel2 > median) //new pixel > last median, the median goes right
						{
							if (aux == (hist[median] + 1)) //The previous median was the last pixel of its column, so it changes
							{
								j++;
								while (hist[j] == 0) //Searching for the next pixel
								{
									j++;
								}
								median = (short) (j);
								aux = 1; //The median is the first pixel of its column
							}
							//else, absolutely nothing changes
						} else //pixel2<median, new pixel < last median, the median goes left
						{
							if (aux == 1) //The previous median was the first pixel of its column in the histogram, so it changes
							{
								j--;
								while (hist[j] == 0) //Searching for the next pixel
								{
									j--;
								}
								median = (short) (j);
								aux = hist[j]; //The median is the last pixel of its column
							} else {
								aux--; //The previous median wasn't the first pixel of its column, so it doesn't change, just need to mark its new position
							}
						}
					}
				}
			}
			MedVals[t] = unrankArray[median];
		}
		// now convert this pixel back from rank to original data and subtract the median
		// this must be done AFTER median calculation. Otherwise we mix ranked and unranked data.

		randA1.setPosition(startposition);
		UnsignedShortType currPix = randA1.get();
		for (int t = 0; t < T; t++) {
			//take a step in time
			if (t <= windowC) {            //Apply first median to frame 0->windowC
				currPix.setShort((short) (offset + unrankArray[currPix.getShort()] - MedVals[0]));
			} else if (t<(T - windowC)) {  //Apply median from windowC back to the current frame 
				currPix.setShort((short) (offset + unrankArray[currPix.getShort()] - MedVals[t-windowC]));
			} else {                       //Apply last median to frame (T-windowC)->T
				currPix.setShort((short) (offset + unrankArray[currPix.getShort()] - MedVals[MedVals.length-1]));
			}
			randA1.move(1, 2);
		}
	}
}