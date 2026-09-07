# WIPStatusTracker

An Android app for tracking pending (work-in-progress) jobs.

## Download

A ready-to-install debug APK is included in this repo: [`apk/WIPStatusTracker.apk`](apk/WIPStatusTracker.apk)

On your phone: download the APK, open it, and allow "Install unknown apps" when prompted (it is a debug build, not from the Play Store).

## Features

Each work item captures:

1. **Date and time stamp** — recorded automatically when the item is added
2. **Company name**
3. **Description of the work**
4. **Status buttons** on every item:
   - **URGENT** (red)
   - **WIP** (yellow)
   - **OK** (beige)
5. **Estimated finish date** (optional) — pick a date when adding the item; each card shows "Finish by: …". Once the date passes, the card shows a red **OVERDUE** badge and how many days overdue it is; jobs due today are flagged too.
6. **Done ✓ Delete** button — removes the item once the work is finished (with a confirmation dialog)

The list shows each job as a compact one-line row (status colour dot, company name, due date or OVERDUE flag). Tapping a row opens the full detail view with the timestamp, description, deadline, status buttons, and delete button. The row colour follows the selected status, so urgent jobs stand out at a glance. All items are saved on the device and survive app restarts. New items appear at the top of the list and default to WIP status.

## Requirements

- Android 7.0 (API 24) or newer

## Building from source

```bash
cd WIPStatusTracker
ANDROID_HOME=/path/to/android-sdk gradle assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

Built with Kotlin, AndroidX, and Material 3 (compileSdk 35, AGP 8.7.3).
