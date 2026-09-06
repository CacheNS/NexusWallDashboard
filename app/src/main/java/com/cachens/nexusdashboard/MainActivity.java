package com.cachens.nexusdashboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements LocationListener, DashboardView.Listener,
        MotionDetector.Listener {
    private static final int PERMISSION_REQUEST = 42;
    private static final int PHOTO_PICKER_REQUEST = 43;
    private static final long WEATHER_REFRESH_MS = 30L * 60L * 1000L;
    private static final long LOCATION_INTERVAL_MS = 10L * 60L * 1000L;
    private static final long PHOTO_INTERVAL_MS = 10L * 60L * 1000L;
    private static final long NEWS_REFRESH_MS = 30L * 60L * 1000L;
    private static final long NETWORK_RETRY_MS = 60L * 1000L;
    private static final long IDLE_TIMEOUT_MS = 2L * 60L * 1000L;
    private static final long IDLE_CHECK_MS = 10L * 1000L;
    private static final float LOCATION_DISTANCE_METERS = 1000f;

    private final ExecutorService weatherExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService photoExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService newsExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private DashboardView dashboardView;
    private LocationManager locationManager;
    private PhotoRepository photoRepository;
    private MotionDetector motionDetector;
    private long lastWeatherFetchStarted;
    private long lastInteractionAt;
    private boolean dimmed;
    private boolean permissionRequested;
    private boolean hasWeather;

    private final Runnable photoRotation = new Runnable() {
        @Override
        public void run() {
            loadNextPhoto();
            handler.postDelayed(this, PHOTO_INTERVAL_MS);
        }
    };

    private final Runnable newsRefresh = new Runnable() {
        @Override
        public void run() {
            updateNews();
            handler.postDelayed(this, NEWS_REFRESH_MS);
        }
    };

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            if (!dimmed && System.currentTimeMillis() - lastInteractionAt >= IDLE_TIMEOUT_MS) {
                dimDisplay();
            }
            handler.postDelayed(this, IDLE_CHECK_MS);
        }
    };

    private final Runnable weatherRetry = new Runnable() {
        @Override
        public void run() {
            String query = manualLocation();
            if (query.length() == 0) {
                startLocationUpdates();
            } else {
                updateWeatherForPlace(query, true);
            }
        }
    };

    private final Runnable newsRetry = new Runnable() {
        @Override
        public void run() {
            updateNews();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        dashboardView = new DashboardView(this);
        dashboardView.setListener(this);
        setContentView(dashboardView);
        enterImmersiveMode();
        LegacyTls.initialize(this);

        photoRepository = new PhotoRepository(this);
        importStagedPhotoCache();
        motionDetector = new MotionDetector(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        lastInteractionAt = System.currentTimeMillis();

        WeatherSnapshot cachedWeather = WeatherSnapshot.load(getSharedPreferences("weather", MODE_PRIVATE));
        if (cachedWeather != null) {
            hasWeather = true;
            dashboardView.setWeather(cachedWeather);
            dashboardView.setStatus(AppText.get(this, "last_updated") + " "
                    + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(cachedWeather.fetchedAt)));
        }
        NewsSnapshot cachedNews = NewsSnapshot.load(getSharedPreferences("news", MODE_PRIVATE));
        if (cachedNews != null) {
            dashboardView.setNews(cachedNews);
        }

        handler.postDelayed(photoRotation, 1000L);
        handler.post(newsRefresh);
        handler.post(idleCheck);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        wakeDisplay();
        startAvailableFeatures();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        if (motionDetector != null) {
            motionDetector.stop();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(photoRotation);
        handler.removeCallbacks(newsRefresh);
        handler.removeCallbacks(idleCheck);
        handler.removeCallbacks(weatherRetry);
        handler.removeCallbacks(newsRetry);
        weatherExecutor.shutdownNow();
        photoExecutor.shutdownNow();
        newsExecutor.shutdownNow();
        super.onDestroy();
    }

    private void startAvailableFeatures() {
        if (Build.VERSION.SDK_INT >= 23 && !permissionRequested) {
            List<String> missing = new ArrayList<String>();
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.CAMERA);
            }
            if (manualLocation().length() == 0
                    && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
                missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (!missing.isEmpty()) {
                permissionRequested = true;
                requestPermissions(missing.toArray(new String[missing.size()]), PERMISSION_REQUEST);
            }
        }

        loadNextPhoto();
        if (hasPermission(Manifest.permission.CAMERA)) {
            motionDetector.start();
        }
        String query = manualLocation();
        if (query.length() == 0) {
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startLocationUpdates();
            }
        } else {
            updateWeatherForPlace(query, false);
        }
    }

    private boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String manualLocation() {
        return getSharedPreferences("settings", MODE_PRIVATE).getString("location_query", "Novi Sad").trim();
    }

    private void startLocationUpdates() {
        boolean enabled = false;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                enabled = true;
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        LOCATION_INTERVAL_MS, LOCATION_DISTANCE_METERS, this);
                Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    updateWeather(location, false);
                }
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                enabled = true;
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        LOCATION_INTERVAL_MS, LOCATION_DISTANCE_METERS, this);
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    updateWeather(location, false);
                }
            }
        } catch (SecurityException error) {
            dashboardView.setStatus(AppText.get(this, "location_required"));
            return;
        }
        dashboardView.setStatus(enabled
                ? AppText.get(this, "finding_location")
                : AppText.get(this, "enable_location"));
    }

    @Override
    public void onLocationChanged(Location location) {
        updateWeather(location, true);
    }

    private void updateWeather(final Location location, boolean force) {
        if (!beginWeatherFetch(force)) {
            return;
        }
        dashboardView.setStatus(AppText.get(this, "updating"));
        weatherExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    showWeather(WeatherClient.fetch(location.getLatitude(), location.getLongitude()));
                } catch (Exception error) {
                    showWeatherError(error);
                }
            }
        });
    }

    private void updateWeatherForPlace(final String query, boolean force) {
        if (!beginWeatherFetch(force)) {
            return;
        }
        dashboardView.setStatus(AppText.get(this, "updating"));
        weatherExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    showWeather(WeatherClient.fetchPlace(query));
                } catch (Exception error) {
                    showWeatherError(error);
                }
            }
        });
    }

    private boolean beginWeatherFetch(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastWeatherFetchStarted < WEATHER_REFRESH_MS) {
            return false;
        }
        if (now - lastWeatherFetchStarted < 30000L) {
            return false;
        }
        lastWeatherFetchStarted = now;
        return true;
    }

    private void showWeather(final WeatherSnapshot result) {
        hasWeather = true;
        lastWeatherFetchStarted = result.fetchedAt;
        handler.removeCallbacks(weatherRetry);
        PmHistory.apply(result, getSharedPreferences("pm_history", MODE_PRIVATE));
        result.save(getSharedPreferences("weather", MODE_PRIVATE));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dashboardView.setWeather(result);
                dashboardView.setStatus(AppText.get(MainActivity.this, "updated") + " "
                        + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(result.fetchedAt)));
            }
        });
    }

    private void showWeatherError(final Exception error) {
        lastWeatherFetchStarted = 0;
        handler.removeCallbacks(weatherRetry);
        handler.postDelayed(weatherRetry, NETWORK_RETRY_MS);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dashboardView.setStatus(AppText.get(MainActivity.this,
                        hasWeather ? "offline_cached" : "offline_retry"));
            }
        });
    }

    private void loadNextPhoto() {
        final int width = dashboardView.getWidth();
        final int height = dashboardView.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        photoExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final android.graphics.Bitmap bitmap = photoRepository.loadNext(width, height);
                if (bitmap != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dashboardView.setBackgroundBitmap(bitmap);
                        }
                    });
                }
            }
        });
    }

    private void importStagedPhotoCache() {
        photoExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File staging = new File(Environment.getExternalStorageDirectory(),
                        "NexusWallDashboard/city-cache");
                try {
                    if (photoRepository.importStagedCityCache(staging)) {
                        Log.i("NexusDashboard", "Imported "
                                + photoRepository.cityPhotoCount() + " Novi Sad photos");
                    }
                } catch (Exception error) {
                    Log.e("NexusDashboard", "Could not import Novi Sad photo cache", error);
                }
            }
        });
    }

    private void updateNews() {
        newsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final NewsSnapshot result = NewsClient.fetch();
                    result.save(getSharedPreferences("news", MODE_PRIVATE));
                    handler.removeCallbacks(newsRetry);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dashboardView.setNews(result);
                        }
                    });
                } catch (Exception error) {
                    Log.e("NexusDashboard", "News update failed", error);
                    handler.removeCallbacks(newsRetry);
                    handler.postDelayed(newsRetry, NETWORK_RETRY_MS);
                }
            }
        });
    }

    @Override
    public void onProviderDisabled(String provider) {
        dashboardView.setStatus(AppText.get(this, "enable_location"));
    }

    @Override
    public void onProviderEnabled(String provider) {
        startAvailableFeatures();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            startAvailableFeatures();
        }
    }

    @Override
    public void onSettingsRequested() {
        final SharedPreferences preferences = getSharedPreferences("settings", MODE_PRIVATE);
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(AppText.get(this, "settings_hint"));
        input.setText(preferences.getString("location_query", "Novi Sad"));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(AppText.get(this, "settings_title"))
                .setView(input)
                .setPositiveButton(AppText.get(this, "save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String query = input.getText().toString().trim();
                        preferences.edit().putString("location_query", query).apply();
                        lastWeatherFetchStarted = 0;
                        if (locationManager != null) {
                            locationManager.removeUpdates(MainActivity.this);
                        }
                        if (query.length() == 0) {
                            startAvailableFeatures();
                        } else {
                            updateWeatherForPlace(query, true);
                        }
                    }
                })
                .setNegativeButton(AppText.get(this, "cancel"), null)
                .setNeutralButton(AppText.get(this, "photos"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openPhotoPicker();
                    }
                })
                .show();
    }

    private void openPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PHOTO_PICKER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PHOTO_PICKER_REQUEST || resultCode != RESULT_OK || data == null) {
            return;
        }
        photoExecutor.execute(new Runnable() {
            @Override
            public void run() {
                importSelectedPhotos(data);
                photoRepository.reload();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadNextPhoto();
                    }
                });
            }
        });
    }

    private void importSelectedPhotos(Intent data) {
        List<android.net.Uri> selected = new ArrayList<android.net.Uri>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                selected.add(clipData.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            selected.add(data.getData());
        }
        if (selected.isEmpty()) {
            return;
        }

        File directory = photoRepository.directory();
        File[] existing = directory.listFiles();
        if (existing != null) {
            for (File file : existing) {
                file.delete();
            }
        }
        byte[] buffer = new byte[32 * 1024];
        for (int i = 0; i < selected.size(); i++) {
            InputStream input = null;
            FileOutputStream output = null;
            try {
                input = getContentResolver().openInputStream(selected.get(i));
                if (input == null) {
                    continue;
                }
                output = new FileOutputStream(new File(directory, String.format(Locale.US, "photo_%03d", i)));
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (Exception error) {
                Log.e("NexusDashboard", "Could not import selected photo " + i, error);
            } finally {
                try {
                    if (input != null) input.close();
                } catch (Exception ignored) {
                }
                try {
                    if (output != null) output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        wakeDisplay();
    }

    @Override
    public void onMotionDetected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                wakeDisplay();
            }
        });
    }

    private void dimDisplay() {
        dimmed = true;
        dashboardView.setDimmed(true);
        WindowManager.LayoutParams parameters = getWindow().getAttributes();
        parameters.screenBrightness = 0.01f;
        getWindow().setAttributes(parameters);
    }

    private void wakeDisplay() {
        lastInteractionAt = System.currentTimeMillis();
        if (!dimmed) {
            return;
        }
        dimmed = false;
        dashboardView.setDimmed(false);
        WindowManager.LayoutParams parameters = getWindow().getAttributes();
        parameters.screenBrightness = 0.65f;
        getWindow().setAttributes(parameters);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
