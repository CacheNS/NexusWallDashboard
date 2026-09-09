package com.cachens.nexusdashboard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ForecaWeatherTest {
    private static final long NOW = 1788939000000L;

    @Test
    public void coordinatesUseLongitudeFirstWithoutLosingGpsPrecision() {
        assertEquals("19.836941234,45.251671234",
                ForecaWeather.coordinates(45.251671234, 19.836941234));
        assertEquals("-73.5,-12.25", ForecaWeather.coordinates(-12.25, -73.5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCoordinatesAreRejected() {
        ForecaWeather.coordinates(Double.NaN, 19.8);
    }

    @Test
    public void manualCoordinatesBypassCityResolution() {
        ResolvedLocation result = ForecaWeather.coordinateLocation("45.251671234, 19.836941234");
        assertEquals(45.251671234, result.latitude, 0);
        assertEquals(19.836941234, result.longitude, 0);
        assertNull(ForecaWeather.coordinateLocation("Novi Sad, Serbia"));
        assertNull(ForecaWeather.coordinateLocation("Novi Sad"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void outOfRangeManualCoordinatesAreRejected() {
        ForecaWeather.coordinateLocation("91, 19.8");
    }

    @Test
    public void currentAndDailyFieldsAreMappedWithoutSwappingTemperatures() throws Exception {
        WeatherSnapshot result = forecast();
        assertEquals(26.7, result.temperature, 0.001);
        assertEquals(24, result.apparentTemperature, 0.001);
        assertEquals(12.2, result.windSpeed, 0.001);
        assertEquals(0.4, result.precipitation, 0.001);
        assertEquals(0, result.isDay);
        assertEquals(2, result.weatherCode);
        assertEquals(NOW, result.weatherTime);
        assertEquals("", result.weatherStation);
        assertEquals(30, result.dailyHigh[0], 0.001);
        assertEquals(18, result.dailyLow[0], 0.001);
        assertEquals(80, result.dailyCode[0]);
        assertTrue(Double.isNaN(result.dailyHigh[1]));
        assertTrue(Double.isNaN(result.aqi));
    }

    @Test(expected = JSONException.class)
    public void missingTemperatureDoesNotBecomeZero() throws Exception {
        ForecaWeather.parse(new JSONObject("{\"current\":{\"temperature\":null}}"),
                new JSONObject("{\"forecast\":[]}"), 45.25, 19.83, "", NOW);
    }

    @Test
    public void nearestFreshObservationOverridesEstimateWithMatchingFeelsLike() throws Exception {
        WeatherSnapshot result = forecast();
        JSONArray observations = new JSONArray()
                .put(station("Farther", 45.35, "2026-09-09T09:00:00+02:00", 25))
                .put(station("Nearest", 45.26, "2026-09-09T09:00:00+02:00", 24));
        ForecaWeather.applyObservation(result, new JSONObject().put("observations", observations), NOW);
        assertEquals(24, result.temperature, 0.001);
        assertEquals(22, result.apparentTemperature, 0.001);
        assertEquals("Nearest", result.weatherStation);
        assertEquals(ForecaWeather.timestamp("2026-09-09T07:00Z"), result.weatherTime);
        assertTrue(result.weatherStationDistanceKm < 2);
        assertTrue(Double.isNaN(result.precipitation));
        assertEquals(0, result.isDay);
    }

    @Test
    public void staleFutureDistantAndMissingObservationsDoNotOverrideEstimate() throws Exception {
        WeatherSnapshot result = forecast();
        JSONArray observations = new JSONArray()
                .put(station("Stale", 45.25, "2026-09-09T04:00Z", 10))
                .put(station("Future", 45.25, "2026-09-10T07:00Z", 10))
                .put(station("Distant", 46.25, "2026-09-09T07:00Z", 10))
                .put(station("Missing", 45.25, "2026-09-09T07:00Z", 10)
                        .put("temperature", JSONObject.NULL));
        ForecaWeather.applyObservation(result, new JSONObject().put("observations", observations), NOW);
        assertEquals(26.7, result.temperature, 0.001);
        assertEquals("", result.weatherStation);
    }

    @Test
    public void missingObservationFeelsLikeIsNotTakenFromEstimate() throws Exception {
        WeatherSnapshot result = forecast();
        ForecaWeather.applyObservation(result, new JSONObject().put("observations", new JSONArray()
                .put(station("Local", 45.25, "2026-09-09T07:00Z", 25)
                        .put("feelsLikeTemp", JSONObject.NULL))), NOW);
        assertTrue(Double.isNaN(result.apparentTemperature));
    }

    @Test
    public void symbolsCoverPrecipitationCloudsAndMissingData() {
        assertEquals(0, ForecaWeather.conditionCode("d000"));
        assertEquals(1, ForecaWeather.conditionCode("n500"));
        assertEquals(3, ForecaWeather.conditionCode("d400"));
        assertEquals(45, ForecaWeather.conditionCode("d600"));
        assertEquals(61, ForecaWeather.conditionCode("d410"));
        assertEquals(63, ForecaWeather.conditionCode("n430"));
        assertEquals(69, ForecaWeather.conditionCode("d421"));
        assertEquals(85, ForecaWeather.conditionCode("d422"));
        assertEquals(73, ForecaWeather.conditionCode("d432"));
        assertEquals(95, ForecaWeather.conditionCode("d440"));
        assertEquals(-1, ForecaWeather.conditionCode(""));
    }

    @Test
    public void timestampsSupportOffsetsSecondsAndMinutes() {
        assertEquals(NOW, ForecaWeather.timestamp("2026-09-09T09:30:00+02:00"));
        assertEquals(NOW, ForecaWeather.timestamp("2026-09-09T07:30Z"));
        assertEquals(NOW, ForecaWeather.timestamp("2026-09-09T07:30:00.000Z"));
        assertEquals(0, ForecaWeather.timestamp("2026-02-30T07:30Z"));
        assertEquals(0, ForecaWeather.timestamp("invalid"));
    }

    private static WeatherSnapshot forecast() throws Exception {
        return ForecaWeather.parse(new JSONObject("{\"current\":{\"temperature\":26.7,"
                + "\"feelsLikeTemp\":24,\"windSpeed\":12.2,\"precipRate\":0.4,"
                + "\"symbol\":\"n200\",\"time\":\"2026-09-09T09:30:00+02:00\"}}"),
                new JSONObject("{\"forecast\":[{\"date\":\"2026-09-09\",\"maxTemp\":30,"
                        + "\"minTemp\":18,\"symbol\":\"d420\"}]}"), 45.25, 19.83, "Novi Sad", NOW);
    }

    private static JSONObject station(String name, double latitude, String time, double temperature)
            throws Exception {
        return new JSONObject().put("station", name).put("latitude", latitude).put("longitude", 19.83)
                .put("time", time).put("temperature", temperature).put("feelsLikeTemp", temperature - 2)
                .put("symbol", "d000");
    }
}