package com.example.activityprofilesamplegathering;

public class GravityFilter {
	
	private final static double ALPHA = 0.8;
	private double gx, gy, gz;
	
	public GravityFilter() {
		gx = 0;
		gy = 0;
		gz = 0;
	}
	
	public int[] filter(int [] array) {
		// calculate the gravity
		gx = ALPHA * gx + (1 - ALPHA) * array[0];
		gy = ALPHA * gy + (1 - ALPHA) * array[1];
		gz = ALPHA * gz + (1 - ALPHA) * array[2];
		
		array[0] = (int) (array[0] - gx);
		array[1] = (int) (array[1] - gy);
		array[2] = (int) (array[2] - gz);
		return array;
	}

}
