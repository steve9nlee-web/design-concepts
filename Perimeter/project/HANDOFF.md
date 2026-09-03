# Perimeter — developer handoff

Auto attendance for staff: log in when they reach company wi-fi inside a 100 m
geofence, log out one minute after they leave it, append one row per session to
a spreadsheet in Google Drive.

**Design reference:** `Perimeter - Auto Attendance App.dc.html` (three staff
screens plus the admin keypad and the admin-only Rules screen; all states
reachable via the Simulate panel — the demo PIN is shown under it).
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

## 2. Roles — admin vs staff

The app is installed on the **staff member's own phone** and there is no sign-in.
Everything below follows from that: there is no session to authorise against, so
the split is done by hiding a screen behind a local PIN, and by making the
**server** the authority on anything that matters.

### What staff can reach

Three tabs, always: **Status**, **Log**, **Sheet**. No Rules tab exists in the
tab bar. Staff can read their own diagnostics and their own hours, and can hit
**Retry now** when the sync queue is backed up. They cannot change the fence,
the grace period, the allow-list, or the minimum accuracy, and they cannot see
the Drive file name, the folder id, or the total row count.

### What admin reaches, and how

A narrow **padlock chip** sits at the right end of the tab bar — visible to
everyone, but it leads to a keypad, not to settings. It is deliberately *not* a
hidden gesture: an admin walking up to a staff phone must be able to find it
without training.

```
tap padlock  →  PIN keypad  →  correct PIN  →  Rules tab appears and is selected
                                            →  Sheet screen reveals file/folder/rows
```

- PIN length follows the configured PIN (4 digits in the prototype).
- **5 wrong entries lock the keypad for 60 s.** Without this a 4-digit PIN is
  10,000 guesses. Keep the counter in encrypted storage, not memory, or a force
  quit resets it.
- The admin session **auto-locks after 120 s idle** (`adminIdleSeconds`), and
  there is an explicit **Lock** button on the Rules screen. Locking returns the
  user to Status and removes the Rules tab again.
- On lock, re-hide the Sheet screen's admin block. Do not cache it.

### What this does and does not buy you

A PIN checked on the handset stops ordinary staff. It does **not** stop a rooted
device, a repackaged APK, or anyone who reads the PIN out of the binary. So:

1. **Never ship the PIN in the APK.** It arrives with the site config from the
   server and is stored in the Keystore / Keychain, never in `SharedPreferences`
   or `UserDefaults` as plain text. Rotating it must not require a release.
2. **The phone never writes site config.** It `GET`s the fence, grace, allow-list
   and min accuracy; it has no endpoint to change them. An admin edit on the
   Rules screen is an *authenticated admin API call*, not a local write — if it
   fails, the screen must show the old value, not the typed one.
3. **The server validates every row against its own copy of the config.** A
   handset that has been tampered with can change what it *displays* and never
   what gets *recorded*. This is the part that actually enforces the rule; the
   PIN is only UI.
4. If a real admin identity is ever needed (audit trail of who changed the fence
   and when), the PIN is not enough — that needs an actual admin login on the
   API call, and the PIN becomes just the screen unlock in front of it.

---

## 3. Android

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

## 4. iOS

- `NSLocationAlwaysAndWhenInUseUsageDescription`, `UIBackgroundModes: location`.
- `CLLocationManager.startMonitoring(for: CLCircularRegion)` for the fence.
- BSSID needs the **Access WiFi Information** entitlement plus
  `CNCopyCurrentNetworkInfo` (or `NEHotspotNetwork.fetchCurrent` on iOS 14+).
  Apple gates this — request the entitlement early, it is not automatic.
- No true always-on service. Region monitoring wakes the app; do the 60-second
  grace as a `UNTimeIntervalNotificationTrigger` plus a re-check on wake, not a
  live timer.

---

## 5. Screens

| Screen | States to build |
|---|---|
| **Status** | on site · grace countdown (60→0, cancellable) · logged out · login blocked (BSSID/GPS mismatch) · location off |
| **Log** | week total, average arrival, session list with `OK` / `LOW GPS` / `OPEN` flags |
| **Sheet** | connected · syncing · offline with queue depth and retry — plus, admin only, the file/tab/folder/rows block and **Open in Drive** |
| **Keypad** | idle · digits entered · wrong PIN with tries remaining · locked out with countdown. Staff-facing; this is the only admin entry point |
| **Rules** | admin only, does not exist in the tab bar otherwise — fence radius, grace seconds, allowed router list, min GPS accuracy, background-tracking status, plus the session banner with countdown and **Lock** |

The grace screen is the one to get right: a visible countdown with an "I'm still
on site" button. Silent logouts generate payroll disputes.

Diagnostics (SSID, router ID, GPS fix, accuracy, distance from centre) stay
visible on Status by default. Staff trust an automatic timesheet only when they
can see why it decided what it decided.

---

## 6. API

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

Site config, read-only to the handset:

```
GET /v1/sites/HQ-AMPANG
{
  "radius_m": 100,
  "grace_s": 60,
  "min_accuracy_m": 30,
  "allowed": [{"ssid": "CORP-STAFF", "bssid": "3c:07:54:aa:1d:02"}, ...],
  "admin_pin": "4917",          // rotate server-side; store in Keystore/Keychain
  "admin_idle_s": 120,
  "config_version": 37
}
```

5. **There is no `PUT /v1/sites/:id` from the staff app.** Admin edits made on
   the Rules screen go to a separate authenticated admin endpoint. Poll this on
   launch and on network regain; `config_version` tells the phone whether to
   refresh. Stamp the version onto each session row so a disputed shift can be
   replayed against the rules that were live at the time.
6. **Reject on the server, not just on the phone.** A row whose coordinates sit
   outside the fence the server holds is flagged, whatever the handset claimed.

---

## 7. Spreadsheet

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

## 8. Still open

- Multi-site staff — who assigns which fences, and what happens when two
  fences overlap.
- Whether a written row can ever be edited, by whom, and whether the original
  value is kept.
- Retention period for location data. This is personal data under most privacy
  regimes; decide the window before launch, not after.
- Manager visibility: live presence, or day totals only.
