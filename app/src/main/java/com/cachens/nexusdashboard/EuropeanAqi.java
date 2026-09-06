package com.cachens.nexusdashboard;

final class EuropeanAqi {
    private EuropeanAqi() {
    }

    static double fromPollutants(double pm25, double pm10, double nitrogenDioxide,
                                 double ozone, double sulphurDioxide) {
        double result = Double.NaN;
        result = maxAvailable(result, fromConcentration(pm25, new double[]{10, 20, 25, 50, 75}));
        result = maxAvailable(result, fromConcentration(pm10, new double[]{20, 40, 50, 100, 150}));
        result = maxAvailable(result, fromConcentration(nitrogenDioxide,
                new double[]{40, 90, 120, 230, 340}));
        result = maxAvailable(result, fromConcentration(ozone, new double[]{50, 100, 130, 240, 380}));
        result = maxAvailable(result, fromConcentration(sulphurDioxide,
                new double[]{100, 200, 350, 500, 750}));
        return result;
    }

    static double fromParticles(double pm25, double pm10) {
        return fromPollutants(pm25, pm10, Double.NaN, Double.NaN, Double.NaN);
    }

    private static double fromConcentration(double concentration, double[] thresholds) {
        if (Double.isNaN(concentration)) {
            return Double.NaN;
        }
        double lowerConcentration = 0;
        double lowerIndex = 0;
        for (int i = 0; i < thresholds.length; i++) {
            double upperConcentration = thresholds[i];
            double upperIndex = (i + 1) * 20;
            if (concentration <= upperConcentration) {
                return lowerIndex + (concentration - lowerConcentration)
                        * (upperIndex - lowerIndex) / (upperConcentration - lowerConcentration);
            }
            lowerConcentration = upperConcentration;
            lowerIndex = upperIndex;
        }
        double finalBand = thresholds[thresholds.length - 1] - thresholds[thresholds.length - 2];
        return Math.min(500, 100 + (concentration - thresholds[thresholds.length - 1])
                * 20 / finalBand);
    }

    private static double maxAvailable(double first, double second) {
        if (Double.isNaN(first)) {
            return second;
        }
        if (Double.isNaN(second)) {
            return first;
        }
        return Math.max(first, second);
    }
}
