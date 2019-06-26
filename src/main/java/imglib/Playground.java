package imglib;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class Playground {



    public static void main(String[] args) {
        final ImagePlus imp = IJ.openImage("/Users/pietzsch/Desktop/random-smalltif");
        final Img< UnsignedShortType > img = ImageJFunctions.wrap(imp);


    }
}
