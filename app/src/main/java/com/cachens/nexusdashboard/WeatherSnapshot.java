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
    double latitude;
    double longitude;
    String locationName;
    long fetchedAt;
    final double[] dailyHigh = new double[FORECAST_DAYS];
    final double[] dailyLow = new double[FORECAST_DAYS];
    final int[] dailyCode = new int[FORECAST_DAYS];
    final String[] dailyDate = new String[FORECAST_DAYS];

    void save(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit()
                .putLong("fetchedAt", fetchedAt)
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
        if (!preferences.contains("fetchedAt")) {
            return null;
        }
        WeatherSnapshot result = new WeatherSnapshot();
        result.fetchedAt = preferences.getLong("fetchedAt", 0);
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
