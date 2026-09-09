package com.cachens.nexusdashboard;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class AirCareClientTest {
    private static final long NOW = 1788939060000L;

    @Test
    public void coordinatesPreservePrecisionAndOrderAcrossLocales() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("https://getaircare.com/api/v4/api.php?requestType=point"
                    + "&lat=45.251671234&lng=19.836941234&radius=1&p=1",
                    AirCareClient.address(45.251671234, 19.836941234));
            assertTrue(AirCareClient.address(-12.25, -73.5).contains("lat=-12.25&lng=-73.5"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCoordinatesAreRejected() {
        AirCareClient.address(Double.NaN, 19.8);
    }

    @Test
    public void nearestStationKeepsItsOwnNameAqiAndPollutants() throws Exception {
        JSONObject near = station("Novi Sad - Tekelijina", 45.251, NOW, 13);
        near.getJSONArray("measurements").put(measurement(2, 10));
        AirCareClient.Reading result = parse(response().put("stations", new JSONArray()
                .put(station("Farther", 45.26, NOW, 55)).put(near)));
        assertEquals("Novi Sad - Tekelijina", result.name);
        assertEquals(13, result.aqi, 0);
        assertEquals(10, result.pm25, 0);
        assertTrue(Double.isNaN(result.nitrogenDioxide));
        assertEquals(NOW, result.observedAt);
        assertFalse(result.areaAverage);
        assertTrue(result.distanceKm < 1);
    }

    @Test
    public void malformedStaleFutureUnnamedAndDistantStationsUseAreaAverage() throws Exception {
        JSONArray stations = new JSONArray().put("broken")
                .put(station("Stale", 45.25, NOW - AirCareClient.MAX_AGE_MS - 1000, 1))
                .put(station("Future", 45.25, NOW + 301000, 1))
                .put(station(" ", 45.25, NOW, 1))
                .put(station("Distant", 46.25, NOW, 1))
                .put(station("Bad coordinates", 45.25, NOW, 1).put("lng", 200))
                .put(station("US only", 45.25, NOW, 1).put("measurements",
                        new JSONArray().put(measurement(10, 49))));
        AirCareClient.Reading result = parse(response().put("stations", stations));
        assertTrue(result.areaAverage);
        assertEquals("", result.name);
        assertEquals(16, result.aqi, 0);
        assertEquals(1000, result.radiusMeters, 0);
        assertTrue(Double.isNaN(result.distanceKm));
    }

    @Test
    public void invalidValuesAreMissingButZeroIsValidAndForecastIsIgnored() throws Exception {
        JSONObject response = response().put("measurements", new JSONArray()
                .put("bad").put(measurement(7, JSONObject.NULL))
                .put(measurement(7, -1)).put(measurement(7, "Infinity"))
                .put(measurement(7, 100).put("offset", 1)).put(measurement(7, 0))
                .put(measurement(2, JSONObject.NULL)).put(measurement(1, -10)));
        AirCareClient.Reading result = parse(response);
        assertEquals(0, result.aqi, 0);
        assertTrue(Double.isNaN(result.pm25));
        assertTrue(Double.isNaN(result.pm10));
    }

    @Test
    public void missingOrStaleAggregateDoesNotBecomeCurrentData() throws Exception {
        assertNull(parse(new JSONObject()));
        assertNull(parse(response().put("measurementTime", (NOW - AirCareClient.MAX_AGE_MS) / 1000 - 1)));
        assertNull(parse(response().put("measurementTime", Long.MAX_VALUE)));
        assertNull(parse(response().put("measurements", new JSONArray().put(measurement(10, 49)))));
        assertNull(parse(response().put("measurements", new JSONArray().put(measurement(7, -1)))));
    }

    @Test
    public void freshnessBoundariesAndStationTiesAreDeterministic() throws Exception {
        assertTrue(AirCareClient.fresh(NOW - AirCareClient.MAX_AGE_MS, NOW));
        assertFalse(AirCareClient.fresh(NOW - AirCareClient.MAX_AGE_MS - 1, NOW));
        assertTrue(AirCareClient.fresh(NOW + 300000, NOW));
        assertFalse(AirCareClient.fresh(NOW + 300001, NOW));
        JSONObject response = response().put("stations", new JSONArray()
                .put(station("Older", 45.251, NOW - 1000, 12).put("sid", 1))
                .put(station("Higher ID", 45.251, NOW, 14).put("sid", 3))
                .put(station("Winner", 45.251, NOW, 13).put("sid", 2)));
        assertEquals("Winner", parse(response).name);
    }

    @Test
    public void distanceLimitIsThirtyKilometers() throws Exception {
        double boundaryDelta = Math.toDegrees(30.0 / 6371);
        assertFalse(parse(response().put("stations", new JSONArray().put(
                station("Inside", 45.25 + boundaryDelta - 0.000001, NOW, 13)))).areaAverage);
        assertTrue(parse(response().put("stations", new JSONArray().put(
                station("Outside", 45.25 + boundaryDelta + 0.000001, NOW, 13)))).areaAverage);
    }

    @Test
    public void applyingReadingReplacesLegacyPmAndDoesNotAlterForeca() throws Exception {
        WeatherSnapshot snapshot = new WeatherSnapshot();
        snapshot.temperature = 25;
        snapshot.localPmSource = "Sensor.Community";
        snapshot.localPmAqiReady = true;
        snapshot.applyAirCare(parse(response().put("stations", new JSONArray()
                .put(station("Named station", 45.251, NOW, 13)))));
        assertEquals("AirCare", snapshot.aqiProvider);
        assertEquals("Named station", snapshot.aqiSource);
        assertEquals(13, snapshot.aqi, 0);
        assertEquals(25, snapshot.temperature, 0);
        assertEquals("", snapshot.localPmSource);
        assertFalse(snapshot.localPmAqiReady);
        assertTrue(Double.isNaN(snapshot.pm25));
        snapshot.applyAirCare(parse(response()));
        assertTrue(snapshot.aqiAreaAverage);
        assertEquals("", snapshot.aqiSource);
        snapshot.applyAirCare(null);
        assertTrue(Double.isNaN(snapshot.aqi));
        assertTrue(Double.isNaN(snapshot.nitrogenDioxide));
        assertEquals(0, snapshot.aqiObservedAt);
        assertFalse(snapshot.aqiAreaAverage);
        assertEquals(25, snapshot.temperature, 0);
    }

    private static AirCareClient.Reading parse(JSONObject response) {
        return AirCareClient.parse(response, 45.25, 19.83, NOW);
    }

    private static JSONObject response() throws Exception {
        return new JSONObject().put("measurementTime", NOW / 1000).put("stationsRadius", 1000)
                .put("measurements", new JSONArray().put(measurement(7, 16))
                        .put(measurement(10, 49)).put(measurement(4, 14)));
    }

    private static JSONObject station(String name, double latitude, long timestamp, double aqi)
            throws Exception {
        return new JSONObject().put("n", name).put("sid", 84633).put("lat", latitude)
                .put("lng", 19.83).put("t", timestamp / 1000)
                .put("measurements", new JSONArray().put(measurement(7, aqi)));
    }

    private static JSONObject measurement(int pollutant, Object value) throws Exception {
        return new JSONObject().put("pid", pollutant).put("val", value);
    }
}