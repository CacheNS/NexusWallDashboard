# Nexus Wall Dashboard Requirements

This file is the source of truth for product requirements. Update it whenever
the requested behavior, constraints, or acceptance criteria change.

## Product

- **Name:** Nexus Wall Dashboard
- **Source location:** `C:\src\NexusWallDashboard`
- **Android namespace/application ID:** `com.cachens.nexusdashboard`
- **Target device:** Nexus 7 (2012 Wi-Fi, `grouper`, 1280x800, Android 4.4.2)
- **Orientation:** Landscape

## Dashboard

- Show a large 24-hour clock and the current date.
- Keep the clock and date local to the device; they do not require a network
  refresh.
- Show current weather and a five-day forecast in metric units.
- Show a small condition icon beside each forecast day's weather text.
- Show European AQI plus PM2.5, PM10, and NO2 values.
- Keep the weather/AQI card on the right and the five-day forecast across the
  lower portion of the display.
- Place the latest-update status and Settings label together on the top-right
  row, above the weather card.
- Render update timestamps using Android's configured 12/24-hour time format.
- Do not display a permanent language toggle on the dashboard.
- Use the same full-precision GPS coordinates, explicit manual coordinates,
  or geocoded manual location for weather and AirCare. Do not substitute a
  neighborhood, city center for a GPS fix, or preferred sensor.
- Use Foreca for weather, location search, and five-day forecasts. Preserve GPS
  coordinate precision and send longitude,latitude to Foreca.
- Prefer the nearest valid Foreca station observation within 30 km and two
  hours; otherwise label the coordinate-based current weather as an estimate.
- Keep measured temperature and feels-like values from the same observation.
  Display station/source and weather timestamp separately from download time.
- Show precipitation rate in mm/h for estimates and unavailable for station
  observations, which do not expose the same rate field.
- Configure the Foreca API key in a masked settings field. Store it privately,
  disable backups, send it only to Foreca in an HTTPS Authorization header,
  and never expose it in URLs, tracked source code, logs, or error messages.
- Support an optional bundled default key from the build environment variable
  FORECA_API_KEY, falling back to foreca.apiKey in ignored local.properties.
  A nonblank Settings override takes precedence; clearing it uses the default.
  Do not display or copy the default into the saved override. Document that
  keys bundled in the APK are extractable, not securely concealed.
- Show clear missing-key and rejected-key status messages.
- Do not display cached weather from an earlier provider after upgrading.
- Use AirCare alone for AQI, PM2.5, PM10, NO2 and O3. Query its v4 point
  endpoint with lat, lng, radius=1 and p=1, without Foreca credentials.
- Select the nearest returned named station with valid coordinates, a finite
  nonnegative EU AQI, distance at most 30 km, and data at most two hours old.
  Allow at most five minutes of future clock skew. Break distance ties by
  newer measurement, then lower station ID. Skip malformed candidates.
- If no station qualifies, use a fresh valid AirCare area average; otherwise
  show unavailable. Copy pollutants from the same selected object, never
  fill station gaps from the aggregate, and ignore forecast-offset entries.
- Display only the station name, without AirCare/SEPA prefixes. For aggregate
  fallback display only localized "Area average". Preserve source-row placement
  and truncate long station names rather than overlapping neighboring content.
- Use AirCare's EU AQI (pid=7) directly, not US AQI or locally computed AQI.
  Match its inclusive category boundaries: 26, 33, 66, 100, then hazardous.
  Zero is valid; missing, negative and nonfinite readings stay unavailable.
- Do not invoke SEPA, Sensor.Community or persisted community-PM overrides.
- Persist AQI provider, source name/kind, station ID, observation time, distance
  and aggregate radius. Clear pre-AirCare air-quality cache values while
  retaining valid Foreca weather; retain AirCare provenance for offline display.
- Air-quality fetch failures must not discard fresh Foreca weather.
- Do not describe AirCare stations as necessarily regulatory or imply that
  coordinates alone guarantee measurement accuracy. Document the public
  endpoint's unverified reuse terms, rate limits and stability.
- Cache the last successful weather response for offline display.
- If Wi-Fi or DNS is unavailable during startup, keep cached data visible and
  retry weather and news after one minute without showing raw network errors.
- Run full-screen and remain usable as the tablet's Home application.

## Refresh Scheduling

- Refresh weather, AQI, and news once per hour while the activity is active.
- Measure request intervals with monotonic elapsed time so manual wall-clock
  corrections cannot stop or prematurely trigger refreshes.
- When the dimmed display wakes, refresh weather/AQI and news only if at least
  10 minutes have elapsed since the respective previous request.
- Retry failed weather/AQI or news requests one minute after the failure.
- Pause scheduled network callbacks while the activity is not in the
  foreground and restore the correct remaining hourly or retry delay when it
  resumes.
- Prevent duplicate requests while a request for the same data is already in
  flight.
- If the configured weather target changes during an in-flight request,
  discard the stale completion and immediately request the new target.
- Continue displaying the last successful cached weather and news during
  outages and lifecycle transitions.

## Location

- Allow a city or an explicit latitude, longitude pair to be entered manually.
- Explicit coordinates must bypass city search and retain their precision.
- Default the manual-location field to empty so first run uses Android
  location services.
- On upgrade from a build that supplied an implicit location, clear the saved
  location selection and location-derived weather and PM caches once. Require
  the user to enable location services or enter a place in Settings.
- Manual location must work without Android location services.
- An empty manual-location field enables automatic GPS/network location.
- If no cached weather exists and automatic location has not produced a fix,
  prompt the user to wait for GPS, open Android Location Settings, or enter a
  place in app Settings.
- Request automatic-location updates no more frequently than every 10 minutes
  and only after movement of at least 1 km.
- Prefer network location for recurring automatic updates. Use GPS as a
  one-shot fallback when network location is enabled but has no fix newer than
  30 minutes; use recurring GPS only when network location is unavailable.

## Photos

- Do not request access to the user's Google account or Google Photos library.
- Use only photos intentionally selected from the Google Photos album
  `Natasa, Dusan & 2 others`.
- Do not scan or display unrelated photos elsewhere on the tablet.
- Let the user explicitly select images through the Android document picker,
  either directly from the official Google Photos provider when available or
  after downloading them with the official Google Photos app.
- Copy only selected images into the dashboard's private storage.
- Selected photos take priority and change once per local calendar day.
- Select photos deterministically from the date so the same photo remains
  selected across app restarts and display dim/wake cycles during that day.
- Refresh the photo when the local date, clock, or time zone changes without
  maintaining a recurring photo rotation timer.
- If no selected photos exist, use a locally provisioned cache of exactly
  100 licensed Novi Sad photos.
- Curate the city cache around recognizable, normal Novi Sad scenes:
  Petrovaradin Fortress, the Danube and bridges, Trg Slobode and the city
  centre, streets, architecture, parks, Štrand, skyline, sunsets, night views,
  and winter scenes.
- Exclude cemetery/grave imagery, funeral subjects, war/battle material,
  archival plans and maps, logos, and repetitive institutional close-ups.
- The current collection consists of 98 Wikimedia Commons images, one Pexels
  image, and one Unsplash image. Keep at least one Pexels and one Unsplash
  image when rebuilding the mixed-source cache.
- Normalize every city image to a landscape 1280x800 JPEG and target a total
  cache size of roughly 15-30 MB.
- Require exact filenames `photo_001.jpg` through `photo_100.jpg`, 100 unique
  SHA-256 hashes, and 100 unique source pages.
- Require `manifest.json` with a top-level `photos` array. Every entry must
  contain filename, title, author, source URL, license, license URL,
  modification note, and SHA-256.
- Permit only CC0, public domain, CC BY, CC BY-SA, Pexels License, and Unsplash
  License material appropriate for the personal offline cache.
- Keep the 100-photo cache in app-private storage and outside the public Git
  repository and APK.
- Keep the PC copy in ignored directory `.local\novi-sad-cache`.
- Stage provisioning files at
  `/sdcard/NexusWallDashboard/city-cache`.
- Store imported files under app-private `files/photos/novi-sad`; store
  explicitly selected user photos under `files/photos/user`.
- Import a staged city cache atomically so a damaged or interrupted transfer
  cannot replace the last complete cache.
- Recover the previous complete cache if the app is interrupted between backup
  and activation renames.
- Remove shared-storage staging files after a successful import.
- Preserve the private cache across `adb install -r`; reprovision it after an
  uninstall or signing-key change.
- If no city cache exists, select among the bundled freely licensed Novi Sad
  photos by local calendar day.
- Keep source and license details for bundled images in `ATTRIBUTIONS.txt` and
  for the external cache in its generated `manifest.json` and
  `ATTRIBUTIONS.txt`.

## Language

- Support Serbian Latin and English.
- Start in Serbian Latin.
- Provide the language selector inside Settings rather than permanently on
  the dashboard.
- The Settings dialog must contain manual location, separate mutually
  exclusive `Srpski` and `English` radio buttons, a screen-dimming timeout,
  Save, Cancel, and Select Photos controls.
- Translate dashboard labels, weather conditions, AQI labels, status text,
  settings text, dates, weekdays, and month names.

## Motion-Activated Display

- Use the Nexus 7 front camera to detect movement.
- Keep camera frames in volatile memory only.
- Never save, upload, or transmit camera frames.
- Make the inactivity period configurable in Settings with choices of 0, 1,
  3, 5, 10, 15, 30, and 60 seconds.
- Interpret 0 seconds as dimming immediately after motion stops. Allow only
  the camera's sub-second motion-sampling grace so active movement continues
  to keep the display awake without visible flicker.
- Default the inactivity period to 3 seconds.
- After the configured period without motion or touch, show a black screen at
  minimum brightness.
- Restore the dashboard to normal brightness when movement or touch is
  detected.
- Keep the camera preview low resolution and sample frames at a reduced rate
  to limit CPU use and heat.
- Prefer the lowest supported camera preview rate of at least 5 frames per
  second, falling back to a supported preview range when the device does not
  report discrete rates.
- Retain only sampled luminance values between motion checks rather than a
  complete camera frame.
- If camera access is unavailable, keep the dashboard functional without
  motion wake.
- Treat the black/minimum-brightness state as display-off behavior. Hardware
  sleep is out of scope because Android suspends reliable camera detection
  when the screen and app are actually asleep.

## News

- Show a one-line headline ticker.
- Redraw time-dependent dashboard content on its visible boundary: once per
  minute for the clock and once per 10 seconds while the news ticker is
  available.
- Do not schedule recurring canvas redraws while the dashboard is dimmed or
  the activity is not in the foreground. Data, photo, status, settings, and
  dim/wake changes must still redraw immediately.
- Render the source at 18sp and the headline at 19sp.
- Place the ticker flush against the bottom edge across the full display
  width.
- Place the update status and Settings label together at the top-right.
- Retrieve the five newest titles from `https://www.021.rs/rss/all`.
- Retrieve the five newest titles from `https://n1info.rs/feed/`.
- Show the source name and one title at a time.
- Rotate through all ten titles every ten seconds.
- Refresh both feeds every hour.
- Refresh both feeds when the display wakes if the previous request was at
  least 10 minutes earlier.
- Cache the last successful headlines and continue showing them when a feed is
  temporarily unavailable.
- Do not display article bodies, images, or advertising.

## Android Compatibility and Networking

- Support stock Android 4.4.2/API 19 while compiling against the current
  configured Android SDK.
- Keep Java source compatibility at Java 7.
- Use Conscrypt to provide modern TLS on Android 4.4.
- Preserve the Android trust store and the bundled ISRG Root X1, DigiCert
  Global Root G2, and GlobalSign Root R1 certificates.
- Do not embed API keys, account credentials, or private tokens.
- PurpleAir remains a future optional provider. Do not integrate it unless a
  useful nearby public sensor is verified or the user installs a personal
  sensor. Prefer that sensor's local-network JSON over a cloud key.

## Device Setup

- Remove third-party games and unrelated apps.
- Disable clearly nonessential bundled apps while retaining Settings, Wi-Fi,
  location, keyboard, Play Store, Google Play services, Chrome, downloads,
  package installation, media storage, launcher recovery, and core Android
  services.
- Reduce Android animation scales to improve responsiveness.
- Start on stock Android 4.4.2.
- Do not unlock the bootloader or erase the tablet unless a demonstrated
  compatibility blocker requires a custom ROM and the user approves the wipe.
- Do not unlock or root the tablet solely for charge limiting. The stock
  kernel exposes no charge-threshold or charging-enable control, and root
  access alone does not add hardware support.

## Safety and Reliability

- Keep memory use suitable for a 1 GB device.
- Downsample local images before display.
- Avoid unverified APK and ROM sources.
- Keep the wall mount ventilated and inspect the battery for swelling or heat.
- Prefer an external smart plug or USB power controller that keeps the battery
  near 55-80% rather than maintaining a permanent full charge. Keep any future
  controller integration on the local network and do not embed credentials.
- Recover after app restart, tablet reboot, and temporary Wi-Fi loss.
- Keep only one decoded RGB_565 background bitmap resident at a time.
- Reject incomplete, duplicate, corrupt, non-landscape, or hash-mismatched
  staged photo collections without replacing the active cache.

## Build, Deployment, and Acceptance

- Repository: `https://github.com/CacheNS/NexusWallDashboard`
- Branch: `main`
- Build with JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, compile/target
  SDK 35, and minimum SDK 19.
- Build and lint with:
  `.\gradlew.bat --no-daemon assembleDebug lintDebug`
- Install without deleting private data using:
  `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- Provision the local city cache using:
  `tools\provision-novi-sad-photos.ps1`
- Before release, verify:
  - The app launches after reboot and remains the full-screen dashboard.
  - Weather, AQI, and both news sources load without runtime errors.
  - Weather and AQI providers use the active GPS or geocoded manual
    coordinates, and AirCare station names or area-average labels are accurate.
  - The private city cache contains exactly 100 validated JPEGs.
  - The active manifest contains no cemetery/grave-related entries.
  - The full-width bottom news ticker and top-right status/Settings controls
    are readable at 1280x800.
  - The two language radio buttons appear inside Settings and no language
    control appears on the dashboard.
  - A short dim/wake or pause/resume does not refresh data inside the
    10-minute guard and does not advance the background photo.
  - The background photo changes after the local calendar date changes and
    remains unchanged for the rest of that day.
