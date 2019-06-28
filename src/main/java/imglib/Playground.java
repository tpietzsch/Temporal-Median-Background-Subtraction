package imglib;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import java.util.concurrent.atomic.AtomicInteger;

public class Playground {

    static class RankMap
    {
        public static final int U16_SIZE = 65536;

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

    //two methods for concurrent calculation
    private static Thread[] newThreadArray() {
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

        final int imgw = (int) img.dimension(0);
        final int imgh = (int) img.dimension(1);
        final int pixels = imgw * imgh;

        //do calculation in parallel per pixel
        final AtomicInteger ai = new AtomicInteger(0); //special unqique int for each thread
        final Thread[] threads = newThreadArray(); //all threads
        for (int ithread = 0; ithread < threads.length; ithread++) {
            threads[ithread] = new Thread() { //make threads
                {
                    setPriority(Thread.NORM_PRIORITY);
                }
                public void run() {
                    RandomAccess<UnsignedShortType> front = ranked.randomAccess();
                    RandomAccess<UnsignedShortType> back = img.randomAccess();
                    MedianHistogram filter = new MedianHistogram(window, rankmap.getMaxRank());
                    for (int j = ai.getAndIncrement(); j < pixels; j = ai.getAndIncrement()) { //get unique i
                        final int[] pos = { j % imgw, j / imgw, 0 };
                        front.setPosition(pos);
                        back.setPosition(pos);

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
            }; //end of thread creation
        }
        startAndJoin(threads);

        final long t1 = System.currentTimeMillis();
        System.out.println("(t1-t0) = " + (t1 - t0));
        System.out.println("done");
    }
}
