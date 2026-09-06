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
Tap Settings, then Select Photos. Select only images from the Google Photos
album "Natasa, Dusan & 2 others". If the old Android picker cannot browse
Google Photos directly, first download the chosen images with the official
Google Photos app and then select the downloaded files.

The dashboard copies only explicitly selected images into private app storage
and ignores all other tablet photos. User-selected images take priority. If no
user images are selected, the app uses a locally provisioned cache of 100 Novi
Sad photos, then falls back to three images bundled with the APK. Backgrounds
rotate after every 10 minutes of awake display time; the rotation timer pauses
while the dashboard is dimmed or not in the foreground.

Run `tools\provision-novi-sad-photos.ps1` from the configured development PC
to validate and transfer the machine-local `.local\novi-sad-cache` collection.
The app imports the staged files atomically into private storage at startup.
The cache survives `adb install -r`, but must be reprovisioned after uninstall
or a signing-key change. See ATTRIBUTIONS.txt and the cache's generated
ATTRIBUTIONS.txt for image credits.

Language and location
---------------------
The app starts in Serbian Latin. Tap "SR | EN" at the top-right to switch
between Serbian Latin and English.

Tap "PODEŠAVANJA" / "SETTINGS" at the bottom-right to enter a city or postal
code. The initial value is Novi Sad. Leave the field empty to use Android
location services instead.

Motion wake
-----------
The front camera performs low-resolution, in-memory motion detection. After
two minutes without motion or touch, the dashboard becomes black and lowers
screen brightness to the minimum. Motion or a touch restores normal
brightness. Camera frames are never saved or transmitted.

The display is dimmed rather than put into hardware sleep because Android
cannot reliably keep camera motion detection running while the app is
suspended.

News
----
The one-line news ticker rotates through the five newest titles from 021.rs
and the five newest titles from N1. Feeds refresh every hour and refresh when
the display wakes if the previous request was at least 10 minutes earlier. The
last successful set is cached for offline use.

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
Weather and fallback modeled air-quality data are retrieved from Open-Meteo.
For Novi Sad, AQI coordinates are centered on Telep. The dashboard prefers a
recent outdoor Sensor.Community monitor in the Telep area for indicative local
PM2.5 and PM10 readings. It retains the nearest official Serbian Environmental
Protection Agency (SEPA) station for regulatory measurements and gases, with
Open-Meteo as the final fallback.

Community PM readings are shown immediately, but they affect European AQI only
after the app has accumulated a sufficiently complete 24-hour average. This
avoids treating a short low-cost-sensor sample as a regulatory AQI value.
Weather and air-quality data refresh every hour. Waking a dimmed display also
requests fresh data when the previous request was at least 10 minutes earlier.

https://open-meteo.com/
https://vazduh.sepa.gov.rs/
https://sensor.community/

Build
-----
Set JAVA_HOME to JDK 17 and ANDROID_HOME to the Android SDK, then run:

  .\gradlew.bat assembleDebug

Install
-------

  adb install -r app\build\outputs\apk\debug\app-debug.apk

For the configured development PC, `tools\build-and-install.ps1` builds,
installs, and launches the app.

The app registers as a Home app so Android can use it as a dedicated launcher.
It also starts after boot on Android versions that permit boot-time activity
launches.
