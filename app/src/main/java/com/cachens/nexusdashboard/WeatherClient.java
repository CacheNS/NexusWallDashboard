package com.cachens.nexusdashboard;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

final class WeatherClient {
    private static final String SEPA_URL = "https://vazduh.sepa.gov.rs/?view=desktop";
    private static final double MAX_SEPA_DISTANCE_KM = 30.0;

    private WeatherClient() {
    }

    static WeatherSnapshot fetch(double latitude, double longitude) throws Exception {
        return fetch(latitude, longitude, "");
    }

    static WeatherSnapshot fetchPlace(String query) throws Exception {
        ResolvedLocation location = resolve(query);
        return fetch(location.latitude, location.longitude, location.name);
    }

    private static WeatherSnapshot fetch(double latitude, double longitude, String locationName) throws Exception {
        String coordinates = String.format(Locale.US, "latitude=%.5f&longitude=%.5f", latitude, longitude);
        String forecastUrl = "https://api.open-meteo.com/v1/forecast?" + coordinates
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto&forecast_days="
                + WeatherSnapshot.FORECAST_DAYS;
        String airUrl = "https://air-quality-api.open-meteo.com/v1/air-quality?" + coordinates
                + "&current=european_aqi,pm10,pm2_5,nitrogen_dioxide,ozone&timezone=auto";

        JSONObject forecast = new JSONObject(readUrl(forecastUrl));
        JSONObject air = new JSONObject(readUrl(airUrl));
        JSONObject current = forecast.getJSONObject("current");
        JSONObject currentAir = air.getJSONObject("current");
        JSONObject daily = forecast.getJSONObject("daily");

        WeatherSnapshot result = new WeatherSnapshot();
        result.latitude = latitude;
        result.longitude = longitude;
        result.locationName = locationName;
        result.fetchedAt = System.currentTimeMillis();
        result.temperature = current.optDouble("temperature_2m", Double.NaN);
        result.apparentTemperature = current.optDouble("apparent_temperature", Double.NaN);
        result.humidity = current.optDouble("relative_humidity_2m", Double.NaN);
        result.windSpeed = current.optDouble("wind_speed_10m", Double.NaN);
        result.precipitation = current.optDouble("precipitation", Double.NaN);
        result.weatherCode = current.optInt("weather_code", 0);
        result.isDay = current.optInt("is_day", 1);
        result.aqi = currentAir.optDouble("european_aqi", Double.NaN);
        result.pm25 = currentAir.optDouble("pm2_5", Double.NaN);
        result.pm10 = currentAir.optDouble("pm10", Double.NaN);
        result.nitrogenDioxide = currentAir.optDouble("nitrogen_dioxide", Double.NaN);
        result.ozone = currentAir.optDouble("ozone", Double.NaN);
        result.aqiSource = "Open-Meteo";
        result.aqiDistanceKm = Double.NaN;

        try {
            applyNearestSepaStation(result);
        } catch (IOException error) {
            Log.w("NexusDashboard", "SEPA station data unavailable; using Open-Meteo AQI", error);
        } catch (JSONException error) {
            Log.w("NexusDashboard", "SEPA station data invalid; using Open-Meteo AQI", error);
        }

        JSONArray dates = daily.getJSONArray("time");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");
        JSONArray codes = daily.getJSONArray("weather_code");
        for (int i = 0; i < WeatherSnapshot.FORECAST_DAYS; i++) {
            result.dailyDate[i] = dates.optString(i, "");
            result.dailyHigh[i] = highs.optDouble(i, Double.NaN);
            result.dailyLow[i] = lows.optDouble(i, Double.NaN);
            result.dailyCode[i] = codes.optInt(i, 0);
        }
        return result;
    }

    private static void applyNearestSepaStation(WeatherSnapshot result) throws IOException, JSONException {
        String page = readUrl(SEPA_URL);
        String marker = "window.STATIONS_FOR_MAP = ";
        int start = page.indexOf(marker);
        if (start < 0) {
            throw new JSONException("SEPA station list not found");
        }
        start += marker.length();
        int end = page.indexOf("];", start);
        if (end < 0) {
            throw new JSONException("SEPA station list is incomplete");
        }

        JSONArray stations = new JSONArray(page.substring(start, end + 1));
        JSONObject nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.getJSONObject(i);
            JSONArray components = station.optJSONArray("components");
            if (!hasParticleMeasurement(components)) {
                continue;
            }
            double distance = distanceKm(result.latitude, result.longitude,
                    station.getDouble("lat"), station.getDouble("lng"));
            if (distance < nearestDistance) {
                nearest = station;
                nearestDistance = distance;
            }
        }
        if (nearest == null || nearestDistance > MAX_SEPA_DISTANCE_KM) {
            return;
        }

        JSONArray components = nearest.getJSONArray("components");
        double pm25 = componentValue(components, "PM2.5");
        double pm10 = componentValue(components, "PM10");
        double no2 = componentValue(components, "NO2");
        double ozone = componentValue(components, "O3");
        double sulphurDioxide = componentValue(components, "SO2");
        double stationAqi = calculateEuropeanAqi(pm25, pm10, no2, ozone, sulphurDioxide);
        if (Double.isNaN(stationAqi)) {
            return;
        }

        result.aqi = stationAqi;
        result.pm25 = pm25;
        result.pm10 = pm10;
        result.nitrogenDioxide = no2;
        result.ozone = ozone;
        result.aqiSource = nearest.getString("name");
        result.aqiDistanceKm = nearestDistance;
    }

    private static boolean hasParticleMeasurement(JSONArray components) {
        if (components == null) {
            return false;
        }
        return !Double.isNaN(componentValue(components, "PM2.5"))
                || !Double.isNaN(componentValue(components, "PM10"));
    }

    private static double componentValue(JSONArray components, String name) {
        for (int i = 0; i < components.length(); i++) {
            JSONObject component = components.optJSONObject(i);
            if (component != null && name.equals(component.optString("name")) && !component.isNull("value")) {
                return component.optDouble("value", Double.NaN);
            }
        }
        return Double.NaN;
    }

    private static double calculateEuropeanAqi(double pm25, double pm10, double no2, double ozone,
                                                double sulphurDioxide) {
        double result = Double.NaN;
        result = maxAvailable(result, pollutantAqi(pm25, new double[]{10, 20, 25, 50, 75}));
        result = maxAvailable(result, pollutantAqi(pm10, new double[]{20, 40, 50, 100, 150}));
        result = maxAvailable(result, pollutantAqi(no2, new double[]{40, 90, 120, 230, 340}));
        result = maxAvailable(result, pollutantAqi(ozone, new double[]{50, 100, 130, 240, 380}));
        result = maxAvailable(result, pollutantAqi(sulphurDioxide, new double[]{100, 200, 350, 500, 750}));
        return result;
    }

    private static double pollutantAqi(double concentration, double[] thresholds) {
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
        return Math.min(500, 100 + (concentration - thresholds[thresholds.length - 1]) * 20 / finalBand);
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

    private static double distanceKm(double latitude1, double longitude1,
                                     double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static ResolvedLocation resolve(String query) throws Exception {
        String address = "https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(query, "UTF-8")
                + "&count=1&language=en&format=json";
        JSONObject response = new JSONObject(readUrl(address));
        JSONArray results = response.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new IOException("Location not found: " + query);
        }
        JSONObject first = results.getJSONObject(0);
        String name = first.optString("name", query);
        String admin = first.optString("admin1", "");
        String country = first.optString("country", "");
        if (admin.length() > 0 && !admin.equalsIgnoreCase(name)) {
            name += ", " + admin;
        } else if (country.length() > 0) {
            name += ", " + country;
        }
        return new ResolvedLocation(name, first.getDouble("latitude"), first.getDouble("longitude"));
    }

    private static String readUrl(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        LegacyTls.configure(connection);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NexusWallDashboard/1.0");
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readStream(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Weather service returned HTTP " + status + ": " + body);
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        return result.toString();
    }
}
