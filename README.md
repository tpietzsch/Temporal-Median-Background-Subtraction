# Temporal Median filter 
An adaption of https://github.com/marcelocordeiro/MedianFilter-ImageJ

Copyright (c) 2014, Marcelo Augusto Cordeiro, Milstein Lab, University of Toronto.
This ImageJ plugin was developed for the Milstein Lab at the University of Toronto,
with the help of Professor Josh Milstein during the summer of 2014, as part of the
Science Without Borders research opportunity program.

Copyright (c) 2017 Bram van den Broek and Rolf Harkes, Dutch Cancer Institute of Amsterdam.
Implementation of the algorithm in a maven .jar for easy deployment in Fiji (ImageJ2). Adaption is released under the GPL V3

Changes:
* The window is changed from forward to central.
* The empty bins in the histogram are removed beforehand to reduce memory usage.
* The possibility to add an offset to the data before median removal to prevent integer overflow.
* Normalization of data was removed
* Custom start and finish of filter was removed
* Automatic correction of even windowsizes
* Automatic conversion to 16-bit

Used articles:
T.S.Huang et al. 1979 - Original algorithm for median calculation
