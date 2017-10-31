The working of the plugin can be tested using the following macro commands:


run("T1 Head (2.4M, 16-bits)");
run("Temporal Median", "header=[a] image1=t1-head.tif window=10 offset=600");

Note 1: The plugin also changes the open image. In the test case there are only 814 unique values in the image, so after the plugin runs it will contain only values between 0 and 813.
Note 2: The values should never exeed 32767 because the 16-bit short in java will cause such values to be negative. 
