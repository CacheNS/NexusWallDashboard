package com.cachens.nexusdashboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.test.InstrumentationTestCase;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ForecaDeviceTest extends InstrumentationTestCase {
    public void testCachePreservesForecaSourceAndCoordinates() throws Exception {
        SharedPreferences preferences = getInstrumentation().getContext()
                .getSharedPreferences("weather_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        try {
            WeatherSnapshot original = fixture();
            original.weatherStation = "Rimski Sancevi";
            original.weatherStationDistanceKm = 9.2;
            original.save(preferences);
            WeatherSnapshot cached = WeatherSnapshot.load(preferences);
            assertNotNull(cached);
            assertEquals(original.temperature, cached.temperature);
            assertEquals(original.apparentTemperature, cached.apparentTemperature);
            assertEquals(original.latitude, cached.latitude);
            assertEquals(original.longitude, cached.longitude);
            assertEquals(original.weatherTime, cached.weatherTime);
            assertEquals(original.weatherStation, cached.weatherStation);
            assertEquals(original.weatherStationDistanceKm, cached.weatherStationDistanceKm);
            assertTrue(Double.isNaN(cached.aqi));
            preferences.edit().remove("provider").commit();
            assertNull(WeatherSnapshot.load(preferences));
        } finally {
            preferences.edit().clear().commit();
        }
    }

    public void testWeatherSourcesRenderOnTablet() throws Throwable {
        final WeatherSnapshot weather = fixture();
        final Context testContext = getInstrumentation().getContext();
        final SharedPreferences settings = testContext.getSharedPreferences("render_test", Context.MODE_PRIVATE);
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
                        for (boolean measured : new boolean[] {false, true}) {
                            weather.weatherStation = measured ? "Novi Sad - Rimski Sancevi Observatory" : "";
                            weather.weatherStationDistanceKm = measured ? 9.2 : Double.NaN;
                            DashboardView view = new DashboardView(context);
                            view.setWeather(weather);
                            view.setStatus("Foreca fixture");
                            view.layout(0, 0, 1280, 800);
                            Bitmap bitmap = Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888);
                            view.draw(new Canvas(bitmap));
                            File destination = new File(testContext.getExternalFilesDir(null),
                                    "foreca-" + (serbian ? "sr" : "en")
                                            + (measured ? "-station" : "-estimate") + ".png");
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

    private static WeatherSnapshot fixture() throws Exception {
        return ForecaWeather.parse(new JSONObject("{\"current\":{\"temperature\":25,"
                + "\"feelsLikeTemp\":23,\"relHumidity\":39,\"windSpeed\":18,"
                + "\"symbol\":\"d000\",\"time\":\"2026-09-09T09:00:00+02:00\"}}"),
                new JSONObject("{\"forecast\":[{\"date\":\"2026-09-09\",\"maxTemp\":30,"
                        + "\"minTemp\":18,\"symbol\":\"d421\"}]}"),
                45.251671234, 19.836941234, "Novi Sad, Vojvodina", System.currentTimeMillis());
    }
}