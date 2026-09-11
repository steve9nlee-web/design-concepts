# Idea Capture

An Android app to quickly record ideas or work items as they come across your mind, each tagged with an urgency status.

## Features

- **Capture by typing or voice**: type your idea, or tap the mic button to speak in English — speech is turned into text using Android's built-in speech recognizer.
- **Two-row entry form**: row 1 is the idea/work title (typing or voice), row 2 is a longer description (typing, with voice also available).
- **Status tagging**: mark each idea as **Urgent**, **Not so urgent**, or **WIP** (work in progress).
- **List view**: ideas are sorted Urgent → WIP → Not so urgent, with color-coded status badges.
- **Quick actions**: tap a card to edit, tap its status badge to cycle the status, long-press to delete.
- **Filters**: filter the list by All / Urgent / Not so urgent / WIP.
- **Offline & private**: everything is stored locally on the device (SharedPreferences + JSON); no network access, no account.

## Getting the APK

Every push to the repository builds a debug APK via GitHub Actions (workflow: `Build IdeaCapture APK`). Download it from the workflow run's **Artifacts** section (`IdeaCapture-debug-apk`), copy it to your phone, and install it (you may need to allow "install from unknown sources").

## Building locally

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
cd IdeaCapture
gradle assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Tech

- Kotlin, single-module Android app (minSdk 24, targetSdk 35)
- Material 3 components (chips, cards, FAB)
- `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` for voice-to-text (no microphone permission needed — uses the system speech dialog)
