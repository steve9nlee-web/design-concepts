# BillCapture

Android app that photographs bills/receipts, extracts the key details with
**on-device OCR** (no cloud account needed for scanning), and appends each
bill as a row in `Bill_Capture.xlsx` — exported to Downloads and, when a
Google account is connected, synced to Google Drive.

## What it captures (the A–H sections of a bill)

| Column | Section | Example (Kim Poh receipt) |
|--------|---------|---------------------------|
| A | Company Name | Kim Poh Restaurant Sdn. Bhd. |
| B | Company No. | 1199959-D |
| C | Address | 3321-1, Jalan Perak, 11600 Pulau Pinang |
| D | Contact No or Email | 017-9515294 |
| E | Bill Date | 07-09-2026 12:16 |
| F | Bill No | POS087500 |
| G | Category + Description | Food: R. Chicken Rice 13.00, Boiled Chicken 10.00, … |
| H | Total Amount | 31.00 |
| — | Photo Taken At | date & time the photo was taken (from EXIF) |

Every row also records **when the photo was taken** (EXIF capture time, or
the file's timestamp as a fallback).

## Three ways to capture

1. **Upload a photo (auto scan)** — *Upload Bill Photo (Auto Scan)*, the
   first button on the home screen, picks an existing photo (annotated
   A–H markings are fine) and runs OCR on it automatically; the review
   screen opens with the fields pre-filled so you only correct what it
   missed before saving.
2. **Scan with the camera** — *Capture Bill* opens the camera; ML Kit reads
   the photo on the device (the Chinese model is bundled, so bilingual
   Chinese/English receipts work, fully offline) and pre-fills all fields
   A–H for review before saving.
3. **Manual entry with photo** — if the app can't recognise the bill, use
   *Upload Photo (Manual Entry)* on the home screen (or *"Can't read it?
   Enter manually"* on the review screen). Pick or keep the bill photo and
   fill in each labelled section A–H; the hint on every field says where
   that information sits on a printed bill. On save the **photo itself is
   uploaded to Google Drive** (folder `BillCapture Photos`) together with
   the Excel row, so the original stays reviewable.

## OCR — no setup required

Scanning uses **ML Kit on-device text recognition** (Chinese + Latin).
There is no Cloud Vision key, no service account, and it works offline.

## Google Drive sync (optional, one-time setup)

Rows always save to `Bill_Capture.xlsx` in the app and a copy in
**Downloads**. To also sync the xlsx (and manual-entry photos) to Google
Drive:

1. Create a project at <https://console.cloud.google.com/> and enable the
   **Google Drive API**.
2. Configure the OAuth consent screen (External; add your account as a test
   user while unverified) and add the `.../auth/drive.file` scope.
3. Create an **OAuth client ID → Android** with package name
   `com.billcapture.app` and your signing SHA-1 (`gradlew signingReport`).
4. In the app, tap **Select Google Drive Account** and sign in.

The scope is `drive.file` (only files this app creates), which avoids
Google's app-verification requirements.

## Building the APK

- **Android Studio**: open this folder, Build → Build APK.
- **Command line**: `gradle assembleDebug` (Android SDK 35 required); the
  APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
- **GitHub Actions**: the `Build BillCapture APK` workflow builds on every
  push touching `BillCapture/` — download the `BillCapture-debug-apk`
  artifact from the workflow run.

## File map

| File | Role |
|------|------|
| `MainActivity.kt` | Home screen, Google Sign-In, entry to all flows |
| `CameraActivity.kt` | CameraX preview + guide-box overlay + capture |
| `ProcessingActivity.kt` | On-device ML Kit OCR, field review, save |
| `ManualEntryActivity.kt` | Photo upload + manual A–H entry fallback |
| `ReceiptParser.kt` | Extracts fields A–H from raw OCR text |
| `BillRepository.kt` | Shared save path + photo-taken timestamp |
| `HistoryActivity.kt` / `BillsAdapter.kt` | Bill list |
| `ExcelManager.kt` | xlsx read/write + Downloads export |
| `DriveUploader.kt` | Drive REST upload (xlsx + photos) |
| `SessionManager.kt` | Sign-in configuration/state |
| `BitmapUtil.kt` | Photo downscale + EXIF rotation |

## Implementation notes

- **No Apache POI.** The xlsx is written/read by `ExcelManager.kt` directly
  (an .xlsx is a zip of XML) — POI is unreliable on Android and adds ~10 MB.
- **Master xlsx lives in app-private storage**; each save exports a fresh
  copy to public Downloads (via MediaStore on Android 10+), so no storage
  permission prompts on modern Android.
- Rows written by the previous 4/5-column version of the app are still read
  and migrated into the new column layout on the next save.
