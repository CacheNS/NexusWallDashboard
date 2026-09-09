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
    private static final String SEPA_URL = "https://vazduh.sepa.gov.rs/?view=desktop";
    private static final double MAX_SEPA_DISTANCE_KM = 30.0;

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

        try {
            applyNearestSepaStation(result);
        } catch (IOException error) {
            Log.w("NexusDashboard", "SEPA station data unavailable", error);
        } catch (JSONException error) {
            Log.w("NexusDashboard", "SEPA station data invalid", error);
        }
        result.referencePm25 = result.pm25;
        result.referencePm10 = result.pm10;
        try {
            SensorCommunityClient.applyNearest(result, result.aqiDistanceKm);
        } catch (IOException error) {
            Log.w("NexusDashboard", "Local PM data unavailable", error);
        } catch (JSONException error) {
            Log.w("NexusDashboard", "Local PM data invalid", error);
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
        double stationAqi = EuropeanAqi.fromPollutants(pm25, pm10, no2, ozone, sulphurDioxide);
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

    private static double distanceKm(double latitude1, double longitude1,
                                     double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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
