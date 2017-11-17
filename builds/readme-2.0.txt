TemporalMedian 2.0

The plugin can be tested using the following macro commands:

run("T1 Head (2.4M, 16-bits)");
run("Temporal Median", "header=[a] image1=t1-head.tif window=11 offset=600");

Note 1: The plugin changes the original datastack.
Note 2: The values should never exeed 32767 because the 16-bit short in java will cause such values to be negative. 
Note 3: This plugin only calculates the median for odd windowsizes. In even windowsizes the median is calculated as the mean of the two central values, adding computational complexity.