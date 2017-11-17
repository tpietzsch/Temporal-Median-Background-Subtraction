/* Fast Temporal Median filter 
In 2017 Rolf Harkes and Bram van den Broek, Netherlands Cancer Institute, 
implemented the T.S.Huang algorithm in a maven .jar for easy deployment in Fiji (ImageJ2)
The data is read to a single array and each pixel is processed in parallel. 
The filter is intended for pre-processing of single molecule localization data.
A dataset of 180x180x25000 pixels was filtered in 15 seconds on a regular PC.

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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageConverter;
import ij.process.StackConverter;
import java.util.concurrent.atomic.AtomicInteger;

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
        menuPath = "Plugins>Temporal Median")
public class TemporalMedian implements Command, Previewable {

    @Parameter
    private LogService log;

    @Parameter
    private StatusService statusService;

    @Parameter(label = "Select image", description = "the image field")
    private ImagePlus image1;

    @Parameter(label = "Median window", description = "the frames for medan")
    private short window;

    @Parameter(label = "Added offset", description = "offset added to the new image")
    private short offset;

    //@Parameter(label = "Ignore this", description = "Do not use this field", visibility = ItemVisibility.INVISIBLE)
    //private ImagePlus IGNOREimage; //never used, need for getting a list selection for image1
    public static void main(final String... args) throws Exception {

    }

    @Override
    public void run() {
        double bitdepth = (double) image1.getBitDepth(); //declare double for raising to power
        if (bitdepth != 16) {
            log.warn("BitDepth must 16 but was " + image1.getBitDepth() + ". Will convert now");
            if (ImageConverter.getDoScaling()) {
                ImageConverter.setDoScaling(false);
                new StackConverter(image1).convertToGray16();
                ImageConverter.setDoScaling(true);
            } else {
                new StackConverter(image1).convertToGray16();
            }
        }
        //get datastack
        image1.deleteRoi(); //remove to prevent a partial duplicate
        ImageStack stack1 = image1.getStack();
        int w = stack1.getWidth();
        int h = stack1.getHeight();
        int t = stack1.getSize();
        int pixels = w * h;
        statusService.showProgress(0, pixels);
        //sanity check on the input
        if (window % 2 == 0) {
            window++;
            log.warn("No support for even windows. Window = " + window);
        }
        if (window >= (short) t) {
            window = (short) t;
            log.warn("Window is larger than largest dimension. Reducing window to " + window);
        }
        //allocate data storage
        log.debug("allocating datastorage");
        short data[] = new short[t * pixels];
        short temp[] = new short[pixels];
        log.debug("finished");
        log.debug("loading data from stack");
        boolean inihist[] = new boolean[65536];
        for (int i = 0; i < t; i++) { //all timepoints
            temp = (short[]) stack1.getPixels(i + 1); 
            for (int p = 0; p < pixels; p++) {//all pixels
                data[i + p * t] = temp[p];
                inihist[temp[p]] = true;
            }
        }
        log.debug("finished");
        log.debug("compress data");
        //compress data
        int addindex[] = compress(data, inihist);
        log.debug("finished");
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
                        substrmedian(data, i, t, addindex);
                        if ((i%200)==0){
                            statusService.showProgress(i, pixels);
                        }
                    }
                }
            }; //end of thread creation
        }
        startAndJoin(threads);
        //make stack and count zeros
        int zeros = 0;
        for (int i = 0; i < t; i++) { //all timepoints
            for (int p = 0; p < pixels; p++) { //all pixels
                temp[p] = data[i + p * t];
                if (temp[p]<0){zeros++;temp[p]=0;}
            }
            stack1.setPixels(temp.clone(), (i+1));
        }
        if (zeros>0&&(zeros/t)<10){log.info(zeros + " pixels went below zero. Concider increasing the offset." + (zeros/t) + " pixels per frame.");}
        if ((zeros/t)>=10){log.warn(zeros + " pixels went below zero. Concider increasing the offset." + (zeros/t) + " pixels per frame.");}
        image1.setStack(stack1);
        image1.setTitle("MEDFILT_" + image1.getTitle());
        image1.setDisplayMode(IJ.GRAYSCALE);
        image1.show();
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

    public static int[] compress(short[] data, boolean[] inihist) {
        int values = (int) 65536;
        int subtract[] = new int[values];
        int decompressF[] = new int[values]; //decompress array decompress[data] --> original data
        int idx = 0;
        int subtractvalue = 0;
        for (int i = 0; i < values; i++) {
            if (inihist[i]) {
                subtract[i] = subtractvalue;
                decompressF[idx] = subtractvalue + idx;
                idx++;
            } else {
                subtractvalue++;
            }
        }
        //trim the decompress array
        int decompress[] = new int[idx];
        System.arraycopy(decompressF, 0, decompress, 0, idx);
        //compress data
        for (int i = 0; i < data.length; i++) {
            data[i] -= subtract[data[i]];
        }
        return decompress;
    }

    /**
     * Main calculation loop. Manipulates the data.
     *
     * @param data
     * @param pix
     * @param T
     * @param addindex
     */
    public void substrmedian(short[] data, int pix, int T, int[] addindex) {
        int windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
        int tempres[] = new int[T-window+1]; //store decompressed medians for subtraction after the loop
        int step = pix * T;
        short pixel = 0;
        short pixel2 = 0;
        short hist[] = new short[addindex.length]; //Gray-level histogram init at 0
        short median = 0;//The median of this pixel
        short aux = 0;   //Marks the position of the median pixel in the column of the histogram, starting with 1
        for (int t = 0; t <= (T - window); t++) //over all timepoints
        {
            if (t == 0) //Building the first histogram
            {
                for (int t2 = 0; t2 < window; t2++) //For each frame inside the window
                {
                    hist[data[step + t2]]++; //Add it to the histogram
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
                pixel = data[t + step - 1]; //Old pixel remove from the histogram
                pixel2 = data[t + window + step-1]; //New pixel, add to the histogram
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
            tempres[t] = addindex[median];
        }
        // now decompress data, subtract median and put data back (must be done AFTER median calculation)
        for (int t = 0; t < T; t++) {
            if (t <= windowC) {            //Apply first median to frame 0->windowC
                data[t+step] = (short) (offset+addindex[data[t+step]] - tempres[0]);
            } else if (t<(T - windowC)) {  //Apply median from windowC back to the current frame 
                data[t+step] = (short) (offset+addindex[data[t+step]] - tempres[t-windowC]);
            } else {                      //Apply last median to frame (T-windowC)->T
                data[t+step] = (short) (offset+addindex[data[t+step]] - tempres[tempres.length-1]);
            }
        }
    }
}