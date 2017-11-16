/* Fast Temporal Median filter 
Copyright (c) 2014, Marcelo Augusto Cordeiro, Milstein Lab, University of Toronto
This ImageJ plugin was developed for the Milstein Lab at the University of Toronto,
with the help of Professor Josh Milstein during the summer of 2014, as part of the
Science Without Borders research opportunity program.

In 2017 Bram van den Broek and Rolf Harkes, Dutch Cancer Institute of Amsterdam 
implemented the algorithm in a maven .jar for easy deployment in Fiji (ImageJ2)
The window is changed from forward to central.
The empty bins in the histogram are removed beforehand to reduce memory usage.
The possibility to add an offset to the data before median removal to prevent rounding errors.

Used articles:
T.S.Huang et al. 1979 - Original algorithm for median calculation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
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
        menuPath = "Plugins>Temporal Median2")
public class TemporalMedian2 implements Command, Previewable {

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

    @Parameter(label = "split stack?", description = "split the image stack")
    private boolean split;

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
        for (int i = 0; i < t; i++) {
            temp = (short[]) stack1.getPixels(i + 1);
            for (int p = 0; p < pixels; p++) {
                data[i + (p - 1) * pixels] = temp[i];
                inihist[temp[i]] = true;
            }
        }
        log.debug("finished");
        log.debug("compress data");
        //compress data
        int addindex[] = compress(data, inihist, offset);
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
                    }
                }
            }; //end of thread creation
        }
        startAndJoin(threads);
        //make stack
        for (int i = 0; i < t; i++) {
            for (int p = 0; p < pixels; p++) {
                temp[i] = data[i + (p - 1) * pixels];
            }
            stack1.setPixels(temp, i + 1);
        }
        image1.setTitle("MEDFILT_" + image1.getTitle());
        image1.setDisplayMode(IJ.GRAYSCALE);
        image1.show();
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

    public static int[] compress(short[] data, boolean[] inihist, int offset) {
        int values = (int) 65536;
        int subtract[] = new int[values];
        int addindex[] = new int[values];
        int idx = 0;
        int subtractvalue = 0;
        for (int i = 0; i < values; i++) {
            if (inihist[i]) {
                subtract[i] = subtractvalue;
                addindex[idx] = subtractvalue + offset;
                idx++;
            } else {
                subtractvalue++;
            }
        }
        for (int i = 0; i < data.length;i++){
            data[i] -= subtract[data[i]];
        }
        return addindex;
    }

    /**
     * Main calculation loop. Manipulates the data in image.
     *
     * @param data
     * @param pix
     * @param T
     * @param addindex
     */
    public void substrmedian(short[] data, int pix, int T, int[] addindex) {
        int windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
        int step = pix * T;
        short pixel = 0;
        short pixel2 = 0;
        short hist[] = new short[addindex.length]; //Gray-level histogram init at 0
        short median = 0;//The median of this pixel
        short aux = 0;   //Marks the position of the median pixel in the column of the histogram, starting with 1
        for (int t = 0; t <= (T - window); t++) //over all timepoints
        {
            //statusService.showProgress(k, t);
            if (t == 0) //Building the first histogram
            {
                //log.debug("calculating first histogram");
                for (int t2 = 0; t2 <= window; t2++) //For each frame inside the window
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
                pixel = data[t + step]; //Old pixels remove from the histogram
                pixel2 = data[1 + t + window + step]; //New pixel, add them to the histogram
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

            if (t == 1) { //first median calculation. apply to k=1..window/2
                for (int fr = 1; fr <= windowC; fr++) {
                    data[step + fr] = (short) (data[step + fr] - median - addindex[median]);
                }
            } else { //apply to frame in centre of the medianwindow
                data[step + windowC] = (short) (data[step + windowC] - median - addindex[median]);
            }
        }
        for (int fr = 1 + T - windowC; fr <= T; fr++) { //apply last medan to remaining frames
            data[step + fr] = (short) (data[step + fr] - median - addindex[median]); //
        }
    }
}