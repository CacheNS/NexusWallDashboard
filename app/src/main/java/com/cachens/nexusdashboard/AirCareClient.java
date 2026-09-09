package com.cachens.nexusdashboard;

import org.json.JSONArray;
import org.json.JSONObject;

final class AirCareClient {
    static final long MAX_AGE_MS = 2L * 60 * 60 * 1000;
    private static final long FUTURE_TOLERANCE_MS = 5L * 60 * 1000;
    private static final double MAX_DISTANCE_KM = 30;

    private AirCareClient() {
    }

    static String address(double latitude, double longitude) {
        if (!validCoordinates(latitude, longitude)) {
            throw new IllegalArgumentException("Invalid air-quality coordinates");
        }
        return "https://getaircare.com/api/v4/api.php?requestType=point&lat="
                + Double.toString(latitude) + "&lng=" + Double.toString(longitude)
                + "&radius=1&p=1";
    }

    static Reading parse(JSONObject response, double latitude, double longitude, long now) {
        address(latitude, longitude);
        Reading nearest = null;
        JSONArray stations = response.optJSONArray("stations");
        for (int index = 0; stations != null && index < stations.length(); index++) {
            JSONObject station = stations.optJSONObject(index);
            if (station == null) continue;
            double stationLatitude = station.optDouble("lat", Double.NaN);
            double stationLongitude = station.optDouble("lng", Double.NaN);
            String name = station.isNull("n") ? "" : station.optString("n", "").trim();
            long observedAt = timestamp(station, "t");
            JSONArray measurements = station.optJSONArray("measurements");
            if (name.length() == 0 || !validCoordinates(stationLatitude, stationLongitude)
                    || !fresh(observedAt, now) || Double.isNaN(value(measurements, 7))) continue;
            double distance = distanceKm(latitude, longitude, stationLatitude, stationLongitude);
            if (distance > MAX_DISTANCE_KM) continue;
            long stationId = station.optLong("sid", 0);
            if (nearest == null || distance < nearest.distanceKm
                    || (distance == nearest.distanceKm && observedAt > nearest.observedAt)
                    || (distance == nearest.distanceKm && observedAt == nearest.observedAt
                    && stationId < nearest.stationId)) {
                nearest = new Reading(measurements, name, stationId, observedAt, distance,
                        Double.NaN, false);
            }
        }
        if (nearest != null) return nearest;
        long observedAt = timestamp(response, "measurementTime");
        JSONArray measurements = response.optJSONArray("measurements");
        if (!fresh(observedAt, now) || Double.isNaN(value(measurements, 7))) return null;
        double radius = response.optDouble("stationsRadius", Double.NaN);
        return new Reading(measurements, "", 0, observedAt, Double.NaN,
                nonnegative(radius) ? radius : Double.NaN, true);
    }

    private static double value(JSONArray measurements, int pollutantId) {
        for (int index = 0; measurements != null && index < measurements.length(); index++) {
            JSONObject measurement = measurements.optJSONObject(index);
            if (measurement == null || measurement.optInt("pid", -1) != pollutantId
                    || measurement.optDouble("offset", 0) != 0) continue;
            double value = measurement.optDouble("val", Double.NaN);
            if (nonnegative(value)) return value;
        }
        return Double.NaN;
    }

    private static long timestamp(JSONObject object, String key) {
        long seconds = object.optLong(key, 0);
        return seconds > 0 && seconds <= Long.MAX_VALUE / 1000 ? seconds * 1000 : 0;
    }

    static boolean fresh(long observedAt, long now) {
        return observedAt > 0 && observedAt <= now + FUTURE_TOLERANCE_MS
                && observedAt >= now - MAX_AGE_MS;
    }

    private static boolean nonnegative(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0;
    }

    private static boolean validCoordinates(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    private static double distanceKm(double latitude, double longitude,
                                     double stationLatitude, double stationLongitude) {
        double latitudeDelta = Math.toRadians(stationLatitude - latitude);
        double longitudeDelta = Math.toRadians(stationLongitude - longitude);
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(stationLatitude))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, haversine))));
    }

    static final class Reading {
        final String name;
        final long stationId;
        final long observedAt;
        final double distanceKm;
        final double radiusMeters;
        final boolean areaAverage;
        final double aqi;
        final double pm25;
        final double pm10;
        final double nitrogenDioxide;
        final double ozone;

        Reading(JSONArray measurements, String name, long stationId, long observedAt,
                double distanceKm, double radiusMeters, boolean areaAverage) {
            this.name = name;
            this.stationId = stationId;
            this.observedAt = observedAt;
            this.distanceKm = distanceKm;
            this.radiusMeters = radiusMeters;
            this.areaAverage = areaAverage;
            aqi = value(measurements, 7);
            pm25 = value(measurements, 2);
            pm10 = value(measurements, 1);
            nitrogenDioxide = value(measurements, 4);
            ozone = value(measurements, 5);
        }
    }
}