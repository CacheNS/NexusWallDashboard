package com.cachens.nexusdashboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ForecaWeather {
    private static final long MAX_OBSERVATION_AGE_MS = 2L * 60L * 60L * 1000L;
    private static final double MAX_STATION_DISTANCE_KM = 30.0;

    private ForecaWeather() {
    }

    static String coordinates(double latitude, double longitude) {
        if (!validCoordinates(latitude, longitude)) {
            throw new IllegalArgumentException("Invalid weather coordinates");
        }
        return Double.toString(longitude) + "," + Double.toString(latitude);
    }

    static ResolvedLocation coordinateLocation(String query) {
        String[] parts = query.split(",", -1);
        if (parts.length != 2) return null;
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(parts[0].trim());
            longitude = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException error) {
            return null;
        }
        coordinates(latitude, longitude);
        return new ResolvedLocation(query.trim(), latitude, longitude);
    }

    static WeatherSnapshot parse(JSONObject currentResponse, JSONObject dailyResponse,
                                 double latitude, double longitude, String name, long now)
            throws JSONException {
        coordinates(latitude, longitude);
        WeatherSnapshot result = new WeatherSnapshot();
        result.latitude = latitude;
        result.longitude = longitude;
        result.locationName = name.length() == 0
                ? String.format(Locale.US, "%.5f, %.5f", latitude, longitude) : name;
        result.fetchedAt = now;
        JSONObject current = currentResponse.getJSONObject("current");
        if (!finite(current.optDouble("temperature", Double.NaN))) {
            throw new JSONException("Foreca temperature missing");
        }
        applyConditions(result, current);
        result.precipitation = current.optDouble("precipRate", Double.NaN);
        result.aqi = Double.NaN;
        result.pm25 = Double.NaN;
        result.pm10 = Double.NaN;
        result.nitrogenDioxide = Double.NaN;
        result.ozone = Double.NaN;
        result.referencePm25 = Double.NaN;
        result.referencePm10 = Double.NaN;
        result.localPm25 = Double.NaN;
        result.localPm10 = Double.NaN;
        result.localPmDistanceKm = Double.NaN;
        result.aqiSource = "";
        result.aqiDistanceKm = Double.NaN;
        JSONArray forecast = dailyResponse.getJSONArray("forecast");
        for (int day = 0; day < WeatherSnapshot.FORECAST_DAYS; day++) {
            JSONObject values = forecast.optJSONObject(day);
            result.dailyDate[day] = values == null ? "" : values.optString("date", "");
            result.dailyHigh[day] = values == null ? Double.NaN : values.optDouble("maxTemp", Double.NaN);
            result.dailyLow[day] = values == null ? Double.NaN : values.optDouble("minTemp", Double.NaN);
            result.dailyCode[day] = values == null ? -1 : conditionCode(values.optString("symbol", ""));
        }
        return result;
    }

    static void applyObservation(WeatherSnapshot result, JSONObject response, long now)
            throws JSONException {
        JSONArray observations = response.getJSONArray("observations");
        JSONObject nearest = null;
        double nearestDistance = MAX_STATION_DISTANCE_KM;
        long latestTime = 0;
        for (int index = 0; index < observations.length(); index++) {
            JSONObject observation = observations.optJSONObject(index);
            if (observation == null) continue;
            double latitude = observation.optDouble("latitude", Double.NaN);
            double longitude = observation.optDouble("longitude", Double.NaN);
            long timestamp = timestamp(observation.optString("time", ""));
            if (!validCoordinates(latitude, longitude)
                    || !finite(observation.optDouble("temperature", Double.NaN))
                    || observation.optString("station", "").trim().length() == 0
                    || timestamp <= 0 || timestamp > now
                    || now - timestamp > MAX_OBSERVATION_AGE_MS) continue;
            double distance = distanceKm(result.latitude, result.longitude, latitude, longitude);
            if (distance < nearestDistance
                    || (distance == nearestDistance && timestamp > latestTime)) {
                nearest = observation;
                nearestDistance = distance;
                latestTime = timestamp;
            }
        }
        if (nearest != null) {
            int currentIsDay = result.isDay;
            applyConditions(result, nearest);
            result.isDay = currentIsDay;
            result.precipitation = Double.NaN;
            result.weatherStation = nearest.getString("station");
            result.weatherStationDistanceKm = nearestDistance;
        }
    }

    private static void applyConditions(WeatherSnapshot result, JSONObject conditions) {
        result.temperature = conditions.optDouble("temperature", Double.NaN);
        result.apparentTemperature = conditions.optDouble("feelsLikeTemp", Double.NaN);
        result.humidity = conditions.optDouble("relHumidity", Double.NaN);
        result.windSpeed = conditions.optDouble("windSpeed", Double.NaN);
        String symbol = conditions.optString("symbol", "");
        result.weatherCode = conditionCode(symbol);
        result.isDay = symbol.startsWith("n") ? 0 : 1;
        result.weatherTime = timestamp(conditions.optString("time", ""));
    }

    static int conditionCode(String symbol) {
        if (!symbol.matches("[dn][0-6][0-4][0-2]")) return -1;
        char clouds = symbol.charAt(1);
        char precipitation = symbol.charAt(2);
        char type = symbol.charAt(3);
        if (precipitation == '4') return 95;
        if (precipitation != '0') {
            if (type == '1') return 69;
            if (type == '2') return precipitation == '2' ? 85 : 73;
            if (precipitation == '2') return 80;
            return precipitation == '1' ? 61 : 63;
        }
        if (clouds == '6') return 45;
        if (clouds == '0') return 0;
        if (clouds == '1' || clouds == '5') return 1;
        if (clouds == '4') return 3;
        return 2;
    }

    static long timestamp(String value) {
        String normalized = value.replaceFirst("Z$", "+0000")
                .replaceFirst("([+-][0-9]{2}):([0-9]{2})$", "$1$2");
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mmZ"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(normalized, position);
            if (date != null && position.getIndex() == normalized.length()) return date.getTime();
        }
        return 0;
    }

    private static boolean validCoordinates(double latitude, double longitude) {
        return finite(latitude) && finite(longitude)
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double distanceKm(double latitude1, double longitude1,
                                     double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371.0 * 2 * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }
}