# DocScannerCloud

A minimal Android app for photographing identification cards and passports with an
on-screen guide frame, with optional automatic backup of each capture to a Google
Drive folder you choose.

## Features

- Live camera preview (CameraX) with a dimmed overlay and a cut-out **guide frame**
  the user aligns the document with.
- Two frame modes, switchable in the UI:
  - **ID card** — ISO ID-1 aspect ratio (85.60 × 53.98 mm), the standard for national
    ID cards and driver's licenses.
  - **Passport** — passport data-page aspect ratio (≈125 × 88 mm).
- On capture, the photo is automatically **cropped to the guide frame** and saved to
  the gallery under `Pictures/DocScanner/`.
- Optional **Google Drive sync** (cloud button, top-right): when enabled, a copy of
  every capture is uploaded in the background to a Drive folder you name. Uploads
  run under WorkManager, so captures taken offline are uploaded when connectivity
  returns.
- Runtime camera-permission handling; works on Android 7.0 (API 24) and up.

## Project structure

```
app/src/main/java/com/example/docscanner/
  MainActivity.kt          Camera setup, capture, crop-to-frame, save to gallery,
                           Drive sync settings dialog + OAuth consent flow
  DocumentOverlayView.kt   Custom view: scrim + document window + corner brackets
  DriveSync.kt             Sync preferences (enabled/folder) + upload enqueueing
  DriveUploadWorker.kt     Background upload to Drive (REST v3, multipart)
app/src/main/res/
  layout/activity_main.xml Preview + overlay + mode toggle + shutter button
  layout/dialog_drive_sync.xml  Drive sync settings dialog
```

## Google Drive sync setup (one-time, required before the feature works)

The app uses Google Identity Services with the narrow `drive.file` scope — it can
only see files and folders it created itself, never the rest of the user's Drive.
For Google to issue tokens, the app must be registered in Google Cloud:

1. Create a project at <https://console.cloud.google.com/> and enable the
   **Google Drive API**.
2. Configure the **OAuth consent screen** (External, add your Google account as a
   test user while unverified) and add the `.../auth/drive.file` scope.
3. Create an **OAuth client ID → Android** with:
   - Package name: `com.example.docscanner`
   - SHA-1 of your signing key (debug: `gradlew signingReport`)

No client ID is pasted into the code — Google Play services matches the app by
package name + SHA-1 at runtime. Without this registration, enabling sync fails
with an authorization error.

In the app, tap the cloud button (top-right), turn on **Upload a copy of each
capture**, and optionally change the Drive folder name (default
`DocScannerCloud`). The folder is created at the root of My Drive on first upload.

> **Privacy note:** ID and passport scans are highly sensitive. Drive sync is
> off by default and opt-in per device; only enable it on a Google account you
> control and trust.

## Build & run

1. Open the `DocScanner` folder in **Android Studio** (Ladybug or newer).
2. Let it sync — Studio downloads Gradle 8.10.2 and all dependencies automatically.
   (If Studio asks about a missing Gradle wrapper, choose to use the version from
   `gradle/wrapper/gradle-wrapper.properties` or run `gradle wrapper` once.)
3. Run on a physical device (the emulator's virtual camera works too, but a real
   camera is far more useful here).

Or from the command line with an Android SDK installed:

```
gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## How the crop works

`PreviewView` uses FILL_CENTER: the camera image is uniformly scaled to cover the
view and center-cropped. `MainActivity.cropToFrame()` inverts that transform to map
the overlay's frame rectangle (view coordinates) back onto the full-resolution
captured bitmap, so the saved JPEG contains exactly what was inside the frame.

## Ideas for next steps

- Review screen with **Retake / Use photo** before saving.
- Auto-capture when the document fills the frame and is steady (ML Kit object /
  text detection).
- Edge detection + perspective correction (OpenCV) for skewed shots.
- Torch toggle and tap-to-focus.
