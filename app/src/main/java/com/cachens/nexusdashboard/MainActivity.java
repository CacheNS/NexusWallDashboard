package com.cachens.nexusdashboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
    private static final long WEATHER_REFRESH_MS = 60L * 60L * 1000L;
    private static final long WAKE_REFRESH_MIN_INTERVAL_MS = 10L * 60L * 1000L;
    private static final long LOCATION_INTERVAL_MS = 10L * 60L * 1000L;
    private static final long LOCATION_FRESHNESS_MS = 30L * 60L * 1000L;
    private static final long LOCATION_PROMPT_DELAY_MS = 3000L;
    private static final long NEWS_REFRESH_MS = 60L * 60L * 1000L;
    private static final long NETWORK_RETRY_MS = 60L * 1000L;
    private static final long IMMEDIATE_IDLE_TIMEOUT_MS = 750L;
    private static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 3;
    private static final int[] IDLE_TIMEOUT_OPTIONS_SECONDS = {0, 1, 3, 5, 10, 15, 30, 60};
    private static final float LOCATION_DISTANCE_METERS = 1000f;

    private final ExecutorService weatherExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService photoExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService newsExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private DashboardView dashboardView;
    private LocationManager locationManager;
    private PhotoRepository photoRepository;
    private MotionDetector motionDetector;
    private Location lastLocation;
    private volatile long lastWeatherFetchElapsed;
    private volatile long lastNewsFetchElapsed;
    private volatile long lastWeatherFailureElapsed;
    private volatile long lastNewsFailureElapsed;
    private long lastInteractionAt;
    private boolean dimmed;
    private boolean locationPromptShown;
    private boolean permissionRequested;
    private boolean hasWeather;
    private boolean photoLoadInProgress;
    private boolean resumed;
    private volatile boolean destroyed;
    private volatile boolean weatherFetchInProgress;
    private volatile boolean newsFetchInProgress;
    private volatile int weatherLocationRevision;
    private volatile String activeWeatherTarget = "";
    private volatile boolean weatherNeedsRetry;
    private volatile boolean newsNeedsRetry;

    private final BroadcastReceiver dateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadPhotoForToday();
        }
    };

    private final Runnable newsRefresh = new Runnable() {
        @Override
        public void run() {
            updateNews(false);
            if (!newsFetchInProgress) {
                scheduleNewsAfterSkippedAttempt();
            }
        }
    };

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !resumed || dimmed) {
                return;
            }
            long timeout = idleTimeoutMs();
            long remaining = timeout
                    - (SystemClock.uptimeMillis() - lastInteractionAt);
            if (remaining <= 0) {
                dimDisplay();
            } else {
                handler.postDelayed(this, remaining);
            }
        }
    };

    private final Runnable weatherRetry = new Runnable() {
        @Override
        public void run() {
            refreshWeather(true);
            if (!weatherFetchInProgress) {
                scheduleWeatherAfterSkippedAttempt();
            }
        }
    };

    private final Runnable weatherRefresh = new Runnable() {
        @Override
        public void run() {
            refreshWeather(false);
            if (!weatherFetchInProgress) {
                scheduleWeatherAfterSkippedAttempt();
            }
        }
    };

    private final Runnable newsRetry = new Runnable() {
        @Override
        public void run() {
            updateNews(true);
            if (!newsFetchInProgress) {
                scheduleNewsAfterSkippedAttempt();
            }
        }
    };

    private final Runnable locationPrompt = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !resumed || hasWeather || lastLocation != null
                    || manualLocation().length() > 0 || locationPromptShown) {
                return;
            }
            locationPromptShown = true;
            showLocationChoice();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        migrateLocationSelection();
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
        lastInteractionAt = SystemClock.uptimeMillis();

    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        dashboardView.setActive(true);
        lastInteractionAt = SystemClock.uptimeMillis();
        enterImmersiveMode();
        restoreCachedData();
        wakeDisplay();
        startAvailableFeatures();
        resumeDataRefreshes();
        IntentFilter dateChanges = new IntentFilter();
        dateChanges.addAction(Intent.ACTION_DATE_CHANGED);
        dateChanges.addAction(Intent.ACTION_TIME_CHANGED);
        dateChanges.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(dateChangeReceiver, dateChanges);
        dashboardView.post(new Runnable() {
            @Override
            public void run() {
                loadPhotoForToday();
            }
        });
        scheduleIdleTimeout();
    }

    @Override
    protected void onPause() {
        resumed = false;
        dashboardView.setActive(false);
        unregisterReceiver(dateChangeReceiver);
        handler.removeCallbacks(weatherRefresh);
        handler.removeCallbacks(weatherRetry);
        handler.removeCallbacks(newsRefresh);
        handler.removeCallbacks(newsRetry);
        handler.removeCallbacks(idleCheck);
        handler.removeCallbacks(locationPrompt);
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
        destroyed = true;
        handler.removeCallbacks(newsRefresh);
        handler.removeCallbacks(idleCheck);
        handler.removeCallbacks(weatherRetry);
        handler.removeCallbacks(weatherRefresh);
        handler.removeCallbacks(newsRetry);
        handler.removeCallbacks(locationPrompt);
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
        return getSharedPreferences("settings", MODE_PRIVATE).getString("location_query", "").trim();
    }

    private String forecaApiKey() {
        return selectForecaApiKey(getSharedPreferences("foreca", MODE_PRIVATE)
                .getString("api_key", ""), BuildConfig.FORECA_API_KEY);
    }

    static String selectForecaApiKey(String overrideKey, String defaultKey) {
        String configured = overrideKey == null ? "" : overrideKey.trim();
        return configured.length() > 0 ? configured : defaultKey == null ? "" : defaultKey.trim();
    }

    private void migrateLocationSelection() {
        SharedPreferences preferences = getSharedPreferences("settings", MODE_PRIVATE);
        if (preferences.getBoolean("location_selection_migrated", false)) {
            return;
        }
        preferences.edit()
                .remove("location_query")
                .putBoolean("location_selection_migrated", true)
                .apply();
        getSharedPreferences("weather", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("pm_history", MODE_PRIVATE).edit().clear().apply();
    }

    private void startLocationUpdates() {
        boolean enabled = false;
        try {
            locationManager.removeUpdates(this);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                enabled = true;
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        LOCATION_INTERVAL_MS, LOCATION_DISTANCE_METERS, this);
                Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    lastLocation = location;
                    updateWeather(location, false);
                }
                if (!isRecentLocation(location)
                        && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
                }
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                enabled = true;
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        LOCATION_INTERVAL_MS, LOCATION_DISTANCE_METERS, this);
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    lastLocation = location;
                    updateWeather(location, false);
                }
            }
        } catch (SecurityException error) {
            dashboardView.setStatus(AppText.get(this, "location_required"));
            return;
        }
        dashboardView.setStatus(forecaApiKey().length() == 0
            ? AppText.get(this, "foreca_key_required") : enabled
                ? AppText.get(this, "finding_location")
                : AppText.get(this, "enable_location"));
        if (lastLocation == null && !hasWeather) {
            handler.removeCallbacks(locationPrompt);
            handler.postDelayed(locationPrompt, LOCATION_PROMPT_DELAY_MS);
        }
    }

    private static boolean isRecentLocation(Location location) {
        return location != null && location.getTime() > 0
                && System.currentTimeMillis() - location.getTime() <= LOCATION_FRESHNESS_MS;
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        updateWeather(location, false);
    }

    private void refreshWeather(boolean force) {
        String query = manualLocation();
        if (query.length() > 0) {
            updateWeatherForPlace(query, force);
        } else if (lastLocation != null) {
            updateWeather(lastLocation, force);
        } else {
            startLocationUpdates();
        }
    }

    private void updateWeather(final Location location, boolean force) {
        if (destroyed || weatherExecutor.isShutdown()) {
            return;
        }
        String target = String.format(Locale.US, "coordinates:%.5f,%.5f",
                location.getLatitude(), location.getLongitude());
        if (!beginWeatherFetch(force, target)) {
            return;
        }
        dashboardView.setStatus(AppText.get(this, "updating"));
        final int requestRevision = weatherLocationRevision;
        final String apiKey = forecaApiKey();
        weatherExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    showWeather(WeatherClient.fetch(location.getLatitude(), location.getLongitude(), apiKey),
                            requestRevision);
                } catch (Exception error) {
                    showWeatherError(error, requestRevision);
                }
            }
        });
    }

    private void updateWeatherForPlace(final String query, boolean force) {
        if (destroyed || weatherExecutor.isShutdown()) {
            return;
        }
        if (!beginWeatherFetch(force, "place:" + query.toLowerCase(Locale.US))) {
            return;
        }
        dashboardView.setStatus(AppText.get(this, "updating"));
        final int requestRevision = weatherLocationRevision;
        final String apiKey = forecaApiKey();
        weatherExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    showWeather(WeatherClient.fetchPlace(query, apiKey), requestRevision);
                } catch (Exception error) {
                    showWeatherError(error, requestRevision);
                }
            }
        });
    }

    private boolean beginWeatherFetch(boolean force, String target) {
        if (forecaApiKey().length() == 0) {
            dashboardView.setStatus(AppText.get(this, "foreca_key_required"));
            return false;
        }
        boolean targetChanged = activeWeatherTarget.length() > 0
                && !activeWeatherTarget.equals(target);
        if (targetChanged) {
            weatherLocationRevision++;
            lastWeatherFetchElapsed = 0;
            activeWeatherTarget = target;
        }
        if (weatherFetchInProgress) {
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetchElapsed > 0
                && now - lastWeatherFetchElapsed < WEATHER_REFRESH_MS) {
            return false;
        }
        if (lastWeatherFetchElapsed > 0 && now - lastWeatherFetchElapsed < 30000L) {
            return false;
        }
        lastWeatherFetchElapsed = now;
        activeWeatherTarget = target;
        weatherFetchInProgress = true;
        return true;
    }

    private void showWeather(final WeatherSnapshot result, int requestRevision) {
        weatherFetchInProgress = false;
        final int completedRevision = requestRevision;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (destroyed) {
                    return;
                }
                if (completedRevision != weatherLocationRevision) {
                    if (resumed) {
                        refreshWeather(true);
                    }
                    return;
                }
                weatherNeedsRetry = false;
                lastWeatherFailureElapsed = 0;
                result.save(getSharedPreferences("weather", MODE_PRIVATE));
                if (!resumed) {
                    return;
                }
                hasWeather = true;
                handler.removeCallbacks(weatherRetry);
                handler.removeCallbacks(weatherRefresh);
                handler.postDelayed(weatherRefresh, remainingDelay(
                        SystemClock.elapsedRealtime(), lastWeatherFetchElapsed,
                        WEATHER_REFRESH_MS));
                dashboardView.setWeather(result);
                dashboardView.setStatus(AppText.get(MainActivity.this, "updated") + " "
                        + android.text.format.DateFormat.getTimeFormat(MainActivity.this)
                        .format(new Date(result.fetchedAt)));
            }
        });
    }

    private void showWeatherError(final Exception error, int requestRevision) {
        weatherFetchInProgress = false;
        final int completedRevision = requestRevision;
        final long failedAt = SystemClock.elapsedRealtime();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (destroyed) {
                    return;
                }
                if (completedRevision != weatherLocationRevision) {
                    if (resumed) {
                        refreshWeather(true);
                    }
                    return;
                }
                lastWeatherFailureElapsed = failedAt;
                weatherNeedsRetry = true;
                if (!resumed) {
                    return;
                }
                handler.removeCallbacks(weatherRefresh);
                handler.removeCallbacks(weatherRetry);
                handler.postDelayed(weatherRetry, remainingDelay(
                        SystemClock.elapsedRealtime(), lastWeatherFailureElapsed,
                        NETWORK_RETRY_MS));
                dashboardView.setStatus(AppText.get(MainActivity.this,
                    error instanceof WeatherClient.AuthenticationException ? "foreca_key_rejected"
                        : error instanceof IllegalArgumentException ? "invalid_coordinates"
                        : hasWeather ? "offline_cached" : "offline_retry"));
            }
        });
    }

    private void loadPhotoForToday() {
        final int width = dashboardView.getWidth();
        final int height = dashboardView.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (destroyed || photoExecutor.isShutdown() || photoLoadInProgress) {
            return;
        }
        photoLoadInProgress = true;
        photoExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final android.graphics.Bitmap bitmap = photoRepository.loadForToday(width, height);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        photoLoadInProgress = false;
                        if (bitmap == null) {
                            return;
                        }
                        if (destroyed || !resumed || dimmed) {
                            bitmap.recycle();
                            return;
                        }
                        dashboardView.setBackgroundBitmap(bitmap);
                    }
                });
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

    private void updateNews(boolean force) {
        if (destroyed || newsExecutor.isShutdown()) {
            return;
        }
        if (newsFetchInProgress) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!force && lastNewsFetchElapsed > 0
                && now - lastNewsFetchElapsed < NEWS_REFRESH_MS) {
            return;
        }
        if (lastNewsFetchElapsed > 0 && now - lastNewsFetchElapsed < 30000L) {
            return;
        }
        lastNewsFetchElapsed = now;
        newsFetchInProgress = true;
        newsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final NewsSnapshot result = NewsClient.fetch();
                    newsFetchInProgress = false;
                    newsNeedsRetry = false;
                    lastNewsFailureElapsed = 0;
                    result.save(getSharedPreferences("news", MODE_PRIVATE));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed || !resumed) {
                                return;
                            }
                            handler.removeCallbacks(newsRefresh);
                            handler.removeCallbacks(newsRetry);
                            handler.removeCallbacks(newsRefresh);
                            handler.postDelayed(newsRefresh, remainingDelay(
                                    SystemClock.elapsedRealtime(), lastNewsFetchElapsed,
                                    NEWS_REFRESH_MS));
                            dashboardView.setNews(result);
                        }
                    });
                } catch (Exception error) {
                    newsFetchInProgress = false;
                    lastNewsFailureElapsed = SystemClock.elapsedRealtime();
                    newsNeedsRetry = true;
                    Log.e("NexusDashboard", "News update failed", error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed || !resumed) {
                                return;
                            }
                            handler.removeCallbacks(newsRetry);
                            handler.postDelayed(newsRetry, remainingDelay(
                                    SystemClock.elapsedRealtime(), lastNewsFailureElapsed,
                                    NETWORK_RETRY_MS));
                        }
                    });
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

    private void showLocationChoice() {
        new AlertDialog.Builder(this)
                .setTitle(AppText.get(this, "location_choice_title"))
                .setMessage(AppText.get(this, "location_choice_message"))
                .setPositiveButton(AppText.get(this, "enter_location"),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                onSettingsRequested();
                            }
                        })
                .setNeutralButton(AppText.get(this, "location_settings"),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            }
                        })
                .setNegativeButton(AppText.get(this, "wait_gps"), null)
                .show();
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
        input.setText(preferences.getString("location_query", ""));
        input.setSelectAllOnFocus(true);
        final EditText apiKey = new EditText(this);
        apiKey.setSingleLine(true);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String apiKeyLabel = AppText.get(this, BuildConfig.FORECA_API_KEY.length() == 0
            ? "foreca_api_key" : "foreca_api_key_override");
        apiKey.setHint(apiKeyLabel);
        apiKey.setContentDescription(apiKeyLabel);
        apiKey.setSaveEnabled(false);
        apiKey.setText(getSharedPreferences("foreca", MODE_PRIVATE).getString("api_key", ""));
        final RadioButton serbian = new RadioButton(this);
        serbian.setId(View.generateViewId());
        serbian.setText("Srpski");
        final RadioButton english = new RadioButton(this);
        english.setId(View.generateViewId());
        english.setText("English");
        final RadioGroup language = new RadioGroup(this);
        language.setOrientation(RadioGroup.HORIZONTAL);
        language.addView(serbian);
        language.addView(english);
        language.check(AppText.isSerbian(this) ? serbian.getId() : english.getId());
        TextView timeoutLabel = new TextView(this);
        timeoutLabel.setText(AppText.get(this, "screen_timeout"));
        final Spinner timeout = new Spinner(this);
        String[] timeoutLabels = new String[IDLE_TIMEOUT_OPTIONS_SECONDS.length];
        for (int i = 0; i < IDLE_TIMEOUT_OPTIONS_SECONDS.length; i++) {
            timeoutLabels[i] = AppText.formatDuration(
                    this, IDLE_TIMEOUT_OPTIONS_SECONDS[i]);
        }
        ArrayAdapter<String> timeoutAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, timeoutLabels);
        timeoutAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        timeout.setAdapter(timeoutAdapter);
        timeout.setSelection(timeoutOptionIndex(preferences.getInt(
                "idle_timeout_seconds", DEFAULT_IDLE_TIMEOUT_SECONDS)));
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
        settings.setPadding(padding, 0, padding, 0);
        settings.addView(input);
        settings.addView(apiKey);
        settings.addView(language);
        settings.addView(timeoutLabel);
        settings.addView(timeout);
        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.addView(settings);
        new AlertDialog.Builder(this)
                .setTitle(AppText.get(this, "settings_title"))
            .setView(settingsScroll)
                .setPositiveButton(AppText.get(this, "save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String query = input.getText().toString().trim();
                        getSharedPreferences("foreca", MODE_PRIVATE).edit()
                            .putString("api_key", apiKey.getText().toString().trim()).apply();
                        preferences.edit()
                                .putString("location_query", query)
                                .putBoolean("serbian",
                                        language.getCheckedRadioButtonId() == serbian.getId())
                                .putInt("idle_timeout_seconds",
                                        IDLE_TIMEOUT_OPTIONS_SECONDS[
                                                timeout.getSelectedItemPosition()])
                                .apply();
                        lastInteractionAt = SystemClock.uptimeMillis();
                        scheduleIdleTimeout();
                        restoreCachedData();
                        dashboardView.invalidate();
                        weatherLocationRevision++;
                        activeWeatherTarget = "";
                        lastWeatherFetchElapsed = 0;
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
                        if (destroyed) {
                            return;
                        }
                        loadPhotoForToday();
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
                if (!destroyed && resumed) {
                    wakeDisplay();
                }
            }
        });
    }

    private void dimDisplay() {
        dimmed = true;
        handler.removeCallbacks(idleCheck);
        dashboardView.setDimmed(true);
        WindowManager.LayoutParams parameters = getWindow().getAttributes();
        parameters.screenBrightness = 0.01f;
        getWindow().setAttributes(parameters);
    }

    private void wakeDisplay() {
        if (destroyed || !resumed) {
            return;
        }
        lastInteractionAt = SystemClock.uptimeMillis();
        if (!dimmed) {
            scheduleIdleTimeout();
            return;
        }
        dimmed = false;
        dashboardView.setDimmed(false);
        WindowManager.LayoutParams parameters = getWindow().getAttributes();
        parameters.screenBrightness = 0.65f;
        getWindow().setAttributes(parameters);
        loadPhotoForToday();
        scheduleIdleTimeout();
        long now = SystemClock.elapsedRealtime();
        if (lastWeatherFetchElapsed == 0
                || now - lastWeatherFetchElapsed >= WAKE_REFRESH_MIN_INTERVAL_MS) {
            refreshWeather(true);
        }
        if (lastNewsFetchElapsed == 0
                || now - lastNewsFetchElapsed >= WAKE_REFRESH_MIN_INTERVAL_MS) {
            updateNews(true);
        }
    }

    private long idleTimeoutMs() {
        int seconds = getSharedPreferences("settings", MODE_PRIVATE).getInt(
                "idle_timeout_seconds", DEFAULT_IDLE_TIMEOUT_SECONDS);
        return seconds == 0 ? IMMEDIATE_IDLE_TIMEOUT_MS : seconds * 1000L;
    }

    private int timeoutOptionIndex(int seconds) {
        for (int i = 0; i < IDLE_TIMEOUT_OPTIONS_SECONDS.length; i++) {
            if (IDLE_TIMEOUT_OPTIONS_SECONDS[i] == seconds) {
                return i;
            }
        }
        return 0;
    }

    private void scheduleIdleTimeout() {
        handler.removeCallbacks(idleCheck);
        long timeout = idleTimeoutMs();
        if (!destroyed && resumed && !dimmed) {
            handler.postDelayed(idleCheck, timeout);
        }
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

    private void restoreCachedData() {
        WeatherSnapshot cachedWeather = WeatherSnapshot.load(
                getSharedPreferences("weather", MODE_PRIVATE));
        if (cachedWeather != null) {
            hasWeather = true;
            dashboardView.setWeather(cachedWeather);
            dashboardView.setStatus(AppText.get(this, "last_updated") + " "
                    + android.text.format.DateFormat.getTimeFormat(this)
                    .format(new Date(cachedWeather.fetchedAt)));
        }
        NewsSnapshot cachedNews = NewsSnapshot.load(getSharedPreferences("news", MODE_PRIVATE));
        if (cachedNews != null) {
            dashboardView.setNews(cachedNews);
        }
    }

    private void resumeDataRefreshes() {
        long now = SystemClock.elapsedRealtime();
        handler.removeCallbacks(weatherRefresh);
        handler.removeCallbacks(weatherRetry);
        if (weatherNeedsRetry) {
            handler.postDelayed(weatherRetry,
                    remainingDelay(now, lastWeatherFailureElapsed, NETWORK_RETRY_MS));
        } else {
            handler.postDelayed(weatherRefresh,
                    remainingDelay(now, lastWeatherFetchElapsed, WEATHER_REFRESH_MS));
        }

        handler.removeCallbacks(newsRefresh);
        handler.removeCallbacks(newsRetry);
        if (newsNeedsRetry) {
            handler.postDelayed(newsRetry,
                    remainingDelay(now, lastNewsFailureElapsed, NETWORK_RETRY_MS));
        } else if (lastNewsFetchElapsed == 0) {
            handler.post(newsRefresh);
        } else {
            handler.postDelayed(newsRefresh,
                    remainingDelay(now, lastNewsFetchElapsed, NEWS_REFRESH_MS));
        }
    }

    private static long remainingDelay(long now, long lastAttempt, long interval) {
        if (lastAttempt == 0) {
            return 0;
        }
        return Math.max(0, interval - (now - lastAttempt));
    }

    private void scheduleWeatherAfterSkippedAttempt() {
        if (destroyed || !resumed) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long delay = weatherNeedsRetry
                ? remainingDelay(now, lastWeatherFailureElapsed, NETWORK_RETRY_MS)
                : remainingDelay(now, lastWeatherFetchElapsed, WEATHER_REFRESH_MS);
        handler.postDelayed(weatherNeedsRetry ? weatherRetry : weatherRefresh,
                delay == 0 ? NETWORK_RETRY_MS : delay);
    }

    private void scheduleNewsAfterSkippedAttempt() {
        if (destroyed || !resumed) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long delay = newsNeedsRetry
                ? remainingDelay(now, lastNewsFailureElapsed, NETWORK_RETRY_MS)
                : remainingDelay(now, lastNewsFetchElapsed, NEWS_REFRESH_MS);
        handler.postDelayed(newsNeedsRetry ? newsRetry : newsRefresh,
                delay == 0 ? NETWORK_RETRY_MS : delay);
    }

}
