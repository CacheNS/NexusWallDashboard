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
- Show current weather and a five-day forecast in metric units.
- Show a small condition icon beside each forecast day's weather text.
- Show European AQI plus PM2.5, PM10, and NO2 values.
- For Novi Sad, use Telep coordinates for air-quality source selection while
  retaining city-wide weather geocoding.
- Prefer a recent valid outdoor Sensor.Community monitor that is closer to
  Telep than the nearest available SEPA station for indicative PM2.5 and PM10.
- Use a persisted 24-hour community-PM average before allowing those readings
  to affect the European AQI value.
- Retain the nearest official SEPA station for regulatory measurements and
  gaseous pollutants, and use coordinate-based Open-Meteo data as the final
  fallback.
- Identify community and official sources separately without overstating the
  precision of privacy-obfuscated sensor coordinates.
- Refresh weather and air-quality data every hour.
- Refresh weather and air quality when the display wakes if the previous
  request was at least 10 minutes earlier.
- Cache the last successful weather response for offline display.
- If Wi-Fi or DNS is unavailable during startup, keep cached data visible and
  retry weather and news after one minute without showing raw network errors.
- Run full-screen and remain usable as the tablet's Home application.

## Location

- Allow a city or postal code to be entered manually in the app.
- Default the manual location to `Novi Sad`.
- Manual location must work without Android location services.
- An empty manual-location field enables automatic GPS/network location.

## Photos

- Do not request access to the user's Google account or Google Photos library.
- Use only photos intentionally selected from the Google Photos album
  `Natasa, Dusan & 2 others`.
- Do not scan or display unrelated photos elsewhere on the tablet.
- Let the user explicitly select images through the Android document picker,
  either directly from the official Google Photos provider when available or
  after downloading them with the official Google Photos app.
- Copy only selected images into the dashboard's private storage.
- Selected photos take priority and rotate once every 10 minutes.
- Count only awake foreground time toward the 10-minute photo interval; pause
  and preserve the remaining interval while the display is dimmed.
- If no selected photos exist, rotate a locally provisioned cache of exactly
  100 licensed Novi Sad photos.
- Keep the 100-photo cache in app-private storage and outside the public Git
  repository and APK.
- Import a staged city cache atomically so a damaged or interrupted transfer
  cannot replace the last complete cache.
- If no city cache exists, rotate the bundled freely licensed Novi Sad photos.
- Keep source and license details for bundled images in `ATTRIBUTIONS.txt`.

## Language

- Support Serbian Latin and English.
- Start in Serbian Latin.
- Provide an in-app `SR | EN` toggle.
- Translate dashboard labels, weather conditions, AQI labels, status text,
  settings text, dates, weekdays, and month names.

## Motion-Activated Display

- Use the Nexus 7 front camera to detect movement.
- Keep camera frames in volatile memory only.
- Never save, upload, or transmit camera frames.
- After two minutes without motion or touch, show a black screen at minimum
  brightness.
- Restore the dashboard to normal brightness when movement or touch is
  detected.
- Keep the camera preview low resolution and sample frames at a reduced rate
  to limit CPU use and heat.
- If camera access is unavailable, keep the dashboard functional without
  motion wake.
- Treat the black/minimum-brightness state as display-off behavior. Hardware
  sleep is out of scope because Android suspends reliable camera detection
  when the screen and app are actually asleep.

## News

- Show a one-line headline ticker.
- Render the ticker text at a large, wall-readable size.
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

## Safety and Reliability

- Keep memory use suitable for a 1 GB device.
- Downsample local images before display.
- Avoid unverified APK and ROM sources.
- Keep the wall mount ventilated and inspect the battery for swelling or heat.
- Use scheduled charging where practical rather than permanent full charge.
- Recover after app restart, tablet reboot, and temporary Wi-Fi loss.
