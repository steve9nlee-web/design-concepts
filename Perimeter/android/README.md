# Perimeter — Android app

Kotlin + Jetpack Compose implementation of `../project/Perimeter - Auto Attendance App.dc.html`,
built to the spec in `../project/HANDOFF.md`.

**Partly verified.** The environment this was written in had no Android SDK, so
the Android half — Compose UI, the service, anything touching `Context` — has
never been compiled. Expect to fix an import or a signature on first build.

The half that decides someone's pay *is* verified. `AttendanceLogic.kt` holds
the trust rule and the dwell/grace state machine with every Android type kept
out on purpose, so it compiles and runs on a plain JVM. **21 unit tests pass**
(`./gradlew test`), and writing them caught a real bug — see below.

---

## 1. Open it

Android Studio → **File → Open** → select this `android/` folder (not the repo
root). Let it sync; the first sync downloads Gradle 8.9, AGP 8.6.1 and the
dependencies, which takes a few minutes.

You need **JDK 17** (Android Studio bundles it: Settings → Build Tools → Gradle →
Gradle JDK → jbr-17) and **SDK Platform 35** (Tools → SDK Manager).

## 2. Fill in your site

Everything you must change is in one place —
`app/src/main/java/com/perimeter/attendance/config/SiteConfig.kt`, in `DEFAULT`:

| Field | What to put |
|---|---|
| `ssid` | Your office wi-fi name, exactly |
| `allowedBssids` | Your router's MAC. One entry per access point |
| `lat` / `lng` | Your site centre |
| `radiusM` | Fence radius, metres |
| `staffId` / `staffName` | Per phone |
| `endpointUrl` | Your Apps Script `/exec` URL |
| `sharedSecret` | Must match `SHARED_SECRET` in `AppendSession.gs` |

**Finding the BSSID:** connect to the office wi-fi, then in Android Studio's
Logcat filter for `TrustCheck`, or use any "wifi analyzer" app — it is the
`3c:07:54:…` style MAC of the access point, not your phone's.

Get this wrong and the app is not broken — it just never clocks anyone in,
because the trust rule fails closed by design.

## 3. Build

**Run ▶** to install on a connected phone, or
**Build → Build Bundle(s) / APK(s) → Build APK(s)** for a file you can share.

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

That debug APK installs fine for testing. For staff phones you want a **signed
release** build: Build → Generate Signed Bundle / APK → APK → create a keystore
and **keep it safe** — lose it and you cannot update the app in place.

---

## The bug the tests caught

The first draft used `0L` to mean both "this run hasn't started" and a real
timestamp. Epoch 0 never occurs on a live phone, so it would not have shown up
in casual testing — but any run that *did* begin at 0 restarted its own clock on
every observation, meaning **a grace period could never expire and a staff
member would stay clocked in forever**. The marks are now nullable, which makes
the state unrepresentable rather than merely unlikely.

That is the argument for keeping this logic Android-free: the bug was invisible
by inspection and obvious the moment it could be executed.

## What is implemented

- **The trust rule** (`TrustCheck.kt`) — SSID, BSSID allow-list and a GPS fix
  inside the fence, all three, before anything is written. The `02:00:00:00:00:00`
  sentinel Android returns when the wi-fi permission is missing is treated as a
  failure, never a match.
- **The state machine** (`AttendanceService.kt`) — dwell before login, grace
  before logout, re-entry cancels a pending logout. Both measured from
  timestamps, not countdown timers, so doze cannot shorten them.
- **The admin gate** — three tabs for staff; the Rules tab does not exist until
  the PIN is entered on the padlock chip. Five wrong tries lock the keypad for
  60s, and **the counter is persisted**, so a force-quit does not hand out a
  fresh five tries. The session auto-locks after `adminIdleSeconds`.
- **Offline queue** (`SessionStore.kt`) — a JSON file, drained oldest-first. A
  failed post stops the drain rather than reordering rows.
- **Two-phase write** — the open row posts at login with `flag = OPEN`, so a
  session that dies mid-shift is still visible to HR.
- **Boot receiver** — the service comes back after a reboot or app update.

## What is not, and you should know it

- **Settings live on the handset.** The design says the server owns them and the
  phone only reads them. There is no server, so an admin edits them locally
  behind the PIN. A rooted phone could therefore change what gets *recorded*,
  not merely what is displayed. This is the single biggest gap versus the spec —
  see `HANDOFF.md` section 2.
- **The shared secret ships in the APK.** Anyone who unpacks it can post rows as
  any `staff_id`. A real backend holding the credential fixes this; Apps Script
  called directly from the phone cannot.
- **The Rules screen is read-only.** It shows the config and locks it away from
  staff, which is what was asked. Editing fields in-app is not wired up yet —
  change `SiteConfig.DEFAULT` and rebuild, or add `TextField`s and call
  `SiteConfig.save`.
- **OEM battery managers** kill foreground services on many Chinese Android
  skins. Before rollout, add the app to the "protected apps" list on each phone,
  or sessions will die mid-shift and land as `OPEN`.
- **Only the decision logic is tested.** The service shell, the offline queue and
  the UI have no tests, because they need a device or a Robolectric setup. The
  queue's ordering guarantee is the next thing worth covering.
