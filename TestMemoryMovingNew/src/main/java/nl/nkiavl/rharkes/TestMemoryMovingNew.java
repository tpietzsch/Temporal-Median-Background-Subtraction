package nl.nkiavl.rharkes;

import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;

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
        final long[] dims = new long[img.numDimensions()];
		img.dimensions(dims);
		int pixels = (int)(dims[0]*dims[1]*dims[2]);
        //allocate data storage
        short data[] = new short[pixels];
        statusService.showStatus("loading data");
        Cursor<UnsignedShortType> cursor = img.localizingCursor();
        final long[] pos = new long[3];
        int p = 0;
        while (cursor.hasNext()) {
        	cursor.fwd();
        	cursor.localize(pos);
        	data[(int) (pos[2]+pos[1]*dims[2]+pos[0]*dims[2]*dims[1])]=cursor.get().getShort();
        	p++;
        	if ((p%2000)==0){
        		statusService.showProgress(p,pixels);
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