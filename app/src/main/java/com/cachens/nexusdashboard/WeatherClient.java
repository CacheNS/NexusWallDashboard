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

final class WeatherClient {
    private static final String FORECA_URL = "https://weatherapi.foreca.net/api/v1/";

    private WeatherClient() {
    }

    static WeatherSnapshot fetch(double latitude, double longitude, String apiKey) throws Exception {
        return fetch(latitude, longitude, "", apiKey);
    }

    static WeatherSnapshot fetchPlace(String query, String apiKey) throws Exception {
        ResolvedLocation location = resolve(query, apiKey);
        return fetch(location.latitude, location.longitude, location.name, apiKey);
    }

    private static WeatherSnapshot fetch(double latitude, double longitude, String locationName,
                                         String apiKey) throws Exception {
        String coordinates = ForecaWeather.coordinates(latitude, longitude);
        String units = "?tempunit=C&windunit=KMH&rounding=0";
        JSONObject current = readForeca("current/" + coordinates + units + "&tz=UTC", apiKey);
        JSONObject daily = readForeca("forecast/daily/" + coordinates + units
                + "&periods=" + WeatherSnapshot.FORECAST_DAYS, apiKey);
        WeatherSnapshot result = ForecaWeather.parse(current, daily, latitude, longitude,
                locationName, System.currentTimeMillis());
        try {
            JSONObject observations = readForeca("observation/latest/" + coordinates + units
                    + "&stations=6&tz=UTC", apiKey);
            ForecaWeather.applyObservation(result, observations, System.currentTimeMillis());
        } catch (IOException error) {
            Log.w("NexusDashboard", "Foreca observations unavailable; using coordinate estimate");
        } catch (JSONException error) {
            Log.w("NexusDashboard", "Foreca observations invalid; using coordinate estimate");
        }

        result.applyAirCare(null);
        try {
            JSONObject air = new JSONObject(readUrl(AirCareClient.address(latitude, longitude)));
            result.applyAirCare(AirCareClient.parse(air, latitude, longitude, System.currentTimeMillis()));
        } catch (IOException error) {
            Log.w("NexusDashboard", "AirCare data unavailable", error);
        } catch (JSONException error) {
            Log.w("NexusDashboard", "AirCare data invalid", error);
        }
        return result;
    }

    private static ResolvedLocation resolve(String query, String apiKey) throws Exception {
        ResolvedLocation coordinates = ForecaWeather.coordinateLocation(query);
        if (coordinates != null) return coordinates;
        JSONObject response = readForeca("location/search/"
                + URLEncoder.encode(query, "UTF-8").replace("+", "%20") + "?lang=en", apiKey);
        JSONArray results = response.optJSONArray("locations");
        if (results == null || results.length() == 0) {
            throw new IOException("Location not found: " + query);
        }
        JSONObject first = results.getJSONObject(0);
        String name = first.optString("name", query);
        String admin = first.optString("adminArea", "");
        String country = first.optString("country", "");
        if (admin.length() > 0 && !admin.equalsIgnoreCase(name)) {
            name += ", " + admin;
        } else if (country.length() > 0) {
            name += ", " + country;
        }
        return new ResolvedLocation(name, first.getDouble("lat"), first.getDouble("lon"));
    }

    private static JSONObject readForeca(String path, String apiKey) throws IOException, JSONException {
        if (apiKey == null || apiKey.trim().length() == 0) {
            throw new AuthenticationException();
        }
        return new JSONObject(readUrl(FORECA_URL + path, apiKey.trim()));
    }

    static String readUrl(String address) throws IOException {
        return readUrl(address, null);
    }

    private static String readUrl(String address, String apiKey) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        LegacyTls.configure(connection);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NexusWallDashboard/1.0");
        if (apiKey != null) {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        try {
            int status = connection.getResponseCode();
            if (apiKey != null && (status == 401 || status == 403)) {
                throw new AuthenticationException();
            }
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readStream(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Weather service returned HTTP " + status);
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    static final class AuthenticationException extends IOException {
        AuthenticationException() {
            super("Foreca API key missing or rejected");
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
