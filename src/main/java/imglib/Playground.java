package imglib;

import java.util.Arrays;
import java.util.Random;

public class Playground {

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
