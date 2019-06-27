package imglib;

import java.util.Arrays;
import java.util.Random;

import static ucar.units.StandardUnitFormatConstants.T;

public class Playground {

    static class MedianHistogram {
        private final int window;
        private final int maxVal;

        private final int windowC; //0 indexed sorted array has median at this position.
        private final short[] hist; //Gray-level histogram init at 0

        private int t = 0;
        private short median = -1;//The median of this pixel
        private short aux = 0;   //Marks the position of the median pixel in the column of the histogram, starting with 1

        /**
         * @param window window width of the median filter
         * @param maxVal maximum value occurring in the input data
         */
        MedianHistogram(int window, int maxVal) {
            this.window = window;
            this.maxVal = maxVal;

            windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
            hist = new short[maxVal + 1]; //Gray-level histogram init at 0

            history = new short[window];
            hi = 0;
        }

        public short get() {
            return median;
        }

        public void add(final short pixel2) {
            final short pixel = record(pixel2);

            if (t < window) //Building the first histogram
            {
                ++hist[pixel2];
                if (++t == window) {
                    int count = 0, j = -1;
                    while (count <= windowC) //Counting the histogram, until it reaches the median
                    {
                        j++;
                        count += hist[j];
                    }
                    aux = (short) (windowC + hist[j] + 1 - count); //position in the bin. 1 is lowest.
                    median = (short) j;
                }
            } else {
                hist[pixel]--; //Removing old pixel
                hist[pixel2]++; //Adding new pixel
                if (!(
                           (pixel > median  && pixel2 > median)
                        || (pixel < median  && pixel2 < median)
                        || (pixel == median && pixel2 == median)
                    )) //Add and remove the same pixel, or pixel from the same side, the median doesn't change
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
                        }                                //else, absolutely nothing changes
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
        }

        private final short[] history;
        private int hi;

        private short record(short value) {
            final short old = history[hi];
            history[hi] = value;
            if (++hi >= window)
                hi = 0;
            return old;
        }
    }

    static void testMedian() {
        final int inputSize = 20;
        final int maxVal = 9;
        final int windowSize = 3;

        final short[] input = new short[inputSize];
        Random random = new Random();
        for (int i = 0; i < inputSize; ++i)
//            input[i] = (short) ( (inputSize-i-1) / 2 );
//            input[i] = (short) (inputSize-i-1);
            input[i] = (short) random.nextInt(maxVal + 1);
        System.out.println(print(input));

        // compute medians using MedianHistogram
        final int windowC = (windowSize - 1) / 2;
        final short[] hmedians = new short[inputSize];
        Arrays.fill(hmedians, (short) -1);
        MedianHistogram mh = new MedianHistogram(windowSize, maxVal);
        for (int i = 0; i < windowSize - 1; ++i) {
            mh.add(input[i]);
        }
        for (int i = windowSize - 1; i < inputSize; ++i) {
            mh.add(input[i]);
            hmedians[i - windowC] = mh.get();
        }

        // compute medians using sorting
        final short[] medians = new short[inputSize];
        Arrays.fill(medians, (short) -1);
        for (int i = 0; i <= inputSize - windowSize; ++i) {
            final short[] w = Arrays.copyOfRange(input, i, i + windowSize);
            Arrays.sort(w);
            final short median = w[windowC];
            medians[i + windowC] = median;
        }

//        System.out.println(print(input));
        System.out.println(print(hmedians));
        System.out.println(print(medians));
    }

    static String print(short[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < array.length; ++i) {
            sb.append(String.format("%3d", array[i]));
            if (i < array.length - 1)
                sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    public static void main(String[] args) {
//        final ImagePlus imp = IJ.openImage("/Users/pietzsch/Desktop/random-smalltif");
//        final Img< UnsignedShortType > img = ImageJFunctions.wrap(imp);

        testMedian();
    }
}
