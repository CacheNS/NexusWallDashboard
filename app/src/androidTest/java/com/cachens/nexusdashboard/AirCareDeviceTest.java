package com.cachens.nexusdashboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.test.InstrumentationTestCase;
import android.test.InstrumentationTestRunner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class AirCareDeviceTest extends InstrumentationTestCase {
    private static final long NOW = 1788939060000L;
    private static final String STATION_NAME = "Novi Sad - Zmaj Ognjena Vuka";

    public void testCachePreservesStationAndAggregateProvenance() throws Exception {
        SharedPreferences preferences = getInstrumentation().getContext()
                .getSharedPreferences("aircare_cache_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        try {
            for (boolean aggregate : new boolean[] {false, true}) {
                WeatherSnapshot original = fixture(aggregate);
                original.save(preferences);
                WeatherSnapshot cached = WeatherSnapshot.load(preferences);
                assertNotNull(cached);
                assertEquals("AirCare", cached.aqiProvider);
                assertEquals(original.aqiSource, cached.aqiSource);
                assertEquals(original.aqi, cached.aqi);
                assertEquals(original.aqiStationId, cached.aqiStationId);
                assertEquals(original.aqiAreaAverage, cached.aqiAreaAverage);
                assertEquals(NOW, cached.aqiObservedAt);
                assertEquals(original.temperature, cached.temperature);
                assertEquals(original.latitude, cached.latitude);
                assertEquals(original.longitude, cached.longitude);
                assertTrue(Double.isNaN(cached.nitrogenDioxide));
                if (aggregate) assertEquals(1000.0, cached.aqiRadiusMeters);
                else assertEquals(original.aqiDistanceKm, cached.aqiDistanceKm);
            }
            preferences.edit().remove("aqiProvider").putString("aqiSource", "SEPA station")
                    .putString("localPmSource", "Sensor.Community")
                    .putBoolean("localPmAqiReady", true).commit();
            WeatherSnapshot migrated = WeatherSnapshot.load(preferences);
            assertTrue(Double.isNaN(migrated.aqi));
            assertTrue(Double.isNaN(migrated.pm25));
            assertEquals("", migrated.aqiSource);
            assertEquals("", migrated.localPmSource);
            assertFalse(migrated.localPmAqiReady);
            assertEquals(25.0, migrated.temperature);
            assertEquals("Rimski Sancevi", migrated.weatherStation);
        } finally {
            preferences.edit().clear().commit();
        }
    }

    public void testSourceLabelsAndRendering() throws Throwable {
        final WeatherSnapshot station = fixture(false);
        final WeatherSnapshot aggregate = fixture(true);
        final WeatherSnapshot unavailable = fixture(false);
        unavailable.applyAirCare(null);
        final Context testContext = getInstrumentation().getContext();
        final SharedPreferences settings = testContext.getSharedPreferences("aircare_render_test",
                Context.MODE_PRIVATE);
        final Context context = new ContextWrapper(getInstrumentation().getTargetContext()) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return "settings".equals(name) ? settings : super.getSharedPreferences(name, mode);
            }
        };
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (boolean serbian : new boolean[] {false, true}) {
                        settings.edit().putBoolean("serbian", serbian).commit();
                        for (WeatherSnapshot weather : new WeatherSnapshot[] {station, aggregate, unavailable}) {
                            DashboardView view = new DashboardView(context);
                            view.setWeather(weather);
                            assertEquals(weather == unavailable ? "" : weather.aqiAreaAverage
                                    ? AppText.get(context, "area_average") : STATION_NAME,
                                    view.aqiSourceLabel());
                            assertFalse(view.aqiSourceLabel().contains("AirCare"));
                            assertFalse(view.aqiSourceLabel().contains("SEPA"));
                            assertEquals(serbian ? "Dobro" : "Good", AppText.aqiLabel(context, 26));
                            view.layout(0, 0, 1280, 800);
                            Bitmap bitmap = Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888);
                            view.draw(new Canvas(bitmap));
                            assertTrue(bitmap.getPixel(1187, 120) != 0);
                            File destination = new File(testContext.getExternalFilesDir(null),
                                    "aircare-" + (serbian ? "sr" : "en")
                                            + (weather == unavailable ? "-unavailable" : weather.aqiAreaAverage
                                            ? "-average" : "-station") + ".png");
                            try {
                                FileOutputStream output = new FileOutputStream(destination);
                                try {
                                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
                                } finally {
                                    output.close();
                                }
                            } catch (IOException error) {
                                throw new AssertionError(error);
                            } finally {
                                bitmap.recycle();
                            }
                        }
                    }
                }
            });
        } finally {
            settings.edit().clear().commit();
        }
    }

    public void testLiveAirCareForSavedCoordinates() throws Exception {
        Bundle arguments = ((InstrumentationTestRunner) getInstrumentation()).getArguments();
        if (!"true".equals(arguments.getString("liveAirCare"))) return;
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences cached = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        assertTrue("Live test requires saved target coordinates", cached.contains("latitude"));
        double latitude = Double.longBitsToDouble(cached.getLong("latitude", 0));
        double longitude = Double.longBitsToDouble(cached.getLong("longitude", 0));
        LegacyTls.initialize(context);
        JSONObject response = new JSONObject(WeatherClient.readUrl(AirCareClient.address(latitude, longitude)));
        AirCareClient.Reading reading = AirCareClient.parse(response, latitude, longitude,
                System.currentTimeMillis());
        assertNotNull("No fresh station or aggregate returned by AirCare", reading);
        assertTrue(reading.aqi >= 0);
        Bundle status = new Bundle();
        status.putString("stream", "AirCare live result: " + (reading.areaAverage ? "Area average" : reading.name)
                + ", EU AQI=" + reading.aqi + ", station ID=" + reading.stationId + "\n");
        getInstrumentation().sendStatus(0, status);
    }

    private static WeatherSnapshot fixture(boolean aggregate) throws Exception {
        WeatherSnapshot result = ForecaWeather.parse(new JSONObject("{\"current\":{\"temperature\":25,"
                + "\"feelsLikeTemp\":23,\"relHumidity\":39,\"windSpeed\":18,"
                + "\"symbol\":\"d000\",\"time\":\"2026-09-09T09:00:00+02:00\"}}"),
                new JSONObject("{\"forecast\":[{\"date\":\"2026-09-09\",\"maxTemp\":30,"
                        + "\"minTemp\":18,\"symbol\":\"d421\"}]}"),
                45.2671, 19.8335, "Novi Sad", NOW);
        result.weatherStation = "Rimski Sancevi";
        result.weatherStationDistanceKm = 9.2;
        JSONArray measurements = new JSONArray("[{\"pid\":7,\"val\":13},{\"pid\":2,\"val\":10},"
                + "{\"pid\":1,\"val\":11},{\"pid\":4,\"val\":null}]");
        JSONObject response = new JSONObject().put("measurementTime", NOW / 1000)
                .put("measurements", measurements).put("stationsRadius", 1000);
        if (!aggregate) {
            response.put("stations", new JSONArray().put(new JSONObject().put("n", STATION_NAME)
                    .put("sid", 84633).put("lat", 45.262416327564814).put("lng", 19.83564376831055)
                    .put("t", NOW / 1000).put("measurements", measurements)));
        }
        result.applyAirCare(AirCareClient.parse(response, result.latitude, result.longitude, NOW));
        return result;
    }
}