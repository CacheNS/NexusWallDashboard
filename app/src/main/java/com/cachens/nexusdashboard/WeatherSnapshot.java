package com.cachens.nexusdashboard;

import android.content.SharedPreferences;

final class WeatherSnapshot {
    static final int FORECAST_DAYS = 5;

    double temperature;
    double apparentTemperature;
    double humidity;
    double windSpeed;
    double precipitation;
    int weatherCode;
    int isDay;
    double aqi;
    double pm25;
    double pm10;
    double nitrogenDioxide;
    double ozone;
    String aqiSource;
    double aqiDistanceKm;
    double referencePm25;
    double referencePm10;
    double localPm25;
    double localPm10;
    String localPmSource;
    long localPmLocationId;
    long localPmObservedAt;
    double localPmDistanceKm;
    boolean localPmApproximateLocation;
    boolean localPmAqiReady;
    double latitude;
    double longitude;
    String locationName;
    long fetchedAt;
    long weatherTime;
    String weatherStation = "";
    double weatherStationDistanceKm = Double.NaN;
    final double[] dailyHigh = new double[FORECAST_DAYS];
    final double[] dailyLow = new double[FORECAST_DAYS];
    final int[] dailyCode = new int[FORECAST_DAYS];
    final String[] dailyDate = new String[FORECAST_DAYS];

    void save(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit()
            .putString("provider", "Foreca")
                .putLong("fetchedAt", fetchedAt)
            .putLong("weatherTime", weatherTime)
            .putString("weatherStation", weatherStation)
            .putLong("weatherStationDistanceKm", Double.doubleToRawLongBits(weatherStationDistanceKm))
                .putLong("temperature", Double.doubleToRawLongBits(temperature))
                .putLong("apparentTemperature", Double.doubleToRawLongBits(apparentTemperature))
                .putLong("humidity", Double.doubleToRawLongBits(humidity))
                .putLong("windSpeed", Double.doubleToRawLongBits(windSpeed))
                .putLong("precipitation", Double.doubleToRawLongBits(precipitation))
                .putInt("weatherCode", weatherCode)
                .putInt("isDay", isDay)
                .putLong("aqi", Double.doubleToRawLongBits(aqi))
                .putLong("pm25", Double.doubleToRawLongBits(pm25))
                .putLong("pm10", Double.doubleToRawLongBits(pm10))
                .putLong("nitrogenDioxide", Double.doubleToRawLongBits(nitrogenDioxide))
                .putLong("ozone", Double.doubleToRawLongBits(ozone))
                .putString("aqiSource", aqiSource)
                .putLong("aqiDistanceKm", Double.doubleToRawLongBits(aqiDistanceKm))
                .putLong("referencePm25", Double.doubleToRawLongBits(referencePm25))
                .putLong("referencePm10", Double.doubleToRawLongBits(referencePm10))
                .putLong("localPm25", Double.doubleToRawLongBits(localPm25))
                .putLong("localPm10", Double.doubleToRawLongBits(localPm10))
                .putString("localPmSource", localPmSource)
                .putLong("localPmLocationId", localPmLocationId)
                .putLong("localPmObservedAt", localPmObservedAt)
                .putLong("localPmDistanceKm", Double.doubleToRawLongBits(localPmDistanceKm))
                .putBoolean("localPmApproximateLocation", localPmApproximateLocation)
                .putBoolean("localPmAqiReady", localPmAqiReady)
                .putLong("latitude", Double.doubleToRawLongBits(latitude))
                .putLong("longitude", Double.doubleToRawLongBits(longitude))
                .putString("locationName", locationName);
        for (int i = 0; i < FORECAST_DAYS; i++) {
            editor.putLong("dailyHigh" + i, Double.doubleToRawLongBits(dailyHigh[i]));
            editor.putLong("dailyLow" + i, Double.doubleToRawLongBits(dailyLow[i]));
            editor.putInt("dailyCode" + i, dailyCode[i]);
            editor.putString("dailyDate" + i, dailyDate[i]);
        }
        editor.apply();
    }

    static WeatherSnapshot load(SharedPreferences preferences) {
        if (!preferences.contains("fetchedAt")
            || !"Foreca".equals(preferences.getString("provider", ""))) {
            return null;
        }
        WeatherSnapshot result = new WeatherSnapshot();
        result.fetchedAt = preferences.getLong("fetchedAt", 0);
        result.weatherTime = preferences.getLong("weatherTime", 0);
        result.weatherStation = preferences.getString("weatherStation", "");
        result.weatherStationDistanceKm = readDouble(preferences, "weatherStationDistanceKm");
        result.temperature = readDouble(preferences, "temperature");
        result.apparentTemperature = readDouble(preferences, "apparentTemperature");
        result.humidity = readDouble(preferences, "humidity");
        result.windSpeed = readDouble(preferences, "windSpeed");
        result.precipitation = readDouble(preferences, "precipitation");
        result.weatherCode = preferences.getInt("weatherCode", 0);
        result.isDay = preferences.getInt("isDay", 1);
        result.aqi = readDouble(preferences, "aqi");
        result.pm25 = readDouble(preferences, "pm25");
        result.pm10 = readDouble(preferences, "pm10");
        result.nitrogenDioxide = readDouble(preferences, "nitrogenDioxide");
        result.ozone = readDouble(preferences, "ozone");
        result.aqiSource = preferences.getString("aqiSource", "");
        result.aqiDistanceKm = readDouble(preferences, "aqiDistanceKm");
        result.referencePm25 = readDouble(preferences, "referencePm25");
        result.referencePm10 = readDouble(preferences, "referencePm10");
        result.localPm25 = readDouble(preferences, "localPm25");
        result.localPm10 = readDouble(preferences, "localPm10");
        result.localPmSource = preferences.getString("localPmSource", "");
        result.localPmLocationId = preferences.getLong("localPmLocationId", 0);
        result.localPmObservedAt = preferences.getLong("localPmObservedAt", 0);
        result.localPmDistanceKm = readDouble(preferences, "localPmDistanceKm");
        result.localPmApproximateLocation = preferences.getBoolean("localPmApproximateLocation", false);
        result.localPmAqiReady = preferences.getBoolean("localPmAqiReady", false);
        result.latitude = readDouble(preferences, "latitude");
        result.longitude = readDouble(preferences, "longitude");
        result.locationName = preferences.getString("locationName", "");
        for (int i = 0; i < FORECAST_DAYS; i++) {
            result.dailyHigh[i] = readDouble(preferences, "dailyHigh" + i);
            result.dailyLow[i] = readDouble(preferences, "dailyLow" + i);
            result.dailyCode[i] = preferences.getInt("dailyCode" + i, 0);
            result.dailyDate[i] = preferences.getString("dailyDate" + i, "");
        }
        return result;
    }

    private static double readDouble(SharedPreferences preferences, String key) {
        return Double.longBitsToDouble(preferences.getLong(key, Double.doubleToRawLongBits(Double.NaN)));
    }
}
