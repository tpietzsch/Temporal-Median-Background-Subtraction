package imglib;

public class MedianHistogram {
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
    public MedianHistogram(int window, int maxVal) {
        this.window = window;
        this.maxVal = maxVal;

        windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
        hist = new short[maxVal + 1]; //Gray-level histogram init at 0

        history = new short[window];
        hi = 0;
    }

    /**
     * Get current median.
     * It only makes sense to call this after adding {@code windowWidth} pixels.
     */
    public short get() {
        return median;
    }

    /**
     * Add new value to histogram
     */
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
                    (pixel > median && pixel2 > median)
                            || (pixel < median && pixel2 < median)
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
