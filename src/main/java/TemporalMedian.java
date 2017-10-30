/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import ij.ImagePlus;
import ij.ImageStack;

import java.util.stream.IntStream;

import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Adds two datasets using the ImgLib2 framework.
 */
@Plugin(type = Command.class, headless = true,
        menuPath = "NKI>Temporal Median")
public class TemporalMedian implements Command, Previewable {

    private ImagePlus image2 = null;

    @Parameter
    private LogService log;

    @Parameter
    private StatusService statusService;

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private final String header = "This Presents two buttons";

    @Parameter(label = "Select image", description = "the image field")
    private ImagePlus image1;

    @Parameter(label = "Median window", description = "the frames for medan")
    private short window;

    //@Parameter(label = "Ignore this", description = "Do not use this field", visibility = ItemVisibility.INVISIBLE)
    //private ImagePlus IGNOREimage; //never used, need for getting a list selection for image1
    public static void main(final String... args) throws Exception {

    }

    @Override
    public void run() {
        if (image1.getBitDepth() == 16) {
            image1.deleteRoi(); //otherwise we create a partial duplicate
            image2 = image1.duplicate();
            image1.restoreRoi();
            substrmean();
            image2.show();
        } else {
            log.error("BitDepth must be 16 but was " + image1.getBitDepth());
        }

    }

    @Override
    public void cancel() {
        log.info("Cancelled");
    }

    @Override
    public void preview() {
        log.info("previews median");
        statusService.showStatus(header);
    }

    /**
     * Main calculation loop. Manipulates the data in image.
     */
    private void substrmean() {
        final int dims[] = image1.getDimensions(); //0=width, 1=height, 2=nChannels, 3=nSlices, 4=nFrames
        final int dimension = dims[0] * dims[1];
        short[] pixels = new short[dimension]; //pixel data from image1
        short[] pixels2 = new short[dimension]; //pixel data from image1
        short hist[][] = new short[dimension][65536]; //Gray-level histogram init at 0
        short[] median = new short[dimension]; //Array to save the median pixels
        short[] aux = new short[dimension];    //Marks the position of each median pixel in the column of the histogram, starting with 1

        final ImageStack stack = image1.getStack();
        final ImageStack stack2 = image2.getStack();
        log.info(String.valueOf(dims[4]));
        for (int k = 1; k <= (dims[4] - window); k++) //Each passing creates one median frame
        {

            //median = median.clone(); //Cloning the median, or else the changes would overlap the previous median
            if (k == 1) //Building the first histogram
            {
                for (int i = 1; i <= window; i++) //For each frame inside the window
                {
                    pixels = (short[]) (stack.getPixels(i + k - 1)); //Save all the pixels of the frame "i+k-1" in "pixels" (starting with 1)
                    for (int j = 0; j < dimension; j++) //For each pixel in this frame
                    {
                        hist[j][pixels[j]]++; //Add it to the histogram
                    }
                }
                for (int i = 0; i < dimension; i++) //Calculating the median
                {
                    short count = 0, j = -1;
                    while (count < (window / 2)) //Counting the histogram, until it reaches the median
                    {
                        j++;
                        count += hist[i][j];
                    }
                    aux[i] = (short) (count - (int) (Math.ceil(window / 2)) + 1);
                    median[i] = j;
                }
            } else {
                pixels = (short[]) (stack.getPixels(k - 1)); //Old pixels, remove them from the histogram
                pixels2 = (short[]) (stack.getPixels(k + window - 1)); //New pixels, add them to the histogram
                for (int i = 0; i < dimension; i++) //Calculating the new median
                {
                    hist[i][pixels[i]]--; //Removing old pixel
                    hist[i][pixels2[i]]++; //Adding new pixel
                    if (!(((pixels[i] > median[i])
                            && (pixels2[i] > median[i]))
                            || ((pixels[i] < median[i])
                            && (pixels2[i] < median[i]))
                            || ((pixels[i] == median[i])
                            && (pixels2[i] == median[i])))) //Add and remove the same pixel, or pixels from the same side, the median doesn't change
                    {
                        int j = median[i];
                        if ((pixels2[i] > median[i]) && (pixels[i] < median[i])) //The median goes right
                        {
                            if (hist[i][median[i]] == aux[i]) //The previous median was the last pixel of its column in the histogram, so it changes
                            {
                                j++;
                                while (hist[i][j] == 0) //Searching for the next pixel
                                {
                                    j++;
                                }
                                median[i] = (short) (j);
                                aux[i] = 1; //The median is the first pixel of its column
                            } else {
                                aux[i]++; //The previous median wasn't the last pixel of its column, so it doesn't change, just need to mark its new position
                            }
                        } else if ((pixels[i] > median[i]) && (pixels2[i] < median[i])) //The median goes left
                        {
                            if (aux[i] == 1) //The previous median was the first pixel of its column in the histogram, so it changes
                            {
                                j--;
                                while (hist[i][j] == 0) //Searching for the next pixel
                                {
                                    j--;
                                }
                                median[i] = (short) (j);
                                aux[i] = hist[i][j]; //The median is the last pixel of its column
                            } else {
                                aux[i]--; //The previous median wasn't the first pixel of its column, so it doesn't change, just need to mark its new position
                            }
                        } else if (pixels2[i] == median[i]) //new pixel = last median
                        {
                            if (pixels[i] < median[i]) //old pixel < last median, the median goes right
                            {
                                aux[i]++; //There is at least one pixel above the last median (the one that was just added), so the median doesn't change, just need to mark its new position
                            }								//else, absolutely nothing changes
                        } else //pixels[i]==median[i], old pixel = last median
                        {
                            if (pixels2[i] > median[i]) //new pixel > last median, the median goes right
                            {
                                if (aux[i] == (hist[i][median[i]] + 1)) //The previous median was the last pixel of its column, so it changes
                                {
                                    j++;
                                    while (hist[i][j] == 0) //Searching for the next pixel
                                    {
                                        j++;
                                    }
                                    median[i] = (short) (j);
                                    aux[i] = 1; //The median is the first pixel of its column
                                }
                                //else, absolutely nothing changes
                            } else //pixels2[i]<median[i], new pixel < last median, the median goes left
                            {
                                if (aux[i] == 1) //The previous median was the first pixel of its column in the histogram, so it changes
                                {
                                    j--;
                                    while (hist[i][j] == 0) //Searching for the next pixel
                                    {
                                        j--;
                                    }
                                    median[i] = (short) (j);
                                    aux[i] = hist[i][j]; //The median is the last pixel of its column
                                } else {
                                    aux[i]--; //The previous median wasn't the first pixel of its column, so it doesn't change, just need to mark its new position
                                }
                            }
                        }
                    }
                }
            }

            //Subtracting the median
            pixels2 = (short[]) (stack2.getPixels(k));
            for (int j = 0; j < dimension; j++) {
                pixels2[j] = median[j];
                if (pixels2[j] < 0) {
                    pixels2[j] = 0;
                }
            }

            if ((k % 1000) == 0) {
                System.gc(); //Calls the Garbage Collector every 1000 frames
            }
        }

    }
}
