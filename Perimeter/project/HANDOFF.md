# Perimeter — developer handoff

Auto attendance for staff: log in when they reach company wi-fi inside a 100 m
geofence, log out one minute after they leave it, append one row per session to
a spreadsheet in Google Drive.

**Design reference:** `Perimeter - Auto Attendance App.dc.html` (four screens,
all states reachable via the Simulate panel).
**Spreadsheet:** `attendance_2026.xlsx`
**Backend:** `AppendSession.gs`

---

## 1. The trust rule

A login is written only when **all three** hold, continuously, for 15 seconds:

| Check | Source | Why |
|---|---|---|
| SSID equals the site's SSID | `WifiManager` / `NEHotspotNetwork` | Cheap first filter |
| BSSID is on the site allow-list | Same | An SSID is a string anyone can broadcast; the BSSID is the physical router |
| GPS fix inside the fence, accuracy ≤ 30 m | Fused location | Defeats a look-alike hotspot set up off-site |

Logout is the mirror: fire when the device has been **continuously outside** the
fence (or off the allowed network) for 60 s. Any re-entry inside that window
cancels the pending logout — do not write a row.

Both thresholds are server-configurable per site. Do not hard-code them.

---

## 2. Android

### Permissions
```
ACCESS_FINE_LOCATION
ACCESS_BACKGROUND_LOCATION      -- separate grant, Android 10+
ACCESS_WIFI_STATE
NEARBY_WIFI_DEVICES             -- Android 13+, required to read BSSID
FOREGROUND_SERVICE
FOREGROUND_SERVICE_LOCATION     -- Android 14+
POST_NOTIFICATIONS              -- Android 13+, for the logout notice
RECEIVE_BOOT_COMPLETED
```

Request in two steps: fine location first, then background location on a second
screen that explains why. A single combined prompt gets denied.

### Components
- **`GeofencingClient`** — register the site as a circular geofence
  (`INITIAL_TRIGGER_ENTER | INITIAL_TRIGGER_DWELL`, `loiteringDelay` 15 s).
  Geofences survive process death; a plain location listener does not.
- **Foreground service** with a persistent notification, started on ENTER and
  on boot. This is what keeps the session alive and what Play Store review will
  ask about — declare the background-location use case in the listing.
- **`WifiManager.getConnectionInfo()`** for SSID/BSSID. On Android 13+ this
  needs `NEARBY_WIFI_DEVICES` **and** the location toggle on; without it the
  BSSID returns `02:00:00:00:00:00` and the trust rule must fail closed.
- **Room** table `pending_sessions` for the offline queue.
- **WorkManager** (`NetworkType.CONNECTED`, exponential backoff) to drain it.

### Battery-saver reality
OEM aggressive battery managers kill foreground services. Detect with
`PowerManager.isIgnoringBatteryOptimizations()` and surface the "Background
tracking" row on the Rules screen as a warning when it's off. A session that
dies mid-shift must still be written with `flag = OPEN` on next launch — never
silently dropped.

## 3. iOS

- `NSLocationAlwaysAndWhenInUseUsageDescription`, `UIBackgroundModes: location`.
- `CLLocationManager.startMonitoring(for: CLCircularRegion)` for the fence.
- BSSID needs the **Access WiFi Information** entitlement plus
  `CNCopyCurrentNetworkInfo` (or `NEHotspotNetwork.fetchCurrent` on iOS 14+).
  Apple gates this — request the entitlement early, it is not automatic.
- No true always-on service. Region monitoring wakes the app; do the 60-second
  grace as a `UNTimeIntervalNotificationTrigger` plus a re-check on wake, not a
  live timer.

---

## 4. Screens

| Screen | States to build |
|---|---|
| **Status** | on site · grace countdown (60→0, cancellable) · logged out · login blocked (BSSID/GPS mismatch) · location off |
| **Log** | week total, average arrival, session list with `OK` / `LOW GPS` / `OPEN` flags |
| **Sheet** | connected · syncing · offline with queue depth and retry |
| **Rules** | fence radius, grace seconds, allowed router list, min GPS accuracy, background-tracking status |

The grace screen is the one to get right: a visible countdown with an "I'm still
on site" button. Silent logouts generate payroll disputes.

Diagnostics (SSID, router ID, GPS fix, accuracy, distance from centre) stay
visible on Status by default. Staff trust an automatic timesheet only when they
can see why it decided what it decided.

---

## 5. API

Phone → your server:

```
POST /v1/sessions
{
  "staff_id": "EMP-0412",
  "login_at": "2026-09-02T00:41:07Z",
  "logout_at": "2026-09-02T06:26:19Z",
  "site": "HQ-AMPANG",
  "ssid": "CORP-STAFF",
  "bssid": "3c:07:54:aa:1d:02",
  "bssid_match": true,
  "lat": 3.15780, "lng": 101.71170, "accuracy_m": 8,
  "method": "AUTO",
  "device_id": "iPhone-a91f"
}
```

Rules:

1. **Idempotency key is `staff_id + login_at`.** A retry after a dropped
   connection updates the same row; it never appends a duplicate.
2. **Timestamps are server-authoritative.** Take the phone's values as a claim,
   compare against server receipt time, and flag drift beyond a few minutes. A
   staff member who changes their phone clock must not be able to shift a shift.
3. **Two-phase writes.** POST on login with `logout_at` null (row lands with
   `flag = OPEN`), POST again on logout to close it. If the app dies between the
   two, the open row is already visible to HR.
4. **The phone never holds Drive credentials.** Your server calls the Apps
   Script endpoint or the Sheets API with a service account.

---

## 6. Spreadsheet

`attendance_2026.xlsx` — two tabs.

**`Sep_2026`**, one row per session:

```
A staff_id   B name        C date         D login_at    E logout_at
F duration_min (formula)   G duration_hhmm (formula)
H site       I ssid        J bssid        K lat         L lng
M accuracy_m N method      O flag         P device_id   Q row_key
```

- `F` and `G` are formulas, pre-filled to row 400. Appending values to A–E, H–Q
  makes duration compute itself. Extend the fill when you pass row 400.
- `O` is conditionally formatted: `OK` green, `LOW GPS` amber, `OPEN` red.
- `Q` is the idempotency key, `staff_id|login_at`. Don't hide it — the append
  script reads it.

**`Summary`** — weekly session counts, total minutes, hours and flagged counts
via `SUMIFS`/`COUNTIFS`, plus per-staff totals.

### Getting it into that Drive folder

Apps Script cannot write into a real `.xlsx`. Upload the file to the folder,
open it, **File > Save as Google Sheets** once, and keep the Google Sheets copy
as the live append target. Export to `.xlsx` when payroll needs a file. Then
follow the setup comment at the top of `AppendSession.gs`.

---

## 7. Still open

- Multi-site staff — who assigns which fences, and what happens when two
  fences overlap.
- Whether a written row can ever be edited, by whom, and whether the original
  value is kept.
- Retention period for location data. This is personal data under most privacy
  regimes; decide the window before launch, not after.
- Manager visibility: live presence, or day totals only.
