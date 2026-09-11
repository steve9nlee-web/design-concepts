# BillCapture

Android app that photographs bills, extracts Date / Bill No / Description /
Amount via Google Cloud Vision OCR, and appends each bill as a row in
`Bill_Capture.xlsx` (exported to Downloads, optionally synced to Google Drive).

This is the complete project for `SETUP_AND_USAGE_GUIDE.md` (kept in
`Desktop\aaa`). Open this folder directly in Android Studio — no manual
file-copying needed; skip the guide's Part 1 and go straight to
**Part 2: Google Cloud Setup**.

## What you still need to do

1. **Google Cloud** (guide Part 2): enable Cloud Vision API + Google Drive
   API, create a service account key (JSON), and create an **Android** OAuth
   client with package name `com.billcapture.app` and your debug SHA-1
   (`gradlew signingReport`).
2. Put the service account key at
   `app/src/main/assets/google_credentials.json` (gitignored).
3. Run on a device or emulator with Google Play services.

Without the credentials file the app still runs — OCR is skipped and you fill
the four fields manually.

## Differences from the guide (intentional)

- **No Apache POI.** The xlsx is written/read by `ExcelManager.kt` directly
  (an .xlsx is a zip of XML) — POI is unreliable on Android and adds ~10 MB.
- **Drive scope is `drive.file`** (only files this app creates), not full
  `drive` — avoids Google's app-verification requirements.
- **`requestIdToken` is optional.** Sign-in works once the Android OAuth
  client (package + SHA-1) exists in your Cloud project. If you want an ID
  token, paste a *web* client ID into `SessionManager.OAUTH_CLIENT_ID`.
- **Master xlsx lives in app-private storage**; each save exports a fresh copy
  to public Downloads (via MediaStore on Android 10+), so no storage
  permission prompts on modern Android.

## File map

| File | Role |
|------|------|
| `MainActivity.kt` | Home screen, Google Sign-In |
| `CameraActivity.kt` | CameraX preview + guide-box overlay + capture |
| `ProcessingActivity.kt` | Vision OCR call, field extraction, save |
| `HistoryActivity.kt` / `BillsAdapter.kt` | Bill list |
| `ExcelManager.kt` | xlsx read/write + Downloads export |
| `DriveUploader.kt` | Drive REST upload (create/update) |
| `SessionManager.kt` | Sign-in configuration/state |
| `BitmapUtil.kt` | Photo downscale, EXIF rotation, base64 |
