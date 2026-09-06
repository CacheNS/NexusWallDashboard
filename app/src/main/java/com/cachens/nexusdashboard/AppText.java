package com.cachens.nexusdashboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

final class AppText {
    private static final String[] DAYS_EN = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };
    private static final String[] DAYS_SR = {
            "Nedelja", "Ponedeljak", "Utorak", "Sreda", "Četvrtak", "Petak", "Subota"
    };
    private static final String[] DAYS_SHORT_EN = {
            "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
    };
    private static final String[] DAYS_SHORT_SR = {
            "Ned", "Pon", "Uto", "Sre", "Čet", "Pet", "Sub"
    };
    private static final String[] MONTHS_EN = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };
    private static final String[] MONTHS_SR = {
            "januar", "februar", "mart", "april", "maj", "jun",
            "jul", "avgust", "septembar", "oktobar", "novembar", "decembar"
    };

    private AppText() {
    }

    static boolean isSerbian(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        return preferences.getBoolean("serbian", true);
    }

    static void toggleLanguage(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        preferences.edit().putBoolean("serbian", !isSerbian(context)).apply();
    }

    static String get(Context context, String key) {
        boolean sr = isSerbian(context);
        if ("weather".equals(key)) return sr ? "Vreme" : "Weather";
        if ("waiting_data".equals(key)) return sr ? "Čekam podatke" : "Waiting for data";
        if ("waiting_location".equals(key)) return sr ? "Čekam lokaciju..." : "Waiting for location...";
        if ("finding_location".equals(key)) return sr ? "Tražim trenutnu lokaciju..." : "Finding current location...";
        if ("location_required".equals(key)) return sr ? "Potrebna je dozvola za lokaciju" : "Location permission is required";
        if ("enable_location".equals(key)) return sr ? "Uključite lokaciju ili unesite grad u podešavanjima" : "Enable Location or enter a city in settings";
        if ("updating".equals(key)) return sr ? "Ažuriram vreme i kvalitet vazduha..." : "Updating weather and air quality...";
        if ("updated".equals(key)) return sr ? "Ažurirano" : "Updated";
        if ("last_updated".equals(key)) return sr ? "Poslednje ažuriranje" : "Last updated";
        if ("update_failed".equals(key)) return sr ? "Ažuriranje nije uspelo" : "Update failed";
        if ("offline_cached".equals(key)) return sr ? "Nema mreže - prikazujem sačuvane podatke; pokušavam ponovo" : "Offline - showing cached data; retrying";
        if ("offline_retry".equals(key)) return sr ? "Nema mreže - pokušavam ponovo" : "Offline - retrying";
        if ("feels".equals(key)) return sr ? "Osećaj" : "Feels";
        if ("humidity".equals(key)) return sr ? "Vlažnost" : "Humidity";
        if ("wind".equals(key)) return sr ? "Vetar" : "Wind";
        if ("rain".equals(key)) return sr ? "Padavine" : "Rain";
        if ("today".equals(key)) return sr ? "Danas" : "Today";
        if ("settings".equals(key)) return sr ? "PODEŠAVANJA" : "SETTINGS";
        if ("settings_title".equals(key)) return sr ? "Lokacija" : "Location";
        if ("settings_hint".equals(key)) return sr ? "Grad ili poštanski broj; prazno = GPS" : "City or postal code; blank = GPS";
        if ("save".equals(key)) return sr ? "Sačuvaj" : "Save";
        if ("cancel".equals(key)) return sr ? "Otkaži" : "Cancel";
        if ("photos".equals(key)) return sr ? "IZABERI FOTOGRAFIJE" : "SELECT PHOTOS";
        if ("local_pm".equals(key)) return sr ? "PM lokalni senzor Telep" : "PM local Telep sensor";
        if ("local_pm_generic".equals(key)) return sr ? "PM lokalni senzor" : "PM local sensor";
        if ("sepa".equals(key)) return "SEPA";
        return key;
    }

    static String condition(Context context, int code) {
        boolean sr = isSerbian(context);
        if (code == 0) return sr ? "Vedro" : "Clear";
        if (code == 1 || code == 2) return sr ? "Delimično oblačno" : "Partly cloudy";
        if (code == 3) return sr ? "Oblačno" : "Overcast";
        if (code == 45 || code == 48) return sr ? "Magla" : "Fog";
        if (code >= 51 && code <= 57) return sr ? "Rosulja" : "Drizzle";
        if (code >= 61 && code <= 67) return sr ? "Kiša" : "Rain";
        if (code >= 71 && code <= 77) return sr ? "Sneg" : "Snow";
        if (code >= 80 && code <= 82) return sr ? "Pljuskovi" : "Showers";
        if (code >= 85 && code <= 86) return sr ? "Snežni pljuskovi" : "Snow showers";
        if (code >= 95) return sr ? "Grmljavina" : "Thunderstorm";
        return sr ? "Trenutno vreme" : "Current conditions";
    }

    static String aqiLabel(Context context, double value) {
        boolean sr = isSerbian(context);
        if (Double.isNaN(value)) return sr ? "Nedostupno" : "Unavailable";
        if (value < 20) return sr ? "Dobro" : "Good";
        if (value < 40) return sr ? "Prihvatljivo" : "Fair";
        if (value < 60) return sr ? "Umereno" : "Moderate";
        if (value < 80) return sr ? "Loše" : "Poor";
        if (value < 100) return sr ? "Vrlo loše" : "Very poor";
        return sr ? "Izuzetno loše" : "Extremely poor";
    }

    static String formatDate(Context context, Calendar calendar) {
        boolean sr = isSerbian(context);
        String[] days = sr ? DAYS_SR : DAYS_EN;
        String[] months = sr ? MONTHS_SR : MONTHS_EN;
        int dayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        return days[dayIndex] + ", " + calendar.get(Calendar.DAY_OF_MONTH) + ". " + months[calendar.get(Calendar.MONTH)];
    }

    static String shortDay(Context context, Calendar calendar) {
        String[] days = isSerbian(context) ? DAYS_SHORT_SR : DAYS_SHORT_EN;
        return days[calendar.get(Calendar.DAY_OF_WEEK) - 1];
    }
}
