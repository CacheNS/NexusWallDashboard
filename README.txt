Nexus Wall Dashboard
====================

Purpose
-------
A lightweight, landscape Android dashboard for the Nexus 7 (2012). It shows
time, date, weather, a five-day forecast, European AQI, and a rotating
background of photos stored locally on the tablet. It does not access Google
Photos or any Google account data.

Photos
------
The dashboard copies only explicitly selected images into private app storage
and ignores all other tablet photos. User-selected images take priority. If no
user images are selected, the app uses a locally provisioned cache of 100 Novi
Sad photos, then falls back to three images bundled with the APK. Backgrounds
change once per local calendar day. The same photo remains selected across
app restarts and display dim/wake cycles throughout that day.

Run `tools\provision-novi-sad-photos.ps1` from the configured development PC
to validate and transfer the machine-local `.local\novi-sad-cache` collection.
The app imports the staged files atomically into private storage at startup.
The cache survives `adb install -r`, but must be reprovisioned after uninstall
or a signing-key change. See ATTRIBUTIONS.txt and the cache's generated
ATTRIBUTIONS.txt for image credits.

Language and location
---------------------
The app starts in Serbian Latin. Tap "PODEŠAVANJA" / "SETTINGS" at the
top-right to open Settings. Choose either the "Srpski" or "English" radio
button, enter a city or a latitude, longitude pair, and save. For example,
45.25167, 19.83694 uses exact coordinates without a city lookup.
Leave the location field empty
to use Android location services instead; it is empty by default.
Upgrades from builds that supplied an implicit location clear the saved
location and location-derived caches once. Enable location services or enter
a place in Settings again.
When no cached weather or automatic-location fix is available, the app prompts
for a manual place, Android Location Settings, or continued GPS waiting.

Motion wake
-----------
The front camera performs low-resolution, in-memory motion detection. After
the configured period without motion or touch, the dashboard becomes black
and lowers screen brightness to the minimum. The timeout is selected in
Settings and defaults to 3 seconds. Available choices are 0, 1, 3, 5, 10, 15,
30, or 60 seconds. The `0 - Immediately` choice dims as soon as the camera's
next motion-sampling interval confirms that movement has stopped. Motion or a
touch restores normal brightness. Camera frames are never saved or transmitted.
The app requests a 5 FPS preview when the camera supports it and retains only
sampled luminance values between checks to reduce processor and camera load.

The display is dimmed rather than put into hardware sleep because Android
cannot reliably keep camera motion detection running while the app is
suspended.

Power and heat
--------------
The clock redraws on minute boundaries and the news ticker redraws every ten
seconds. Recurring canvas redraws stop while the dashboard is dimmed or in the
background. For a fixed installation, keep the manual location configured so
Android location providers remain off. Use the lowest comfortable display
brightness, keep the wall mount ventilated, and use scheduled charging rather
than holding an aging battery at full charge continuously.

Stock Android 4.4.2 on the Nexus 7 does not expose a charge threshold or a
charging-enable switch. Root access alone therefore cannot limit charging; it
would also require a compatible custom kernel, and unlocking the bootloader
erases the tablet. Do not unlock or root solely for charge limiting. Prefer an
external smart plug or USB power controller that turns charging off near 80%
and restores it near 55%. Keep charging control local and do not store plug or
home-automation credentials in the app.

News
----
The one-line news ticker rotates through the five newest titles from 021.rs
and the five newest titles from N1. Feeds refresh every hour and refresh when
the display wakes if the previous request was at least 10 minutes earlier. The
last successful set is cached for offline use. The full-width ticker sits at
the bottom of the display; update status and Settings are in the top-right.
Update times follow the tablet's configured 12/24-hour format. Separate
Serbian and English radio buttons are available inside Settings.

Requirements
------------
See REQUIREMENTS.md for the maintained product requirements.

Tablet cleanup
--------------
Unrelated third-party apps were uninstalled and clearly nonessential bundled
apps were disabled. `tools\restore-disabled-apps.ps1` re-enables the bundled
apps disabled during setup.

Data source
-----------
Foreca provides weather and five-day forecasts. Create a Foreca API key at
https://developer.foreca.com/my-api and enter it in the masked Foreca API key
field in Settings. The personal, non-commercial Freemium plan is described at
https://business.foreca.com/weather-api/pricing. Do not put keys in tracked files.
The Settings override is stored in private app preferences and sent only to Foreca using an
HTTPS Authorization header. App backup is disabled to keep the key out of
backups. The app distinguishes missing/rejected keys from network failures.

An optional default key can be bundled at build time. Set FORECA_API_KEY in
the build environment, or add foreca.apiKey to the root local.properties file
(already ignored by Git). A nonblank environment value takes precedence over
local.properties. With neither configured, the build contains an empty default.
Rebuild the APK after changing the default. A nonblank key saved in Settings
overrides the bundled default; clearing that field restores the default.
The Settings field shows only the saved override, never the bundled value.
Saving unrelated settings therefore does not pin an old default as an override.
Bundled keys are extractable from the APK: use a personal-use key, avoid sharing
the APK publicly, and rotate the key if exposed. They are not secret storage.

Automatic GPS/network fixes are passed to Foreca at their supplied precision,
in the API's longitude,latitude order. Explicit coordinates bypass geocoding;
city names use Foreca's location search. The same target coordinates select
the air-quality sources. Foreca's coordinate-based current weather is an
estimate, not a measurement at the tablet. The app requests up to six nearby
stations and prefers the nearest valid observation within 30 km and no more
than two hours old. Measured temperature, feels-like temperature, humidity,
wind and condition come from that same observation. If no station qualifies
or observations are unavailable, the coordinate estimate is labeled as such.
The weather card identifies Foreca and the station (or estimate), with the
weather timestamp separate from the download time. Station names may truncate
to fit. Precipitation is the estimated rate in mm/h; it is unavailable for
station observations rather than mixing an estimate into measured conditions.
Old-provider weather caches are not displayed after upgrade.

AirCare supplies all displayed air-quality measurements. The app sends the
same full-precision latitude and longitude used for weather to AirCare's v4
point endpoint, using radius=1 as its web map does. Of the returned stations,
it selects the nearest named station within 30 km with a valid EU AQI and a
measurement no more than two hours old (five minutes of future clock tolerance).
Station distance is calculated from coordinates, not a city-name match.
The source row displays only the station name, without a provider prefix.
Proximity does not guarantee sensor accuracy; AirCare aggregates government,
volunteer and other data sources, not only regulatory instruments.

If no returned station qualifies, a fresh AirCare area average is used and
labeled "Area average" (localized in Serbian). If neither qualifies, AQI is
unavailable. Pollutants come from the same selected station or aggregate;
missing values display as --, with no mixing of sources or local-PM overrides.
The badge uses AirCare's supplied EU AQI (pid=7), never US AQI (pid=10) or a
recalculation from particles. Colors and labels match the web map's inclusive
thresholds: Good <=26, Moderate <=33, Poor <=66, Bad <=100, Hazardous >100.
Zero is a valid reading. These thresholds were verified on 2026-09-09.

AirCare failures do not discard fresh Foreca weather. Cached AirCare readings
retain their station/aggregate identity and measurement timestamp for offline
display. Earlier SEPA/local-PM cache values are cleared without discarding
valid Foreca weather. The former community-PM history is no longer applied.
Weather and air-quality data refresh every hour. Waking a dimmed display also
requests fresh data when the previous request was at least 10 minutes earlier.

https://www.foreca.com/
https://getaircare.com/
https://mojvozduh.eu/web/

AirCare currently accepts this HTTPS endpoint without credentials:
https://getaircare.com/api/v4/api.php?requestType=point&lat={latitude}&lng={longitude}&radius=1&p=1
The Foreca API key is never sent to AirCare. Public access does not establish
a reuse license or guaranteed availability; rate limits and third-party terms
remain unverified. Contact AirCare about supported API access before relying
on this web-map endpoint for distribution: https://getaircare.com/solutions/

Build
-----
Set JAVA_HOME to JDK 17 and ANDROID_HOME to the Android SDK, then run:

  .\gradlew.bat assembleDebug

Validation:

  .\gradlew.bat testDebugUnitTest lintDebug
  .\gradlew.bat assembleDebug assembleDebugAndroidTest
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
  adb shell am instrument -w com.cachens.nexusdashboard.test/android.test.InstrumentationTestRunner

Optional live AirCare smoke test (uses the tablet's saved target coordinates):
  adb shell am instrument -w -e class com.cachens.nexusdashboard.AirCareDeviceTest -e liveAirCare true com.cachens.nexusdashboard.test/android.test.InstrumentationTestRunner

Device tests use isolated preferences and generate weather fixture screenshots
under the test app's external files directory. Live Foreca responses require
the user's API key; fixture tests do not verify account access or coverage.
Use the in-place install/instrument commands above on a configured tablet:
Gradle's connectedDebugAndroidTest cleanup can uninstall the dashboard and
clear its private settings and caches.

Install
-------

  adb install -r app\build\outputs\apk\debug\app-debug.apk

For the configured development PC, `tools\build-and-install.ps1` builds,
installs, and launches the app.

The app registers as a Home app so Android can use it as a dedicated launcher.
It also starts after boot on Android versions that permit boot-time activity
launches.
