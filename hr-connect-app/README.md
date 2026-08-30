# HR Connect — HR Department Android App

A self-contained Android app (APK) for the HR department. All data stays on the
phone (localStorage inside the app's WebView) — no server required.

## Features

1. **Profile** — employee details (name, ID, department, position, email, phone, join date), editable and saved on device.
2. **Attendance** — every shift is tracked; filter by *This week / This month / Last 3 months*; totals for hours worked, days present and late arrivals. **Late arrivals are flagged in red.** History is kept for **3 months** (older records are purged automatically).
3. **Leave** — fill the leave form (category, reason, description), **pick dates**, **attach a document** (MC/letter/photo/PDF via the phone's file picker), **submit**, then **track approval in real time** on a status timeline (Submitted → Approved/Rejected). The *Management* tab is the approver panel (Approve / Reject with a note).
4. **Overtime** — apply with date, start/end time (duration auto-computed), reason and description; same approval flow and history tracking.
5. **Check-In Panel** — clock in / clock out with the handphone, live clock, today's shift from the roster, worked-hours counter, optional GPS location stamp. Clocking in after shift start + grace period flags the day **LATE (red)**.
6. **Rostered Hours** — weekly roster editor (per-day on/off, start/end times, late grace period), today's shift, weekly rostered-hours total, and a month calendar showing rostered (blue), worked (green) and late (red) days.

Sample data is loaded on first launch so the app is explorable immediately —
clear it with one tap from the dashboard banner or the Profile page.

## Layout

```
hr-connect-app/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/hrconnect/app/MainActivity.java   # WebView shell: file chooser, geolocation, back handling
│   ├── assets/www/index.html                      # the entire HRM app (single-file SPA)
│   └── res/drawable/ic_launcher.png
├── app/build.gradle / build.gradle / settings.gradle   # standard Android Studio project
└── scripts/build-apk.sh                           # SDK-less build (apktool + dx + apksig)
```

## Building

**With Android Studio (recommended):** open `hr-connect-app/` and Run — it is a
plain single-activity Java project with no dependencies.

**Without the Android SDK:** `scripts/build-apk.sh` builds and signs the APK
using apktool + dalvik-dx + apksig (all fetched from Maven Central / npm).
This is how the checked-in release APK was produced.

## Installing

Copy `hr-connect.apk` to the phone and open it. The APK is signed with a
self-signed key (APK Signature Scheme v2), so allow "install from unknown
sources" when prompted. Min Android version: 7.0 (API 24).

On first clock-in the app asks for location permission — this is optional and
only stamps coordinates onto the attendance record; denying it simply skips
the location stamp.
