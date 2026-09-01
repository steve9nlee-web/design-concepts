# MileageAdmin (admin app)

Android app for the admin to view every mileage claim submitted by staff via
[`../MileageClaim/`](../MileageClaim/). Claims are served by the Google Apps
Script backend in [`../mileage-backend/`](../mileage-backend/), which also
keeps `Mileage_Claims.xlsx` updated in Google Drive.

## Features

- Pull-to-refresh list of all claims, newest first: staff name, destination,
  purpose, claim date/time, and km.
- Header shows the claim count and total km.
- **Open Excel file in Google Drive** button opens `Mileage_Claims.xlsx`
  (view it in Drive/Sheets/Excel, or download it).

## Getting the APK

- **GitHub Actions**: every push touching this folder builds a debug APK —
  see the *Build Mileage APKs* workflow run, artifact `MileageAdmin-debug-apk`.
- **Android Studio**: open this folder, let Gradle sync, then
  *Build → Build Bundle(s)/APK(s) → Build APK(s)*.

## First-run setup

1. Deploy the backend first (`mileage-backend/README.md`).
2. Install the APK, open **Settings**, and paste the web-app URL (`…/exec`)
   and the shared secret. Tap **Test connection**, then **Save**.
3. Pull down to refresh the claim list.

## File map

| File | Role |
|------|------|
| `MainActivity.kt` | Claim list, totals, refresh, open-Excel button |
| `SettingsActivity.kt` | Server URL + shared secret, connection test |
| `ApiClient.kt` | JSON GET to the Apps Script web app + models |
| `ClaimsAdapter.kt` | Claim list rows |
| `Prefs.kt` | SharedPreferences wrapper |
