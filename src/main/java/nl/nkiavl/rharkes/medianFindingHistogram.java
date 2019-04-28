package nl.nkiavl.rharkes;
/* Median finding histogram 
(c) 2019 Rolf Harkes, Netherlands Cancer Institute.
Based on the Fast Temporal Median Filter for ImageJ by the Milstein Lab.
This class would run on every pixel in an image and is therefore kept minimal.
The medianFindingHistogram is created once and then updated with new values.

Known limitations for increased speed
* only odd windows where the median is the center value

Known limitations due to usage of the short datatype:
* no more than 32767 unique values allowed (a SMLM experiment would usually have about 1000 unique values)
* no more than 32767 equal values allowed  (the window is usually 501)

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
 
public class medianFindingHistogram {
	public short median;
	private final short[] hist;
	private short aux; //position in the bin
	private final short medianPosition;
	
	/**
	 * @param data   Initial data for the histogram, must have odd nr of elements
	 * @param nrBins Must be at least equal to the maximum any data that will be put into the histogram
	 */
	public medianFindingHistogram(short[] data, short nrBins) {
		hist = new short[nrBins];
		medianPosition = (short) (data.length/2); //
		//Add data to histogram
		for (int i = 0; i < data.length; i++) 
		{
			hist[data[i]]++;
		}
		//Find median
		short count = 0;
		short j = -1;
		while (count <= medianPosition) //Counting the histogram, until it reaches the median
		{
			j++;
			count += hist[j];
		}
		aux = (short) (medianPosition-count+hist[j]+1); //position in the bin. 1 is lowest.
		median = j;
	}
	/**
	 * @param addValue
	 * @param removeValue
	 */
	public void addRemoveValues(short addValue, short removeValue) {
		hist[removeValue]--; //Removing old pixel
		hist[addValue]++; //Adding new pixel
		if (!(((removeValue > median)
				&& (addValue > median))
				|| ((removeValue < median)
						&& (addValue < median))
				|| ((removeValue == median)
						&& (addValue == median)))) //Add and remove the same pixel, or pixel from the same side, the median doesn't change
		{
			short j = median;
			if ((addValue > median) && (removeValue < median)) //The median goes right
			{
				if (hist[median] == aux) //The previous median was the last pixel of its column in the histogram, so it changes
				{
					j++;
					while (hist[j] == 0) //Searching for the next pixel
					{
						j++;
					}
					median = j;
					aux = 1; //The median is the first pixel of its column
				} else {
					aux++; //The previous median wasn't the last pixel of its column, so it doesn't change, just need to mark its new position
				}
			} else if ((removeValue > median) && (addValue < median)) //The median goes left
			{
				if (aux == 1) //The previous median was the first pixel of its column in the histogram, so it changes
				{
					j--;
					while (hist[j] == 0) //Searching for the next pixel
					{
						j--;
					}
					median = j;
					aux = hist[j]; //The median is the last pixel of its column
				} else {
					aux--; //The previous median wasn't the first pixel of its column, so it doesn't change, just need to mark its new position
				}
			} else if (addValue == median) //new pixel = last median
			{
				if (removeValue < median) //old pixel < last median, the median goes right
				{
					aux++; //There is at least one pixel above the last median (the one that was just added), so the median doesn't change, just need to mark its new position
				}								//else, absolutely nothing changes
			} else //pixel==median, old pixel = last median
			{
				if (addValue > median) //new pixel > last median, the median goes right
				{
					if (aux == (hist[median] + 1)) //The previous median was the last pixel of its column, so it changes
					{
						j++;
						while (hist[j] == 0) //Searching for the next pixel
						{
							j++;
						}
						median = j;
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
						median = j;
						aux = hist[j]; //The median is the last pixel of its column
					} else {
						aux--; //The previous median wasn't the first pixel of its column, so it doesn't change, just need to mark its new position
					}
				}
			}
		}
	}
}