package nl.nkiavl.rharkes;
/* Fast Temporal Median filter 
(c) 2017 Rolf Harkes and Bram van den Broek, Netherlands Cancer Institute.
Based on the Fast Temporal Median Filter for ImageJ by the Milstein Lab.
It implementes the T.S.Huang algorithm in a maven .jar for easy deployment in Fiji (ImageJ2)
Calculating the median from the ranked data, and processing each pixel in parallel. 
The filter is intended for pre-processing of single molecule localization data.
v2.3

Used articles:
T.S.Huang et al. 1979 - Original algorithm for median calculation

This software is released under the GPL v3. You may copy, distribute and modify 
the software as long as you track changes/dates in source files. Any 
modifications to or software including (via compiler) GPL-licensed code 
must also be made available under the GPL along with build & install instructions.
https://www.gnu.org/licenses/gpl-3.0.en.html

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
import static java.lang.System.arraycopy;

import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Subtracts the temporal median
 */
@Plugin(type = Command.class, headless = true,
menuPath = "Plugins>Process>Temporal Median Background Subtraction")
public class TemporalMedian implements Command{

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter(label = "Select image", description = "the image field")
	private Img<UnsignedShortType> img;
	int values;

	@Parameter(label = "Median window", description = "the frames for medan")
	private short window;

	@Parameter(label = "Added offset", description = "offset added to the new image")
	private short offset;

	public static void main(final String... args) throws Exception {

	}

	@Override
	public void run() {
		final long[] dims = new long[img.numDimensions()];
		img.dimensions(dims);
		//sanity check on the input
		if (window >= dims[2]) {
			window = (short) dims[2];
			if (window % 2 == 0) {window--;}
			log.warn("Window is larger than largest dimension. Reducing window to " + window);
		} else if (window % 2 == 0) {
			window++;
			log.warn("No support for even windows. Window = " + window);
		}
		
		int unrankArray[] = denseRank(img);
		
		//do calculation per pixel
		subtractMedian subMed = new subtractMedian(img.randomAccess(),unrankArray,window,offset,dims);
		for (long x = 0; x < dims[0]; x++) {
			for (long y = 0; y<dims[1];y++) {
				subMed.setPosition(x, 0);
				subMed.setPosition(y, 1);
				subMed.run();
			}
			statusService.showStatus("Row ("+x+"/" +dims[0]+")");
			statusService.showProgress((int)x, (int)dims[0]);
		}
		//check for zeros to warn for underflow
		statusService.showStatus(1, 1, "FINISHED");
		//refresh imagej
	}


	/**
	 * Dense ranking of the input image. 
	 * It returns a decompression array to go from rank to value that has the 
	 * length of the total number of unique values.
	 * Example: [0 5 6 5 10 6] becomes [0 1 2 1 3 2]
	 *          The decompression array is [0 5 6 10] 
	 * @param img input image
	 * @param values maximum possible values
	 * @return integer array to convert the ranked image back
	 * @see <a href = "https://en.wikipedia.org/wiki/Ranking#Dense_ranking_(%221223%22_ranking)">Dense ranking</a>
	 * */ 
	public int[] denseRank(Img< UnsignedShortType > img) {
		UnsignedShortType temp = img.firstElement();
    	values = 1 << temp.getBitsPerPixel();
		boolean[] doesValueExist = new boolean[values];
		Cursor<UnsignedShortType> cursor = img.cursor();
		//go over all pixels to see what values exist
		if (statusService != null) {statusService.showStatus("ranking image (1/3)...");}
		while (cursor.hasNext()) {
			doesValueExist[cursor.next().getInteger()]=true;
		}
		//create the unrank array and subtract array. 
		int subtract[] = new int[values];
		int unrankArrayFull[] = new int[values]; //unrank array unrankArray[data] --> original data (int, since short cannot handle >2^15)
		int idx = 0;
		int subtractvalue = 0;
		if (statusService != null) {statusService.showStatus("ranking image (2/3)...");}
		for (int i = 0; i < values; i++) {
			if (doesValueExist[i]) {
				subtract[i] = subtractvalue;
				unrankArrayFull[idx] = subtractvalue + idx;
				idx++;
			} else {
				subtractvalue++;
			}
		}
		//trim the unranking array
		int unrankArray[] = new int[idx];
		arraycopy(unrankArrayFull, 0, unrankArray, 0, idx);
		//rank data
		if (statusService != null) {statusService.showStatus("ranking image (3/3)...");}
		cursor.reset();
		while (cursor.hasNext()) {
			UnsignedShortType t = cursor.next();
			t.setInteger(t.getInteger()-subtract[t.getInteger()]);
		}
		if (statusService != null) {statusService.showStatus("Finished!");}
		return unrankArray;
	}
}