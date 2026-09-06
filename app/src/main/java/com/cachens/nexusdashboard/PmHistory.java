package com.cachens.nexusdashboard;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class PmHistory {
    private static final long WINDOW_MS = 24L * 60L * 60L * 1000L;
    private static final long MIN_SPAN_MS = 20L * 60L * 60L * 1000L;
    private static final long RETAIN_STALE_MS = 75L * 60L * 1000L;
    private static final int MIN_SAMPLES = 36;
    private static final int MAX_MISSED_REFRESHES = 2;

    private PmHistory() {
    }

    static void apply(WeatherSnapshot result, SharedPreferences preferences) {
        long now = result.fetchedAt;
        List<Sample> samples = parse(preferences.getString("samples", ""), now);
        int missed = preferences.getInt("missed", 0);
        long storedSensorId = preferences.getLong("sensor_id", 0);
        double storedLatitude = readDouble(preferences, "target_latitude");
        double storedLongitude = readDouble(preferences, "target_longitude");
        boolean sameTarget = Double.isNaN(storedLatitude)
                || distanceKm(storedLatitude, storedLongitude, result.latitude, result.longitude) <= 1;
        boolean sameSensor = result.localPmLocationId == 0 || storedSensorId == 0
                || storedSensorId == result.localPmLocationId;
        if (!sameTarget || !sameSensor) {
            samples.clear();
            missed = 0;
        }

        if (result.localPmSource != null && result.localPmObservedAt > 0) {
            addSample(samples, result.localPmObservedAt, result.localPm25, result.localPm10);
            missed = 0;
        } else {
            missed++;
            Sample latest = samples.isEmpty() ? null : samples.get(samples.size() - 1);
            if (latest != null && missed <= MAX_MISSED_REFRESHES
                    && now - latest.timestamp <= RETAIN_STALE_MS) {
                result.localPmSource = preferences.getString("source", "Sensor.Community Telep");
                result.localPmLocationId = storedSensorId;
                result.localPmObservedAt = latest.timestamp;
                result.localPm25 = latest.pm25;
                result.localPm10 = latest.pm10;
                result.pm25 = latest.pm25;
                result.pm10 = latest.pm10;
            }
        }

        if (hasMatureWindow(samples) && result.humidity < 85) {
            double averagePm25 = average(samples, true);
            double averagePm10 = average(samples, false);
            if (plausible(averagePm25, result.referencePm25)
                    && plausible(averagePm10, result.referencePm10)) {
                double localAqi = EuropeanAqi.fromParticles(averagePm25, averagePm10);
                if (!Double.isNaN(localAqi)) {
                    result.aqi = Double.isNaN(result.aqi) ? localAqi : Math.max(result.aqi, localAqi);
                    result.localPmAqiReady = true;
                }
            }
        }

        long persistedSensorId = result.localPmLocationId;
        if (persistedSensorId == 0 && sameTarget) {
            persistedSensorId = storedSensorId;
        }
        preferences.edit()
                .putString("samples", serialize(samples))
                .putString("source", result.localPmSource == null ? "" : result.localPmSource)
                .putLong("sensor_id", persistedSensorId)
                .putLong("target_latitude", Double.doubleToRawLongBits(result.latitude))
                .putLong("target_longitude", Double.doubleToRawLongBits(result.longitude))
                .putInt("missed", missed)
                .apply();
    }

    private static void addSample(List<Sample> samples, long timestamp, double pm25, double pm10) {
        if (!samples.isEmpty() && timestamp - samples.get(samples.size() - 1).timestamp < 20L * 60L * 1000L) {
            return;
        }
        samples.add(new Sample(timestamp, pm25, pm10));
    }

    private static List<Sample> parse(String encoded, long now) {
        List<Sample> result = new ArrayList<Sample>();
        if (encoded.length() == 0) {
            return result;
        }
        String[] rows = encoded.split(";");
        for (String row : rows) {
            String[] values = row.split(",");
            if (values.length != 3) {
                continue;
            }
            try {
                long timestamp = Long.parseLong(values[0]);
                if (timestamp >= now - WINDOW_MS && timestamp <= now + 5L * 60L * 1000L) {
                    result.add(new Sample(timestamp, Double.parseDouble(values[1]),
                            Double.parseDouble(values[2])));
                }
            } catch (NumberFormatException ignored) {
                // Ignore a damaged cached row while preserving the remaining history.
            }
        }
        return result;
    }

    private static boolean hasMatureWindow(List<Sample> samples) {
        return samples.size() >= MIN_SAMPLES
                && samples.get(samples.size() - 1).timestamp - samples.get(0).timestamp >= MIN_SPAN_MS;
    }

    private static double average(List<Sample> samples, boolean pm25) {
        double total = 0;
        int count = 0;
        for (Sample sample : samples) {
            double value = pm25 ? sample.pm25 : sample.pm10;
            if (!Double.isNaN(value)) {
                total += value;
                count++;
            }
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private static boolean plausible(double local, double reference) {
        return Double.isNaN(local) || Double.isNaN(reference)
                || local <= Math.max(75, reference * 3);
    }

    private static String serialize(List<Sample> samples) {
        StringBuilder result = new StringBuilder();
        for (Sample sample : samples) {
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(sample.timestamp).append(',')
                    .append(sample.pm25).append(',')
                    .append(sample.pm10);
        }
        return result.toString();
    }

    private static double readDouble(SharedPreferences preferences, String key) {
        return Double.longBitsToDouble(preferences.getLong(key,
                Double.doubleToRawLongBits(Double.NaN)));
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

    private static final class Sample {
        final long timestamp;
        final double pm25;
        final double pm10;

        Sample(long timestamp, double pm25, double pm10) {
            this.timestamp = timestamp;
            this.pm25 = pm25;
            this.pm10 = pm10;
        }
    }
}
