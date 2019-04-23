package nl.nkiavl.rharkes;

import ij.ImagePlus;
import ij.ImageStack;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, headless = true,
menuPath = "NKI>TestMemMovOld")
public class TestMemoryMovingOld implements Command, Previewable {

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter(label = "Select image", description = "the image field")
	private ImagePlus image1;
	
	public static void main(final String... args) throws Exception {

	}

	@Override
	public void run() {
		ImageStack stack1 = image1.getStack();
		int w = stack1.getWidth();
		int h = stack1.getHeight();
		int t = stack1.getSize();
		int pixels = w * h;
		//allocate data storage
		short data[] = new short[t * pixels];
		short temp[] = new short[pixels];
		statusService.showStatus("loading data");
		Object[] imagearray = stack1.getImageArray();
		for (int i = 0; i < t; i++) { //all timepoints
			statusService.showProgress(i, t);
			temp = (short[]) imagearray[i]; 
			for (int p = 0; p < pixels; p++) {//all pixels
				data[i + p * t] = temp[p];
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