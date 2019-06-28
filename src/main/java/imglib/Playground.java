package imglib;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import java.util.Arrays;
import java.util.Random;

public class Playground {

    public static final int U16_SIZE = 65536;

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

    static void testMedianExt() {
        final int inputSize = 20;
        final int maxVal = 9;
        final int window = 3;

        final short[] input = new short[inputSize];
        Random random = new Random();
        for (int i = 0; i < inputSize; ++i)
//            input[i] = (short) ( (inputSize-i-1) / 2 );
//            input[i] = (short) (inputSize-i-1);
            input[i] = (short) random.nextInt(maxVal + 1);

        // compute medians using MedianHistogram
        final short[] hmedians = new short[inputSize];

        Img<UnsignedShortType> img = ArrayImgs.unsignedShorts(input, 1, 1, inputSize);
        final RankMap rankmap = RankMap.build(img);
        final RandomAccessibleInterval<UnsignedShortType> ranked = Converters.convert((RandomAccessibleInterval) img, rankmap::toRanked, new UnsignedShortType());
        final RandomAccessibleInterval<UnsignedShortType> med = ArrayImgs.unsignedShorts(hmedians, 1, 1, inputSize);
        final int windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.
        for (int y = 0; y < img.dimension(1); ++y) {
            for (int x = 0; x < img.dimension(0); ++x) {
                RandomAccess<UnsignedShortType> front = ranked.randomAccess();
                front.setPosition(new int[]{x, y, 0});

                RandomAccess<UnsignedShortType> back = med.randomAccess();
                back.setPosition(new int[]{x, y, 0});

                MedianHistogram filter = new MedianHistogram(window, rankmap.getMaxRank());

                // read the first window ranked pixels into median filter
                for (int i = 0; i < window; ++i) {
                    filter.add((short) front.get().get());
                    front.fwd(2);
                }
                // write current median for windowC+1 pixels
                for (int i = 0; i <= windowC; ++i) {
                    back.get().set(rankmap.fromRanked(filter.get()));
                    back.fwd(2);
                }


                final int zSize = (int)img.dimension(2);
                final int zSteps = zSize - window;
                for (int i = 0; i < zSteps; ++i) {
                    filter.add((short) front.get().get());
                    front.fwd(2);
                    back.get().set(rankmap.fromRanked(filter.get()));
                    back.fwd(2);
                }

                // write current median for windowC pixels
                for (int i = 0; i < windowC; ++i) {
                    back.get().set(rankmap.fromRanked(filter.get()));
                    back.fwd(2);
                }
            }
        }

        // compute medians using sorting
        final short[] medians = new short[inputSize];
        Arrays.fill(medians, (short) -1);
        for (int i = 0; i <= inputSize - window; ++i) {
            final short[] w = Arrays.copyOfRange(input, i, i + window);
            Arrays.sort(w);
            final short median = w[windowC];
            medians[i + windowC] = median;
        }

        System.out.println("input:   " + print(input));
        System.out.println("hmedians:" + print(hmedians));
        System.out.println("medians :" + print(medians));
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

    static class RankMap
    {
        private final short[] inputToRanked;
        private final short[] rankedToInput;
        private static int maxRank;

        public RankMap(final short[] inputToRanked, final short[] rankedToInput) {
            this.inputToRanked = inputToRanked;
            this.rankedToInput = rankedToInput;
        }

        public static RankMap build(IterableInterval<UnsignedShortType> input)
        {
            final boolean inihist[] = new boolean[U16_SIZE];
            input.forEach(t -> inihist[t.get()] = true);

            final int mapSize = U16_SIZE;
            final short[] inputToRanked = new short[ mapSize ];
            final short[] rankedToInput = new short[ mapSize ];
            int r = 0;
            for ( int i = 0; i < inihist.length; ++i ) {
                if ( inihist[ i ] )
                {
                    rankedToInput[r] = (short) i;
                    inputToRanked[i] = (short) r;
                    ++r;
                }
            }
            maxRank = r - 1;

            return new RankMap(inputToRanked, rankedToInput);
        }

        public void toRanked(final UnsignedShortType in, final UnsignedShortType out) {
            out.set(inputToRanked[in.get()]);
        }

        public void fromRanked(final UnsignedShortType in, final UnsignedShortType out) {
            out.set(rankedToInput[in.get()]);
        }

        public short fromRanked(final short in) {
            return rankedToInput[in];
        }

        public int getMaxRank() {
            return maxRank;
        }
    }

    public static void main(String[] args) {
        new ImageJ();
        final ImagePlus imp = IJ.openImage("/Users/pietzsch/Desktop/random-small.tif");
        imp.show();
        final Img<UnsignedShortType> img = ImageJFunctions.wrap(imp);

        System.out.println("starting ... ");
        final long t0 = System.currentTimeMillis();

        final RankMap rankmap = RankMap.build(img);
        final RandomAccessibleInterval<UnsignedShortType> ranked = Converters.convert((RandomAccessibleInterval) img, rankmap::toRanked, new UnsignedShortType());
        ImageJFunctions.show(ranked, "ranked");

        final int window = 501;
        final int offset = 3000;
        final int windowC = (window - 1) / 2; //0 indexed sorted array has median at this position.

        RandomAccess<UnsignedShortType> front = ranked.randomAccess();
        RandomAccess<UnsignedShortType> back = img.randomAccess();
        MedianHistogram filter = new MedianHistogram(window, rankmap.getMaxRank());
        for (int y = 0; y < img.dimension(1); ++y) {
            for (int x = 0; x < img.dimension(0); ++x) {
                front.setPosition(new int[]{x, y, 0});
                back.setPosition(new int[]{x, y, 0});

                // read the first window ranked pixels into median filter
                for (int i = 0; i < window; ++i) {
                    filter.add((short) front.get().get());
                    front.fwd(2);
                }
                // write current median for windowC+1 pixels
                for (int i = 0; i <= windowC; ++i) {
                    final UnsignedShortType t = back.get();
                    t.set(t.get() + offset - rankmap.fromRanked(filter.get()));
                    back.fwd(2);
                }

                final int zSize = (int)img.dimension(2);
                final int zSteps = zSize - window;
                for (int i = 0; i < zSteps; ++i) {
                    filter.add((short) front.get().get());
                    front.fwd(2);
                    final UnsignedShortType t = back.get();
                    t.set(t.get() + offset - rankmap.fromRanked(filter.get()));
                    back.fwd(2);
                }

                // write current median for windowC pixels
                for (int i = 0; i < windowC; ++i) {
                    final UnsignedShortType t = back.get();
                    t.set(t.get() + offset - rankmap.fromRanked(filter.get()));
                    back.fwd(2);
                }
            }
        }

        final long t1 = System.currentTimeMillis();
        System.out.println("(t1-t0) = " + (t1 - t0));
        System.out.println("done");
    }
}
