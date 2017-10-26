/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import io.scif.services.DatasetIOService;

import java.io.File;
import java.io.IOException;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

@Plugin(type = Command.class, headless = true,
        menuPath = "NKI>Temporal Median Filter")
public class TemporalMedian implements Command, Previewable {

    @Parameter
    private LogService log;

    @Parameter
    private StatusService statusService;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private DatasetIOService datasetIOService;

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private final String header = "Apply temporal median filter";

    @Parameter(label = "Input 1")
    private File file1;

    @Parameter(label = "Result 1", type = ItemIO.OUTPUT)
    private Dataset result1;

    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = net.imagej.Main.launch(args);

        ij.command().run(TemporalMedian.class, true);
    }

    @Override
    public void run() {
        Dataset input1;
        try {
            input1 = datasetIOService.open(file1.getAbsolutePath());
        } catch (final IOException e) {
            log.error(e);
            return;
        }

        result1 = CalcTMF(input1);
    }

    @Override
    public void cancel() {
        log.info("Cancelled");
    }

    @Override
    public void preview() {
        log.info("previews TemporalMedian");
        statusService.showStatus(header);
    }    
    
    @SuppressWarnings({"rawtypes"})
    private Dataset CalcTMF(final Dataset d1) {
        return d1;
    }
}