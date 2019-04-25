package nl.nkiavl.rharkes;
/* Fast Temporal Median filter 
(c) 2017 Rolf Harkes and Bram van den Broek, Netherlands Cancer Institute.
Based on the Fast Temporal Median Filter for ImageJ by the Milstein Lab.
It implementes the T.S.Huang algorithm in a maven .jar for easy deployment in Fiji (ImageJ2)
Calculating the median from the ranked data, and processing each pixel in parallel. 
The filter is intended for pre-processing of single molecule localization data.
v2.2.1

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

import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

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

    @Parameter(label = "Median window", description = "the frames for medan")
    private int window;

    @Parameter(label = "Added offset", description = "offset added to the new image")
    private short offset;

    public static void main(final String... args) throws Exception {

    }

    @Override
    public void run() {
    	//check input image
    	UnsignedShortType tempPixel = img.firstElement();
    	int bitdepth = tempPixel.getBitsPerPixel();
    	int values = 1 << bitdepth;
        if (bitdepth != 16) {
            log.error("BitDepth must 16 but was " + bitdepth);
            return;
        }
		final long[] dims = new long[img.numDimensions()];
		img.dimensions(dims);
		final int pixels = (int)Intervals.numElements(img);
		final int pixelsPerFrame = (int)dims[0] * (int)dims[1];

		log.info("nr of pixels = "+pixels);
		log.info("nr of pixels per frame = "+pixelsPerFrame);
		log.info("nr of bits per pixel = "+bitdepth);
		img.dimensions(dims);
		if ((dims[0]*dims[1]*dims[2]) > Math.pow(2, 32)){
            log.error("No support for more than 4.294.967.296 pixels. Please concider splitting the image.");return;
        }
		
        //sanity check on the input
        if (window >= (int) dims[2]) {
            window = (int) dims[2];
            if (window % 2 == 0) {window--;}
            log.warn("Window is larger than largest dimension. Reducing window to " + window);
        } else if (window % 2 == 0) {
            window++;
            log.warn("No support for even windows. Window = " + window);
        }
        
		//Allocate data storage
		statusService.showStatus("allocating datastorage");      
		final short data[] = new short[pixels];
		//Load data
		statusService.showStatus("loading data");
		int p = 0;
		short temp =0;
        boolean inihist[] = new boolean[values];
		for (UnsignedShortType s : Views.flatIterable(img)) {
			temp = s.getShort();
			inihist[temp] = true;
			data[p++] = temp; 
			if ((p % 2000) == 0) {
				statusService.showProgress(p, pixels);
			}
		}
        //compress data
        int unrankArray[] = denseRank(data, inihist);
        //do calculation in parallel per pixel
        final AtomicInteger ai = new AtomicInteger(0); //special unqique int for each thread
        final Thread[] threads = newThreadArray(); //all threads
        for (int ithread = 0; ithread < threads.length; ithread++) {
            threads[ithread] = new Thread() { //make threads
                {
                    setPriority(Thread.NORM_PRIORITY);
                }
                public void run() {
                    for (int i = ai.getAndIncrement(); i < pixelsPerFrame; i = ai.getAndIncrement()) { //get unique i
                        substrmedian(data, i, pixelsPerFrame, dims, unrankArray,window,offset);
                        if ((i%200)==0){
                            statusService.showProgress(i, pixelsPerFrame);
                        }
                    }
                }
            }; //end of thread creation
        }
        statusService.showStatus("calculating median");
        startAndJoin(threads);
        //make stack and count zeros
        statusService.showStatus("return data to image median");
        int zeros = 0;p=0;
        for (UnsignedShortType s : Views.flatIterable(img)) {
			temp = data[p++] ;
			if (temp<0){zeros++;temp=0;}
			s.setShort(temp);
			if ((p % 2000) == 0) {
				statusService.showProgress(p, pixels);
			}
		}
        if (zeros>0&&(zeros/dims[2])<10){log.info(zeros + " pixels went below zero. Concider increasing the offset." + (zeros/dims[2]) + " pixels per frame.");}
        if ((zeros/dims[2])>=10){log.warn(zeros + " pixels went below zero. Concider increasing the offset." + (zeros/dims[2]) + " pixels per frame.");}
        statusService.showStatus(1, 1, "FINISHED");
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
     * Dense ranking of the input array. 
     * It returns a decompression array to go from rank to value that has the 
     * length of the total number of unique values
     * Example: [0 5 6 5 10 6] becomes [0 1 2 1 3 2]
     *          The decompression array is [0 5 6 10] 
     * @see <a href = "https://en.wikipedia.org/wiki/Ranking#Dense_ranking_(%221223%22_ranking)">Dense ranking</a>
     * @param data    uint16 array with values that will be ranked
     * @param inihist boolean array of length 65536 that is true if the value exists
     * @return        decompression array that can reverse the compression step
     */
    public static int[] denseRank(short[] data, boolean[] inihist) {
        int values = (int) 65536;
        int subtract[] = new int[values];
        int unrankArrayF[] = new int[values]; //decompress array decompress[data] --> original data
        int idx = 0;
        int subtractvalue = 0;
        for (int i = 0; i < values; i++) {
            if (inihist[i]) {
                subtract[i] = subtractvalue;
                unrankArrayF[idx] = subtractvalue + idx;
                idx++;
            } else {
                subtractvalue++;
            }
        }
        //trim the unrank array
        int unrankArray[] = new int[idx];
        System.arraycopy(unrankArrayF, 0, unrankArray, 0, idx);
        //compress data
        for (int i = 0; i < data.length; i++) {
            data[i] -= subtract[data[i]];
        }
        return unrankArray;
    }

    /**
     * Subtract the temporal median from the ranked data.
     * Will change the input variable data.
     * Data is layout as t0x0y0, t1x0y0, t2x0y0 etc.
     *
     * @param data     ranked data with values from 0 to addindex.length
     * @param pix      pixel to analyse
     * @param dims     dimensions of the image data
     * @param addindex decompression array to go from ranked to original data
     * @param window   temporal window (must be odd)
     * @param offset   added to the each pixel before subtraction to prevent underflow
     */
    public void substrmedian(short[] data, int pix, int pixelsPerFrame, long[] dims, int[] unrankArray, int window, short offset) {
    	int T = (int) dims[2]; 
        int windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
        int tempres[] = new int[T-window+1]; //store decompressed medians for subtraction after the loop
        short pixel;
        short pixel2;
        short hist[] = new short[unrankArray.length]; //Gray-level histogram init at 0
        short median = 0;//The median of this pixel
        short aux = 0;   //Marks the position of the median pixel in the column of the histogram, starting with 1
        for (int t = 0; t <= (T - window); t++) //over all timepoints
        {
            if (t == 0) //Building the first histogram
            {
                for (int t2 = 0; t2 < window; t2++) //For each frame inside the window
                {
                    hist[data[pix + pixelsPerFrame*t2]]++; //Add it to the histogram
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
                pixel = data[pix + pixelsPerFrame*(t - 1)]; //Old pixel remove from the histogram
                pixel2 = data[pix + pixelsPerFrame*(t + window-1)]; //New pixel, add to the histogram
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
            tempres[t] = unrankArray[median];
        }
        // now convert this pixel back from rank to original data and subtract the median
        // this must be done AFTER median calculation. Otherwise we mix rank and original.
        for (int t = 0; t < T; t++) {
            if (t <= windowC) {            //Apply first median to frame 0->windowC
                data[pix + pixelsPerFrame*t] = (short) (offset+unrankArray[data[pix + pixelsPerFrame*t]] - tempres[0]);
            } else if (t<(T - windowC)) {  //Apply median from windowC back to the current frame 
                data[pix + pixelsPerFrame*t] = (short) (offset+unrankArray[data[pix + pixelsPerFrame*t]] - tempres[t-windowC]);
            } else {                      //Apply last median to frame (T-windowC)->T
                data[pix + pixelsPerFrame*t] = (short) (offset+unrankArray[data[pix + pixelsPerFrame*t]] - tempres[tempres.length-1]);
            }
        }
    }
}