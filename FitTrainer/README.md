# FitTrainer 💪

An Android app for guided exercise training with **age-adapted programs**, **timed goals**
and **workout records exportable to Excel (.xlsx)**.

## Features

- **8 training areas** — Legs, Arms, Chest, Back, Shoulders, Core/Body, Stamina/Cardio and
  Flexibility — with 37 built-in exercises, each with step-by-step method instructions.
- **Age categories** — Teen (13-17), Young Adult (18-35), Middle Age (36-50), Senior (51-64)
  and Elder (65+). Sets, reps, hold times and rest periods automatically scale to the selected
  age group, and each exercise shows age-specific safety and method guidance based on WHO
  physical-activity recommendations.
- **Timed goals** — every exercise shows a 4-week target ("what to achieve in certain time")
  tailored to your age group, plus a built-in exercise/rest timer with a completion beep.
- **Workout records** — log sets and reps/seconds for every workout; the history screen groups
  them by day and shows totals.
- **Excel export** — one tap exports all records to a real `.xlsx` spreadsheet
  (saved to your phone's Downloads folder, or shared via any app). The file opens in
  Excel, Google Sheets and LibreOffice. No external libraries needed — the app writes the
  Open XML spreadsheet format itself.

## Building the APK

```bash
cd FitTrainer
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17+ and the Android SDK (compileSdk 35). A GitHub Actions workflow
(`.github/workflows/build-fittrainer-apk.yml`) also builds the APK automatically on every
push — download it from the workflow run's **Artifacts** section.

## Installing on your phone

1. Copy `app-debug.apk` to your Android phone (or download the CI artifact on the phone).
2. Open the file; allow "Install unknown apps" for your browser/file manager when prompted.
3. Launch **FitTrainer**, pick your age group, and start training.

## Disclaimer

Training guidance is general information based on public physical-activity guidelines and is
not medical advice. Consult a doctor before starting a new exercise program, especially if
over 65 or with existing health conditions.
