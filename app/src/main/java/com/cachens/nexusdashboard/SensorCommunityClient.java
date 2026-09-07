package com.cachens.nexusdashboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class SensorCommunityClient {
    private static final long MAX_READING_AGE_MS = 15L * 60L * 1000L;
    private static final double MAX_DISCOVERY_DISTANCE_KM = 5.0;

    private SensorCommunityClient() {
    }

    static void applyNearest(WeatherSnapshot result, double officialDistanceKm)
            throws IOException, JSONException {
        String url = String.format(Locale.US,
                "https://data.sensor.community/airrohr/v1/filter/area=%.5f,%.5f,10",
                result.latitude, result.longitude);
        JSONArray rows = new JSONArray(WeatherClient.readUrl(url));
        Candidate nearest = null;
        double maximumDistance = Double.isNaN(officialDistanceKm)
                ? MAX_DISCOVERY_DISTANCE_KM
                : Math.min(MAX_DISCOVERY_DISTANCE_KM, officialDistanceKm);

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            JSONObject location = row.getJSONObject("location");
            if (location.optInt("indoor", 1) != 0) {
                continue;
            }
            double pm25 = componentValue(row.getJSONArray("sensordatavalues"), "P2");
            double pm10 = componentValue(row.getJSONArray("sensordatavalues"), "P1");
            if (!validParticles(pm25, pm10)) {
                continue;
            }
            long observedAt = parseTimestamp(row.optString("timestamp"));
            if (!isRecent(observedAt)) {
                continue;
            }
            double latitude = location.optDouble("latitude", Double.NaN);
            double longitude = location.optDouble("longitude", Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                continue;
            }
            double distance = distanceKm(result.latitude, result.longitude, latitude, longitude);
            if (distance > maximumDistance) {
                continue;
            }
            Candidate candidate = new Candidate(location.optLong("id"), pm25, pm10, observedAt,
                    distance, location.optInt("exact_location", 0) == 0);
            if (nearest == null || candidate.distanceKm < nearest.distanceKm
                    || (candidate.locationId == nearest.locationId
                    && candidate.observedAt > nearest.observedAt)) {
                nearest = candidate;
            }
        }

        Candidate selected = nearest;
        if (selected == null) {
            return;
        }
        result.localPm25 = selected.pm25;
        result.localPm10 = selected.pm10;
        result.localPmLocationId = selected.locationId;
        result.localPmObservedAt = selected.observedAt;
        result.localPmDistanceKm = selected.distanceKm;
        result.localPmApproximateLocation = selected.approximateLocation;
        result.localPmSource = "Sensor.Community";
        result.pm25 = selected.pm25;
        result.pm10 = selected.pm10;
    }

    private static boolean validParticles(double pm25, double pm10) {
        boolean validPm25 = !Double.isNaN(pm25) && pm25 >= 0 && pm25 <= 1000;
        boolean validPm10 = !Double.isNaN(pm10) && pm10 >= 0 && pm10 <= 1500;
        return validPm25 || validPm10;
    }

    private static boolean isRecent(long timestamp) {
        if (timestamp <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < 1577836800000L) {
            return true;
        }
        return timestamp <= now + 5L * 60L * 1000L && now - timestamp <= MAX_READING_AGE_MS;
    }

    private static long parseTimestamp(String value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date parsed = format.parse(value);
            return parsed == null ? 0 : parsed.getTime();
        } catch (ParseException ignored) {
            return 0;
        }
    }

    private static double componentValue(JSONArray values, String type) {
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null && type.equals(value.optString("value_type"))) {
                return value.optDouble("value", Double.NaN);
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

    private static final class Candidate {
        final long locationId;
        final double pm25;
        final double pm10;
        final long observedAt;
        final double distanceKm;
        final boolean approximateLocation;

        Candidate(long locationId, double pm25, double pm10, long observedAt,
                  double distanceKm, boolean approximateLocation) {
            this.locationId = locationId;
            this.pm25 = pm25;
            this.pm10 = pm10;
            this.observedAt = observedAt;
            this.distanceKm = distanceKm;
            this.approximateLocation = approximateLocation;
        }
    }
}
