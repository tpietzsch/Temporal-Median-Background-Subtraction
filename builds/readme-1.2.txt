The working of the plugin can be tested using the following macro commands:


run("T1 Head (2.4M, 16-bits)");
run("Temporal Median", "header=[a] image1=t1-head.tif window=10 offset=600");

Keep in mind the plugin also changes the open image. In the test case there are only 814 unique values in the image, so after the plugin runs it will contain only values between 0 and 813.
