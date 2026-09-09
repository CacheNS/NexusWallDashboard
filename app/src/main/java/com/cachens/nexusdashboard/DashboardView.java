package com.cachens.nexusdashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class DashboardView extends View {
    private static final long CLOCK_REFRESH_MS = 60L * 1000L;
    private static final long NEWS_REFRESH_MS = 10L * 1000L;

    interface Listener {
        void onSettingsRequested();
        void onUserInteraction();
    }

    private final Context context;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF bitmapRect = new RectF();
    private final RectF newsRect = new RectF();
    private final Path iconPath = new Path();
    private final Date currentDate = new Date();
    private final Calendar currentCalendar = Calendar.getInstance();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private LinearGradient backgroundGradient;
    private int gradientWidth;
    private int gradientHeight;
    private boolean gradientDay;
    private WeatherSnapshot weather;
    private NewsSnapshot news;
    private Bitmap backgroundBitmap;
    private Listener listener;
    private String status;
    private boolean dimmed;
    private boolean active;

    private final Runnable displayRefresh = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    DashboardView(Context context) {
        super(context);
        this.context = context;
        status = AppText.get(context, "waiting_location");
        cardPaint.setColor(Color.argb(105, 0, 0, 0));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setListener(Listener value) {
        listener = value;
    }

    void setWeather(WeatherSnapshot value) {
        weather = value;
        invalidate();
    }

    void setNews(NewsSnapshot value) {
        news = value;
        invalidate();
    }

    void setStatus(String value) {
        status = value;
        invalidate();
    }

    void setBackgroundBitmap(Bitmap value) {
        Bitmap previous = backgroundBitmap;
        backgroundBitmap = value;
        if (previous != null && previous != value && !previous.isRecycled()) {
            previous.recycle();
        }
        invalidate();
    }

    void setDimmed(boolean value) {
        dimmed = value;
        removeCallbacks(displayRefresh);
        invalidate();
    }

    void setActive(boolean value) {
        active = value;
        removeCallbacks(displayRefresh);
        if (active) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        boolean day = weather == null || weather.isDay == 1;
        ensureGradient(width, height, day);
        paint.setShader(backgroundGradient);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        drawBackgroundPhoto(canvas, width, height);

        float margin = dp(32);
        float centerY = height * 0.39f;
        long now = System.currentTimeMillis();
        currentDate.setTime(now);
        currentCalendar.setTimeInMillis(now);
        drawText(canvas, clockFormat.format(currentDate), margin, centerY, sp(100), Color.WHITE, Paint.Align.LEFT, true);
        drawText(canvas, AppText.formatDate(context, currentCalendar), margin + dp(6), centerY + dp(60), sp(27),
                Color.argb(235, 255, 255, 255), Paint.Align.LEFT, false);

        float cardLeft = width * 0.57f;
        float cardTop = dp(38);
        float cardRight = width - margin;
        float cardBottom = height - dp(175);
        cardRect.set(cardLeft, cardTop, cardRight, cardBottom);
        canvas.drawRoundRect(cardRect, dp(18), dp(18), cardPaint);

        if (weather == null) {
            drawText(canvas, AppText.get(context, "weather"), cardLeft + dp(24), cardTop + dp(55), sp(28),
                    Color.WHITE, Paint.Align.LEFT, true);
            drawText(canvas, AppText.get(context, "waiting_data"), cardLeft + dp(24), cardTop + dp(105), sp(22),
                    Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
        } else {
            drawWeather(canvas, cardLeft, cardTop, cardRight);
            drawForecast(canvas, width, height);
        }

        drawNewsTicker(canvas, width, height);
        String settings = AppText.get(context, "settings");
        paint.setTextSize(sp(11));
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float settingsWidth = paint.measureText(settings);
        float headerRight = width - dp(16);
        float headerY = dp(24);
        drawText(canvas, settings, headerRight, headerY, sp(11),
                Color.argb(230, 255, 255, 255), Paint.Align.RIGHT, true);
        paint.setTextSize(sp(11));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        String headerStatus = ellipsize(status,
                Math.min(width * 0.48f, headerRight - settingsWidth - dp(36)));
        drawText(canvas, headerStatus, headerRight - settingsWidth - dp(18), headerY, sp(11),
                Color.argb(185, 255, 255, 255), Paint.Align.RIGHT, false);

        if (dimmed) {
            paint.setColor(Color.BLACK);
            canvas.drawRect(0, 0, width, height, paint);
        }
        scheduleDisplayRefresh();
    }

    private void scheduleDisplayRefresh() {
        removeCallbacks(displayRefresh);
        if (!active || dimmed) {
            return;
        }
        long interval = news == null || news.items.isEmpty()
                ? CLOCK_REFRESH_MS : NEWS_REFRESH_MS;
        long now = System.currentTimeMillis();
        postDelayed(displayRefresh, interval - now % interval);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(displayRefresh);
        super.onDetachedFromWindow();
    }

    private void drawBackgroundPhoto(Canvas canvas, int width, int height) {
        if (backgroundBitmap == null || backgroundBitmap.isRecycled()) {
            return;
        }
        float scale = Math.max((float) width / backgroundBitmap.getWidth(),
                (float) height / backgroundBitmap.getHeight());
        float drawnWidth = backgroundBitmap.getWidth() * scale;
        float drawnHeight = backgroundBitmap.getHeight() * scale;
        float left = (width - drawnWidth) / 2f;
        float top = (height - drawnHeight) / 2f;
        paint.setAlpha(220);
        bitmapRect.set(left, top, left + drawnWidth, top + drawnHeight);
        canvas.drawBitmap(backgroundBitmap, null, bitmapRect, paint);
        paint.setAlpha(255);
        paint.setColor(Color.argb(105, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void drawWeather(Canvas canvas, float left, float top, float right) {
        float x = left + dp(24);
        paint.setTextSize(sp(13));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        drawText(canvas, ellipsize(safe(weather.locationName), right - x - dp(24)), x, top + dp(24), sp(13),
                Color.argb(215, 255, 255, 255), Paint.Align.LEFT, false);
        drawText(canvas, AppText.condition(context, weather.weatherCode), x, top + dp(51), sp(22),
                Color.WHITE, Paint.Align.LEFT, true);
        drawText(canvas, number(weather.temperature) + " C", x, top + dp(118), sp(54),
                Color.WHITE, Paint.Align.LEFT, true);
        drawText(canvas, AppText.get(context, "feels") + " " + number(weather.apparentTemperature) + " C",
                x, top + dp(151), sp(17), Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);

        String weatherSource = "Foreca - " + (safe(weather.weatherStation).length() == 0
            ? AppText.get(context, "estimate") : weather.weatherStation
            + String.format(java.util.Locale.US, " (%.1f km)", weather.weatherStationDistanceKm));
        String weatherTime = "";
        if (weather.weatherTime > 0) {
            weatherTime = android.text.format.DateFormat.getDateFormat(context)
                .format(new java.util.Date(weather.weatherTime)) + " "
                + android.text.format.DateFormat.getTimeFormat(context)
                .format(new java.util.Date(weather.weatherTime));
        }
        paint.setTextSize(sp(10));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        float timeWidth = paint.measureText(weatherTime);
        drawText(canvas, ellipsize(weatherSource, right - x - dp(32) - timeWidth), x, top + dp(174), sp(10),
            Color.argb(215, 255, 255, 255), Paint.Align.LEFT, false);
        drawText(canvas, weatherTime, right - dp(24), top + dp(174), sp(10),
            Color.argb(215, 255, 255, 255), Paint.Align.RIGHT, false);

        float row = top + dp(198);
        drawText(canvas, AppText.get(context, "humidity") + "  " + number(weather.humidity) + "%",
                x, row, sp(16), Color.WHITE, Paint.Align.LEFT, false);
        drawText(canvas, AppText.get(context, "wind") + "  " + number(weather.windSpeed) + " km/h",
                x, row + dp(29), sp(16), Color.WHITE, Paint.Align.LEFT, false);
        drawText(canvas, AppText.get(context, "rain") + "  " + number(weather.precipitation) + " mm/h",
                x, row + dp(58), sp(16), Color.WHITE, Paint.Align.LEFT, false);

        float badgeX = right - dp(82);
        float badgeY = top + dp(72);
        paint.setColor(AirCareAqi.color(weather.aqi));
        canvas.drawCircle(badgeX, badgeY, dp(48), paint);
        drawText(canvas, "EU AQI", badgeX, badgeY - dp(4), sp(15), Color.WHITE, Paint.Align.CENTER, true);
        drawText(canvas, number(weather.aqi), badgeX, badgeY + dp(24), sp(23), Color.WHITE, Paint.Align.CENTER, true);
        drawText(canvas, AppText.aqiLabel(context, weather.aqi), badgeX, badgeY + dp(81), sp(14),
                Color.WHITE, Paint.Align.CENTER, true);

        drawText(canvas, "PM2.5  " + number(weather.pm25), right - dp(150), top + dp(210), sp(14),
                Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
        drawText(canvas, "PM10   " + number(weather.pm10), right - dp(150), top + dp(237), sp(14),
                Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
        drawText(canvas, "NO2    " + number(weather.nitrogenDioxide), right - dp(150), top + dp(264), sp(14),
                Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
        String source = aqiSourceLabel();
        if (source.length() > 0) {
            paint.setTextSize(sp(10));
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            source = ellipsize(source, dp(205));
            drawText(canvas, source, right - dp(205), top + dp(289), sp(10),
                    Color.argb(205, 255, 255, 255), Paint.Align.LEFT, false);
        }
    }

    private void drawForecast(Canvas canvas, int width, int height) {
        float left = dp(35);
        float top = height - dp(145);
        float available = width - dp(70);
        float column = available / WeatherSnapshot.FORECAST_DAYS;
        for (int i = 0; i < WeatherSnapshot.FORECAST_DAYS; i++) {
            float x = left + column * i + column / 2f;
            drawText(canvas, formatDay(weather.dailyDate[i], i), x, top, sp(16), Color.WHITE, Paint.Align.CENTER, true);
            String condition = AppText.condition(context, weather.dailyCode[i]);
            paint.setTextSize(sp(14));
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            float iconSize = dp(18);
            float gap = dp(7);
            float totalWidth = iconSize + gap + paint.measureText(condition);
            float start = x - totalWidth / 2f;
            drawWeatherIcon(canvas, weather.dailyCode[i], start + iconSize / 2f, top + dp(23), iconSize);
            drawText(canvas, condition, start + iconSize + gap, top + dp(28), sp(14),
                    Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
            drawText(canvas, number(weather.dailyHigh[i]) + " / " + number(weather.dailyLow[i]) + " C",
                    x, top + dp(53), sp(15), Color.WHITE, Paint.Align.CENTER, false);
        }
    }

    private void drawWeatherIcon(Canvas canvas, int code, float x, float y, float size) {
        paint.setShader(null);
        paint.setStrokeWidth(Math.max(dp(1.4f), size * 0.09f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);

        if (code == 0) {
            drawSun(canvas, x, y, size);
        } else if (code == 1 || code == 2) {
            drawSun(canvas, x - size * 0.18f, y - size * 0.15f, size * 0.65f);
            drawCloud(canvas, x + size * 0.08f, y + size * 0.08f, size * 0.8f);
        } else if (code == 3) {
            drawCloud(canvas, x, y, size);
        } else if (code == 45 || code == 48) {
            for (int i = -1; i <= 1; i++) {
                canvas.drawLine(x - size * 0.45f, y + i * size * 0.24f,
                        x + size * 0.45f, y + i * size * 0.24f, paint);
            }
        } else if ((code >= 51 && code <= 69) || (code >= 80 && code <= 82)) {
            drawCloud(canvas, x, y - size * 0.12f, size * 0.82f);
            for (int i = -1; i <= 1; i++) {
                float dropX = x + i * size * 0.28f;
                canvas.drawLine(dropX, y + size * 0.22f, dropX - size * 0.08f,
                        y + size * 0.48f, paint);
            }
        } else if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
            drawCloud(canvas, x, y - size * 0.12f, size * 0.82f);
            for (int i = -1; i <= 1; i++) {
                float snowX = x + i * size * 0.28f;
                float snowY = y + size * 0.37f;
                canvas.drawLine(snowX - size * 0.08f, snowY, snowX + size * 0.08f, snowY, paint);
                canvas.drawLine(snowX, snowY - size * 0.08f, snowX, snowY + size * 0.08f, paint);
            }
        } else if (code >= 95) {
            drawCloud(canvas, x, y - size * 0.12f, size * 0.82f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 214, 87));
            iconPath.reset();
            iconPath.moveTo(x + size * 0.05f, y + size * 0.12f);
            iconPath.lineTo(x - size * 0.14f, y + size * 0.42f);
            iconPath.lineTo(x + size * 0.02f, y + size * 0.40f);
            iconPath.lineTo(x - size * 0.10f, y + size * 0.62f);
            iconPath.lineTo(x + size * 0.25f, y + size * 0.30f);
            iconPath.lineTo(x + size * 0.08f, y + size * 0.32f);
            iconPath.close();
            canvas.drawPath(iconPath, paint);
        } else {
            drawCloud(canvas, x, y, size);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSun(Canvas canvas, float x, float y, float size) {
        paint.setColor(Color.rgb(255, 214, 87));
        paint.setStyle(Paint.Style.STROKE);
        float radius = size * 0.22f;
        canvas.drawCircle(x, y, radius, paint);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4.0;
            float inner = size * 0.34f;
            float outer = size * 0.48f;
            canvas.drawLine(
                    x + (float) Math.cos(angle) * inner,
                    y + (float) Math.sin(angle) * inner,
                    x + (float) Math.cos(angle) * outer,
                    y + (float) Math.sin(angle) * outer,
                    paint);
        }
    }

    private void drawCloud(Canvas canvas, float x, float y, float size) {
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        iconPath.reset();
        iconPath.moveTo(x - size * 0.42f, y + size * 0.20f);
        iconPath.cubicTo(x - size * 0.55f, y - size * 0.02f,
                x - size * 0.30f, y - size * 0.18f,
                x - size * 0.14f, y - size * 0.10f);
        iconPath.cubicTo(x - size * 0.02f, y - size * 0.42f,
                x + size * 0.38f, y - size * 0.30f,
                x + size * 0.34f, y - size * 0.04f);
        iconPath.cubicTo(x + size * 0.58f, y - size * 0.02f,
                x + size * 0.58f, y + size * 0.22f,
                x + size * 0.36f, y + size * 0.24f);
        iconPath.lineTo(x - size * 0.35f, y + size * 0.24f);
        canvas.drawPath(iconPath, paint);
    }

    private void drawNewsTicker(Canvas canvas, int width, int height) {
        if (news == null || news.items.isEmpty()) {
            return;
        }
        int index = (int) ((System.currentTimeMillis() / 10000L) % news.items.size());
        NewsItem item = news.items.get(index);
        float left = 0;
        float right = width;
        float top = height - dp(48);
        float bottom = height;
        paint.setColor(Color.argb(125, 0, 0, 0));
        newsRect.set(left, top, right, bottom);
        canvas.drawRect(newsRect, paint);
        float sourceWidth = dp(84);
        drawText(canvas, item.source, left + dp(14), bottom - dp(13), sp(18),
                Color.rgb(255, 214, 87), Paint.Align.LEFT, true);
        paint.setTextSize(sp(19));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        String title = ellipsize(item.title, right - left - sourceWidth - dp(22));
        drawText(canvas, title, left + sourceWidth, bottom - dp(13), sp(19),
                Color.WHITE, Paint.Align.LEFT, false);
    }

    private String ellipsize(String value, float maxWidth) {
        if (paint.measureText(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int low = 0;
        int high = value.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (paint.measureText(value.substring(0, middle) + suffix) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return value.substring(0, low).trim() + suffix;
    }

    private void drawText(Canvas canvas, String text, float x, float y, float size, int color,
                          Paint.Align align, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        canvas.drawText(text, x, y, paint);
    }

    private String formatDay(String value, int index) {
        if (index == 0) {
            return AppText.get(context, "today");
        }
        try {
            Date date = apiDateFormat.parse(value);
            if (date == null) {
                return value;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return AppText.shortDay(context, calendar);
        } catch (ParseException ignored) {
            return value;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String number(double value) {
        return Double.isNaN(value) ? "--" : String.format(Locale.getDefault(), "%.0f", value);
    }

    String aqiSourceLabel() {
        if (weather.aqiAreaAverage) return AppText.get(context, "area_average");
        return safe(weather.aqiSource);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private void ensureGradient(int width, int height, boolean day) {
        if (backgroundGradient != null && gradientWidth == width && gradientHeight == height
                && gradientDay == day) {
            return;
        }
        gradientWidth = width;
        gradientHeight = height;
        gradientDay = day;
        int topColor = day ? Color.rgb(28, 102, 164) : Color.rgb(10, 18, 48);
        int bottomColor = day ? Color.rgb(53, 166, 181) : Color.rgb(40, 45, 84);
        backgroundGradient = new LinearGradient(0, 0, width, height, topColor, bottomColor,
                Shader.TileMode.CLAMP);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        if (listener != null) {
            listener.onUserInteraction();
        }
        performClick();
        if (dimmed) {
            return true;
        }
        if (event.getY() < dp(52) && event.getX() > getWidth() - dp(220)
                && listener != null) {
            listener.onSettingsRequested();
        }
        return true;
    }
}
