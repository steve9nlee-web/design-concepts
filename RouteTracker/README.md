# RouteTracker

Android app that automatically records the route you travel using GPS, with
your home WiFi network acting as the start/stop trigger.

## How it works

1. Connect your phone to your home WiFi, open the app, and tap
   **"Use current WiFi as home"** to designate it.
2. Enable **"Auto-track when I leave home WiFi"**. A persistent notification
   shows that monitoring is active.
3. The moment your phone **disconnects from the home WiFi** (i.e. you walk out
   of its range), GPS route recording starts automatically.
4. While travelling, the app logs your position (every ~5 s / 5 m) and later
   detects **stops** — places where you stayed within ~60 m for 3 minutes or
   more.
5. When your phone **reconnects to the home WiFi**, recording stops and the
   trip is saved.

Each trip shows:

- **Departure time** (when you left home WiFi) and **return time**
- **Total travel duration** and **distance**
- Every **stop**: arrival time, departure time, how long you stayed, and the
  street address (reverse-geocoded when possible, otherwise coordinates)
- The **route drawn as a polyline** (works fully offline — no map tiles or
  internet needed)
- **GPX export** so you can open the route in Google Earth, OsmAnd, etc.

> **Note on the "1 m" trigger:** WiFi radios cannot geofence to 1 metre — a
> phone typically stays connected for 10–50 m around the access point. The
> app therefore uses the closest reliable equivalent: recording starts as
> soon as the WiFi connection is lost and stops as soon as it is regained.
> Short WiFi hiccups are debounced (25 s leaving / 10 s returning) so a
> flaky connection at home doesn't create phantom trips.

## Install

Sideload `apk/RouteTracker-v1.0.apk` onto your phone (Android 8.0+):

1. Copy the APK to the phone and open it (allow "install unknown apps").
2. On first arm, grant **Location** permission and choose
   **"Allow all the time"** so recording works with the screen off.
3. Keep Location (GPS) turned on — Android also requires it for the app to
   read the WiFi network name.
4. Recommended: exclude the app from battery optimisation so Android does
   not kill the tracker mid-trip.

## Privacy

All data stays on the device in a local SQLite database. The app has no
internet permission usage — nothing is uploaded anywhere. The persistent
notification always shows when tracking is armed or recording. Long-press a
trip in the list to delete it.

## Build from source

```bash
cd RouteTracker
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+ and the Android SDK (platform 35, build-tools 34+).

## Tech notes

- Kotlin, Material 3, no third-party dependencies, minSdk 26 / targetSdk 34.
- `TrackingService`: foreground service (`location` type) that registers a
  WiFi `NetworkCallback`; SSID changes drive a debounced state machine that
  opens/closes trips.
- `LocationManager` GPS + network providers (no Play Services required).
- `StopDetector`: clusters consecutive points within 60 m for ≥ 3 min.
- Trips survive process death (active trip id persisted; service is
  `START_STICKY` and re-armed on boot).
