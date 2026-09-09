package com.cachens.nexusdashboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AirCareAqiTest {
    @Test
    public void categoriesUseAirCareInclusiveThresholds() {
        double[] values = {0, 26, 26.01, 33, 33.01, 66, 66.01, 100, 100.01, 500};
        int[] categories = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4};
        int[] colors = {0xff4ba062, 0xffdba42a, 0xfff87728, 0xffc60045, 0xff7d2181};
        for (int index = 0; index < values.length; index++) {
            assertEquals(categories[index], AirCareAqi.category(values[index]));
            assertEquals(colors[categories[index]], AirCareAqi.color(values[index]));
        }
    }

    @Test
    public void missingAndInvalidValuesAreUnavailable() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertEquals(-1, AirCareAqi.category(value));
            assertEquals(0xff646e78, AirCareAqi.color(value));
        }
    }
}