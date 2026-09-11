# ID Capture NAS

An Android app that captures (or imports) a photo of an **IC card or passport**,
fits it into a **fixed-size document box**, saves it to the gallery, and uploads
a copy to a **UGREEN NAS** over SMB.

## Features

- **Camera capture** (CameraX) with a dimmed overlay and a cut-out guide frame
  to align the document with. Two frame modes, switchable in the UI:
  - **IC card** — ISO ID-1 aspect ratio (85.60 × 53.98 mm).
  - **Passport** — passport data-page aspect ratio (≈125 × 88 mm).
- **Upload photo** button — pick an existing IC/passport photo with the system
  photo picker (no storage permission needed).
- Every image is normalized into a **fixed pixel box** (~20 px/mm) so all files
  on the NAS come out at the same predictable size:
  - IC: **1712 × 1080 px** — camera captures are cropped to the guide frame and
    scaled to fill the box.
  - Passport: **2500 × 1760 px** — imported photos are scaled to fit inside the
    box on a white background, so nothing is cut off.
- Files are named by document type: `IC_20260906_142530.jpg`,
  `PASSPORT_20260906_142530.jpg`, saved under `Pictures/IDCapture/`.
- **UGREEN NAS upload** (drive-bay button, top-right): every capture is uploaded
  in the background over **SMB2/3** (jcifs-ng) to
  `smb://<address>/<shared folder>/<subfolder>/`. Uploads run under WorkManager
  with retry + backoff, so captures taken off-Wi-Fi upload when the NAS is
  reachable again. NAS credentials are stored in **encrypted** shared
  preferences.
- Runtime camera-permission handling; works on Android 7.0 (API 24) and up.

## Setting up the NAS

1. On the UGREEN NAS (UGOS): make sure **SMB** is enabled
   (Control Panel → File Services), note a **shared folder** name (e.g.
   `Documents`), and use an account with write access to it.
2. In the app, tap the NAS button (top-right) and fill in:
   - **NAS address** — the NAS IP or hostname on your LAN (e.g. `192.168.1.10`).
   - **Shared folder** — the SMB share name.
   - **Subfolder** — created automatically on first upload (default `IDCapture`).
   - **Username / password** — leave the username empty for guest access.
3. Turn on **Upload captures to NAS** and save. The phone must be on the same
   network as the NAS (Wi-Fi) for uploads to complete; queued uploads retry
   automatically.

## Building the APK

Open the `IDCapture` folder in Android Studio and run, or from the command line
with the Android SDK installed:

```
cd IDCapture
gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow (`.github/workflows/build-idcapture-apk.yml`) builds
the debug APK on every push touching `IDCapture/` — download it from the
workflow run's **Artifacts** section, copy it to the phone, and install it
(allow "install from unknown sources").

## Project structure

```
app/src/main/java/com/example/idcapture/
  MainActivity.kt          Camera setup, capture, gallery import, crop/fit to
                           box, save to gallery, NAS settings dialog
  DocumentOverlayView.kt   Custom view: scrim + document window + corner brackets
  OutputBox.kt             Document modes (aspect ratios + fixed output boxes),
                           fill/fit/rotate bitmap helpers
  NasSettings.kt           Encrypted NAS connection prefs + upload enqueueing
  NasUploadWorker.kt       Background SMB upload via jcifs-ng
```

## Privacy note

This app handles photos of identity documents. Images stay on the device and
your own NAS — nothing is sent to any third-party service. Protect the NAS
share with a dedicated account and keep the folder access restricted.
