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
import static java.lang.Runtime.getRuntime;
import static java.lang.System.arraycopy;
import static java.lang.Thread.NORM_PRIORITY;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
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
    //would be nice to allow any integer type in the future.

    @Parameter(label = "Median window", description = "the frames for medan")
    private short window;

    @Parameter(label = "Added offset", description = "offset added to the new image")
    private short offset;

    public static void main(final String... args) throws Exception {

    }

    @Override
    public void run() {
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
        if ((dims[0] * dims[1]* dims[2])>pow(2,32)){
            log.error("No support for more than 4.294.967.296 pixels. Please concider splitting the image.");return;
        }
        int unrankArray[] = denseRank(img);
        //do calculation  per pixel
        long[] dimensionsROI = {1, 1, dims[2]};
        for (int i = 0; i < pixels; i++) { 
            long x = 2+(i % dims[0]);
            long y = 2+(i/dims[0]);
            log.info("pixel x = "+x);
            log.info("pixel y = "+y);
            long[] startPointROI = {x, y, 0};
            RandomAccessibleInterval<UnsignedShortType> myROI = Views.offsetInterval(img, startPointROI, dimensionsROI);
            substrmedian(myROI, dims[2], window,offset,unrankArray);
            if ((i%200)==0){
                statusService.showProgress(i, (int) pixels);
            }
        }
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
        int n_cpus = getRuntime().availableProcessors();
        return new Thread[n_cpus];
    }

    public static void startAndJoin(Thread[] threads) {
        for (int ithread = 0; ithread < threads.length; ++ithread) {
            threads[ithread].setPriority(NORM_PRIORITY);
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
     * @param img
     * @see <a href = "https://en.wikipedia.org/wiki/Ranking#Dense_ranking_(%221223%22_ranking)">Dense ranking</a>
     * @return        decompression array that can reverse the compression step
     */
    public int[] denseRank(Img< UnsignedShortType > img) {
        int values = (int) 65536;
        boolean[] doesValueExist = new boolean[values];
        //go over all pixels to see what values exist
        for( UnsignedShortType t : img ){
            doesValueExist[t.get()]=true;
        }
        //create the unrank array and subtract array. 
        int subtract[] = new int[values];
        int unrankArrayFull[] = new int[values]; //unrank array unrankArray[data] --> original data
        int idx = 0;
        int subtractvalue = 0;
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
        for ( UnsignedShortType t : img ) {
            t.set(t.get()-subtract[t.get()]);
        }
        return unrankArray;
    }

    /**
     * Subtract the temporal median from the ranked data.
     * Will change the input variable data.
     * Data is layout as t0x0y0, t1x0y0, t2x0y0 etc.
     *
     * @param rai         random access interval set to the pixel over entire time
     * @param T           nr of timepoints
     * @param window      temporal window (must be odd)
     * @param offset      added to the each pixel before subtraction to prevent underflow
     * @param unrankArray to convert rank values back to pixel values
     */
    public void substrmedian(RandomAccessibleInterval< UnsignedShortType > rai,long T, short window, short offset,int[] unrankArray) {
        RandomAccess<UnsignedShortType> r = rai.randomAccess();
        int windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
        int MedVals[] = new int[(int)T-window+1]; //store decompressed medians for subtraction after the loop
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
                    r.setPosition(t2, 2);
                    hist[r.get().getInteger()]++; //Add it to the histogram
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
                r.setPosition(t-1, 2);
                pixel = (short) r.get().getInteger(); //Old pixel remove from the histogram
                r.setPosition(t+window-1, 2);
                pixel2 = (short) r.get().getInteger(); //New pixel, add to the histogram
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
        log.info("unrankArray[0] = "+unrankArray[0]);
        log.info("unrankArray[1] = "+unrankArray[1]);
        log.info("unrankArray[end-1] = "+unrankArray[unrankArray.length-2]);
        log.info("unrankArray[end] = "+unrankArray[unrankArray.length-1]);
        log.info("MedVals[0] = "+MedVals[0]);
        log.info("MedVals[1] = "+MedVals[1]);
        log.info("MedVals[end-1] = "+MedVals[MedVals.length-2]);
        log.info("MedVals[end] = "+MedVals[MedVals.length-1]);
        // now convert this pixel back from rank to original data and subtract the median
        // this must be done AFTER median calculation. Otherwise we mix rank and original.
        for (int t = 0; t < T; t++) {
            r.setPosition(t, 2);
            final UnsignedShortType currPix = r.get();
            if (t <= windowC) {            //Apply first median to frame 0->windowC
                currPix.set( (short) (offset + unrankArray[currPix.getInteger()] - MedVals[0]));
            } else if (t<(T - windowC)) {  //Apply median from windowC back to the current frame 
                currPix.set( (short) (offset + unrankArray[currPix.getInteger()] - MedVals[t-windowC]));
            } else {                       //Apply last median to frame (T-windowC)->T
                
                currPix.set( (short) (offset + unrankArray[currPix.getInteger()] - MedVals[MedVals.length-1]));
            }
        }
    }
    long pow (long a, int b)
    {
        if ( b == 0)        return 1;
        if ( b == 1)        return a;
        if ((b%2)==0)       return     pow ( a * a, b/2); //even a=(a^2)^b/2
        else                return a * pow ( a * a, b/2); //odd  a=a*(a^2)^b/2

    }
}