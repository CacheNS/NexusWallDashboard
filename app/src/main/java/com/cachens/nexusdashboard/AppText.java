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

    static String get(Context context, String key) {
        boolean sr = isSerbian(context);
        if ("weather".equals(key)) return sr ? "Vreme" : "Weather";
        if ("waiting_data".equals(key)) return sr ? "Čekam podatke" : "Waiting for data";
        if ("waiting_location".equals(key)) return sr ? "Čekam lokaciju..." : "Waiting for location...";
        if ("finding_location".equals(key)) return sr ? "Tražim trenutnu lokaciju..." : "Finding current location...";
        if ("location_required".equals(key)) return sr ? "Potrebna je dozvola za lokaciju" : "Location permission is required";
        if ("enable_location".equals(key)) return sr ? "Uključite lokaciju ili unesite grad u podešavanjima" : "Enable Location or enter a city in settings";
        if ("location_choice_title".equals(key)) return sr ? "Izaberite lokaciju" : "Choose location";
        if ("location_choice_message".equals(key)) return sr ? "GPS još nema lokaciju. Sačekajte GPS, otvorite podešavanja lokacije ili unesite grad." : "GPS does not have a location yet. Wait for GPS, open Location settings, or enter a city.";
        if ("enter_location".equals(key)) return sr ? "Unesi lokaciju" : "Enter location";
        if ("location_settings".equals(key)) return sr ? "GPS podešavanja" : "Location settings";
        if ("wait_gps".equals(key)) return sr ? "Sačekaj GPS" : "Wait for GPS";
        if ("updating".equals(key)) return sr ? "Ažuriram vreme i kvalitet vazduha..." : "Updating weather and air quality...";
        if ("updated".equals(key)) return sr ? "Ažurirano" : "Updated";
        if ("last_updated".equals(key)) return sr ? "Poslednje ažuriranje" : "Last updated";
        if ("update_failed".equals(key)) return sr ? "Ažuriranje nije uspelo" : "Update failed";
        if ("offline_cached".equals(key)) return sr ? "Nema mreže - prikazujem sačuvane podatke; pokušavam ponovo" : "Offline - showing cached data; retrying";
        if ("offline_retry".equals(key)) return sr ? "Nema mreže - pokušavam ponovo" : "Offline - retrying";
        if ("feels".equals(key)) return sr ? "Osećaj" : "Feels";
        if ("foreca_api_key".equals(key)) return sr ? "Foreca API ključ" : "Foreca API key";
        if ("foreca_api_key_override".equals(key)) return sr ? "Zamenski Foreca API ključ" : "Foreca API key override";
        if ("foreca_key_required".equals(key)) return sr ? "Unesite Foreca API ključ u podešavanjima" : "Enter Foreca API key in settings";
        if ("foreca_key_rejected".equals(key)) return sr ? "Proverite Foreca API ključ i pristup nalogu" : "Check Foreca API key and account access";
        if ("estimate".equals(key)) return sr ? "Procena" : "Estimate";
        if ("invalid_coordinates".equals(key)) return sr ? "Neispravne koordinate lokacije" : "Invalid location coordinates";
        if ("humidity".equals(key)) return sr ? "Vlažnost" : "Humidity";
        if ("wind".equals(key)) return sr ? "Vetar" : "Wind";
        if ("rain".equals(key)) return sr ? "Padavine" : "Rain";
        if ("today".equals(key)) return sr ? "Danas" : "Today";
        if ("settings".equals(key)) return sr ? "PODEŠAVANJA" : "SETTINGS";
        if ("settings_title".equals(key)) return sr ? "Podešavanja" : "Settings";
        if ("settings_hint".equals(key)) return sr ? "Grad ili širina, dužina; prazno = GPS" : "City or latitude, longitude; blank = GPS";
        if ("language".equals(key)) return sr ? "Jezik" : "Language";
        if ("screen_timeout".equals(key)) return sr ? "Zatamni ekran posle" : "Dim screen after";
        if ("save".equals(key)) return sr ? "Sačuvaj" : "Save";
        if ("cancel".equals(key)) return sr ? "Otkaži" : "Cancel";
        if ("photos".equals(key)) return sr ? "IZABERI FOTOGRAFIJE" : "SELECT PHOTOS";
        if ("area_average".equals(key)) return sr ? "Prosek područja" : "Area average";
        return key;
    }

    static String formatDuration(Context context, int seconds) {
        boolean sr = isSerbian(context);
        if (seconds == 0) {
            return sr ? "0 - Odmah" : "0 - Immediately";
        }
        if (seconds < 60) {
            if (seconds == 1) {
                return sr ? "1 sekunda" : "1 second";
            }
            return seconds + (sr ? " sekundi" : " seconds");
        }
        int minutes = seconds / 60;
        if (sr) {
            return minutes == 1 ? "1 minut" : minutes + " minuta";
        }
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    static String condition(Context context, int code) {
        boolean sr = isSerbian(context);
        if (code == 0) return sr ? "Vedro" : "Clear";
        if (code == 1 || code == 2) return sr ? "Delimično oblačno" : "Partly cloudy";
        if (code == 3) return sr ? "Oblačno" : "Overcast";
        if (code == 45 || code == 48) return sr ? "Magla" : "Fog";
        if (code >= 51 && code <= 57) return sr ? "Rosulja" : "Drizzle";
        if (code >= 61 && code <= 67) return sr ? "Kiša" : "Rain";
        if (code == 69) return sr ? "Susnežica" : "Sleet";
        if (code >= 71 && code <= 77) return sr ? "Sneg" : "Snow";
        if (code >= 80 && code <= 82) return sr ? "Pljuskovi" : "Showers";
        if (code >= 85 && code <= 86) return sr ? "Snežni pljuskovi" : "Snow showers";
        if (code >= 95) return sr ? "Grmljavina" : "Thunderstorm";
        return sr ? "Trenutno vreme" : "Current conditions";
    }

    static String aqiLabel(Context context, double value) {
        boolean sr = isSerbian(context);
        switch (AirCareAqi.category(value)) {
            case 0: return sr ? "Dobro" : "Good";
            case 1: return sr ? "Umereno" : "Moderate";
            case 2: return sr ? "Loše" : "Poor";
            case 3: return sr ? "Vrlo loše" : "Bad";
            case 4: return sr ? "Opasno" : "Hazardous";
            default: return sr ? "Nedostupno" : "Unavailable";
        }
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
