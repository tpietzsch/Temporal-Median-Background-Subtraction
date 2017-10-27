/*
 * 
 */

import ij.ImagePlus;
import ij.ImageStack;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.Op;
import net.imagej.ops.OpEnvironment;
import org.scijava.AbstractContextual;
import org.scijava.ItemIO;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.stream.IntStream;

/**
 * A plugin to calculate the Temporal Median of a dataset
 *
 * @author R.Harkes & B.van den Broek
 */
@Plugin(type = Op.class, name = "datasetExample", menuPath = "NKI>Temporal Median")
public class TemporalMedian extends AbstractContextual implements Op {

    private ImagePlus image1 = null;
    private ImagePlus image2 = null;

    @Parameter
    private ConvertService convertService = null;

    @Parameter
    private DatasetService datasetService = null;

    @Parameter(type = ItemIO.BOTH)
    private Dataset dataset = null;

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        checkDataset(dataset);
        this.dataset = dataset;
        setImagePlus();
    }

    @Override
    public OpEnvironment ops() {
        return null;
    }

    @Override
    public void setEnvironment(OpEnvironment opEnvironment) {
    }

    @Override
    public void run() {
        checkNotNull(convertService, "Missing services - did you remember to call setContext?");
        checkNotNull(datasetService, "Missing services - did you remember to call setContext?");

        if (image1 == null) {
            // the plugin is (probably) run from an opService, check the dataset
            // obtained as @Parameter
            setDataset(dataset);
        }
        
        image2 = image1.duplicate();
        makeNegative(); //the main calculation. A void function that manipulates image.
        image2.show();
    }

    // Helper methods --
    private void checkNotNull(final Object object, final String message) {
        if (object == null) {
            throw new NullPointerException(message);
        }
    }

    private void checkArgument(final boolean argument, final String message) {
        if (!argument) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Check whether the Op can operate with the given dataset
     *
     * @throws NullPointerException if dataset == null
     * @throws IllegalArgumentException if the dataset has wrong number of
     * dimensions
     * @throws IllegalArgumentException if the dataset cannot be converted into
     * an ImagePlus
     */
    private void checkDataset(final Dataset dataset) {
        checkNotNull(dataset, "Dataset cannot be null");
        checkDatasetDimensions(dataset);
        checkArgument(convertService.supports(dataset, ImagePlus.class), "Cannot convert given dataset");
    }

    private void checkDatasetDimensions(final Dataset dataset) {
        checkArgument(dataset.numDimensions() == 3, "The plugin is meant only for 3D images");
        CalibratedAxis axes[] = new CalibratedAxis[3];
        dataset.axes(axes);
        checkArgument(axes[0].type().isSpatial(), "Unexpected 1st dimension");
        checkArgument(axes[1].type().isSpatial(), "Unexpected 2nd dimension");
        checkArgument(axes[2].type().equals(Axes.TIME), "Unexpected 3rd dimension");
    }

    /**
     * Main calculation loop. Manipulates the data in image.
     */
    private void makeNegative() {
        final int depth = image1.getNFrames();
        final ImageStack stack1 = image1.getStack();
        final ImageStack stack2 = image2.getStack();

        IntStream.rangeClosed(1, depth).parallel().forEach(t -> {
            short pixels1[] = (short[]) stack1.getPixels(t);
            short pixels2[]; //not read from, can just declare
            pixels2 = (short[]) stack2.getPixels(t);
            for (int i = 0; i < pixels1.length; i++) {
                pixels2[i] = (short) Math.sqrt((double) pixels1[i]);
            }
        });
    }

    private void setImagePlus() {
        image1 = convertService.convert(dataset, ImagePlus.class);
    }
    // endregion
}
