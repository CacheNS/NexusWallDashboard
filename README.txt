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

The dashboard copies only explicitly selected images into private app storage,
ignores all other tablet photos, and rotates the selection once per minute. If
no image has been selected, bundled freely licensed photos of Novi Sad are
displayed. See ATTRIBUTIONS.txt for image credits.

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
and the five newest titles from N1. Feeds refresh every 30 minutes and the last
successful set is cached for offline use.

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
When an official Serbian Environmental Protection Agency (SEPA) monitoring
station with current particle measurements is within 30 km, the dashboard
uses the nearest station and shows its name and distance:
https://open-meteo.com/
https://vazduh.sepa.gov.rs/

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
