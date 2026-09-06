package com.cachens.nexusdashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
        invalidate();
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
        drawText(canvas, status, margin, height - dp(15), sp(11), Color.argb(185, 255, 255, 255),
                Paint.Align.LEFT, false);
        drawText(canvas, AppText.get(context, "settings"), width - dp(105), height - dp(15), sp(11),
                Color.argb(230, 255, 255, 255), Paint.Align.CENTER, true);
        drawText(canvas, AppText.isSerbian(context) ? "SR | EN" : "EN | SR", width - dp(105), dp(27), sp(12),
                Color.argb(230, 255, 255, 255), Paint.Align.CENTER, true);

        if (dimmed) {
            paint.setColor(Color.BLACK);
            canvas.drawRect(0, 0, width, height, paint);
        }
        postInvalidateDelayed(1000);
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
        drawText(canvas, safe(weather.locationName), x, top + dp(24), sp(13),
                Color.argb(215, 255, 255, 255), Paint.Align.LEFT, false);
        drawText(canvas, AppText.condition(context, weather.weatherCode), x, top + dp(51), sp(22),
                Color.WHITE, Paint.Align.LEFT, true);
        drawText(canvas, number(weather.temperature) + " C", x, top + dp(118), sp(54),
                Color.WHITE, Paint.Align.LEFT, true);
        drawText(canvas, AppText.get(context, "feels") + " " + number(weather.apparentTemperature) + " C",
                x, top + dp(151), sp(17), Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);

        float row = top + dp(198);
        drawText(canvas, AppText.get(context, "humidity") + "  " + number(weather.humidity) + "%",
                x, row, sp(16), Color.WHITE, Paint.Align.LEFT, false);
        drawText(canvas, AppText.get(context, "wind") + "  " + number(weather.windSpeed) + " km/h",
                x, row + dp(29), sp(16), Color.WHITE, Paint.Align.LEFT, false);
        drawText(canvas, AppText.get(context, "rain") + "  " + number(weather.precipitation) + " mm",
                x, row + dp(58), sp(16), Color.WHITE, Paint.Align.LEFT, false);

        float badgeX = right - dp(82);
        float badgeY = top + dp(72);
        paint.setColor(aqiColor(weather.aqi));
        canvas.drawCircle(badgeX, badgeY, dp(48), paint);
        drawText(canvas, "AQI", badgeX, badgeY - dp(4), sp(15), Color.WHITE, Paint.Align.CENTER, true);
        drawText(canvas, number(weather.aqi), badgeX, badgeY + dp(24), sp(23), Color.WHITE, Paint.Align.CENTER, true);
        drawText(canvas, AppText.aqiLabel(context, weather.aqi), badgeX, badgeY + dp(81), sp(14),
                Color.WHITE, Paint.Align.CENTER, true);

        drawText(canvas, "PM2.5  " + number(weather.pm25), right - dp(150), top + dp(210), sp(14),
                Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
        drawText(canvas, "PM10   " + number(weather.pm10), right - dp(150), top + dp(237), sp(14),
                Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
        drawText(canvas, "NO2    " + number(weather.nitrogenDioxide), right - dp(150), top + dp(264), sp(14),
                Color.argb(225, 255, 255, 255), Paint.Align.LEFT, false);
    }

    private void drawForecast(Canvas canvas, int width, int height) {
        float left = dp(35);
        float top = height - dp(145);
        float available = width - dp(70);
        float column = available / 3f;
        for (int i = 0; i < 3; i++) {
            float x = left + column * i + column / 2f;
            drawText(canvas, formatDay(weather.dailyDate[i], i), x, top, sp(16), Color.WHITE, Paint.Align.CENTER, true);
            drawText(canvas, AppText.condition(context, weather.dailyCode[i]), x, top + dp(25), sp(14),
                    Color.argb(225, 255, 255, 255), Paint.Align.CENTER, false);
            drawText(canvas, number(weather.dailyHigh[i]) + " / " + number(weather.dailyLow[i]) + " C",
                    x, top + dp(49), sp(15), Color.WHITE, Paint.Align.CENTER, false);
        }
    }

    private void drawNewsTicker(Canvas canvas, int width, int height) {
        if (news == null || news.items.isEmpty()) {
            return;
        }
        int index = (int) ((System.currentTimeMillis() / 10000L) % news.items.size());
        NewsItem item = news.items.get(index);
        float left = dp(32);
        float right = width - dp(32);
        float top = height - dp(72);
        float bottom = height - dp(36);
        paint.setColor(Color.argb(125, 0, 0, 0));
        newsRect.set(left, top, right, bottom);
        canvas.drawRoundRect(newsRect, dp(10), dp(10), paint);
        float sourceWidth = dp(72);
        drawText(canvas, item.source, left + dp(14), bottom - dp(11), sp(14),
                Color.rgb(255, 214, 87), Paint.Align.LEFT, true);
        paint.setTextSize(sp(15));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        String title = ellipsize(item.title, right - left - sourceWidth - dp(22));
        drawText(canvas, title, left + sourceWidth, bottom - dp(11), sp(15),
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

    private static int aqiColor(double value) {
        if (Double.isNaN(value)) return Color.rgb(100, 110, 120);
        if (value < 20) return Color.rgb(42, 160, 92);
        if (value < 40) return Color.rgb(85, 172, 72);
        if (value < 60) return Color.rgb(226, 165, 48);
        if (value < 80) return Color.rgb(224, 105, 48);
        if (value < 100) return Color.rgb(194, 58, 68);
        return Color.rgb(125, 48, 96);
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
        if (event.getY() < dp(60) && event.getX() > getWidth() - dp(220)) {
            AppText.toggleLanguage(context);
            ((android.app.Activity) context).recreate();
            return true;
        }
        if (event.getY() > getHeight() - dp(75) && event.getX() > getWidth() - dp(220)
                && listener != null) {
            listener.onSettingsRequested();
        }
        return true;
    }
}
