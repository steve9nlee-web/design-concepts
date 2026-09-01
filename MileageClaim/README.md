# MileageClaim (staff app)

Android app for staff to submit mileage claims. Each claim carries a
date/time stamp, destination, purpose, and the distance to claim in km, plus
the staff member's name (entered once, remembered). Claims are sent to the
Google Apps Script backend in [`../mileage-backend/`](../mileage-backend/),
which appends them to a Google Sheet and keeps `Mileage_Claims.xlsx` updated
in Google Drive. The admin views claims with
[`../MileageAdmin/`](../MileageAdmin/).

## Features

- Date and time are stamped automatically when the form opens and re-stamped
  after each submission; tap either field to adjust (for claims entered later).
- Claims submitted without signal are kept locally as **Pending** and re-sent
  automatically the next time the app opens or a claim is submitted, or
  manually with the *Send pending claims* button. Nothing typed is lost.
- Recent claims list shows Sent/Pending status.
- No Google sign-in needed on staff phones — the backend runs under the
  admin's account, protected by a shared secret.

## Getting the APK

- **GitHub Actions**: every push touching this folder builds a debug APK —
  see the *Build Mileage APKs* workflow run, artifact `MileageClaim-debug-apk`.
- **Android Studio**: open this folder, let Gradle sync, then
  *Build → Build Bundle(s)/APK(s) → Build APK(s)*.

## First-run setup (each staff phone)

1. Install the APK (allow "install from unknown sources" for a debug APK).
2. Open **Settings** (top-right) and paste:
   - **Server URL** — the Apps Script web-app URL (`…/exec`) from the
     backend deployment (see `mileage-backend/README.md`).
   - **Shared secret** — the same value as `SHARED_SECRET` in `Code.gs`.
3. Tap **Test connection**, then **Save**.
4. Enter your name once on the main screen — it is remembered.

## File map

| File | Role |
|------|------|
| `MainActivity.kt` | Claim form, date/time pickers, history, pending sync |
| `SettingsActivity.kt` | Server URL + shared secret, connection test |
| `ApiClient.kt` | JSON POST/GET to the Apps Script web app (handles its 302 redirect) |
| `Claim.kt` | Claim model + local history store (SharedPreferences JSON) |
| `HistoryAdapter.kt` | Recent-claims list |
| `Prefs.kt` | SharedPreferences wrapper |
