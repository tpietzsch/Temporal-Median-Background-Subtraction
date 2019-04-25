package nl.nkiavl.rharkes;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, headless = true,
        menuPath = "NKI>TestMemMovNew")
public class TestMemoryMovingNew implements Command, Previewable {

    @Parameter
    private LogService log;

    @Parameter
    private StatusService statusService;

    @Parameter(label = "Select image", description = "the image field")
    private Img<UnsignedShortType> img;

    public static void main(final String... args) throws Exception {

    }

    @Override
    public void run() {
    	final int pixels = (int)Intervals.numElements(img);
		final short data[] = new short[pixels];
		statusService.showStatus("loading data");
		int p = 0;
		for (UnsignedShortType s : Views.flatIterable(img)) {
			data[p++] = s.getShort();
			if ((p % 2000) == 0) {
				statusService.showProgress(p, pixels);
			}
		}
		statusService.showStatus(1, 1, "FINISHED");
    }

    @Override
    public void cancel() {
        log.debug("Cancelled");
    }

    @Override
    public void preview() {
        log.debug("previewed");
    }
}