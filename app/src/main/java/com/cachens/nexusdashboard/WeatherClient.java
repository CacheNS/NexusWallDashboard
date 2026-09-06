package com.cachens.nexusdashboard;

import org.json.JSONArray;
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
                + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto&forecast_days=3";
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

        JSONArray dates = daily.getJSONArray("time");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");
        JSONArray codes = daily.getJSONArray("weather_code");
        for (int i = 0; i < 3; i++) {
            result.dailyDate[i] = dates.optString(i, "");
            result.dailyHigh[i] = highs.optDouble(i, Double.NaN);
            result.dailyLow[i] = lows.optDouble(i, Double.NaN);
            result.dailyCode[i] = codes.optInt(i, 0);
        }
        return result;
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
