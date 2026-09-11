# CalorieCam 📸🍎

Android app that tracks your daily food calories from photos:

- **Snap a meal** → the photo is analyzed automatically by AI (Claude vision)
  and turned into calories + protein/carbs/fat, which you can adjust before saving.
- **Profile-driven goals** — your name, age, height, weight and target weight
  set a personal daily calorie target (Mifflin-St Jeor BMR × activity level,
  −500 kcal when losing weight, +300 kcal when gaining).
- **Google Drive spreadsheet log** — every saved meal is appended row by row to
  a Google Sheet in your Drive (name, height, weight, target weight, date, time,
  food, calories, macros, notes). Export it as Excel any time. Offline meals are
  kept locally and synced later.
- **Daily coach tips** — a diet suggestion and an exercise suggestion every day,
  matched to whether you're losing, gaining or maintaining, plus a live
  "calories left today" status.

## Getting the APK

Every push that touches `CalorieCam/` runs the **Build CalorieCam APK**
GitHub Actions workflow. Open the repo's **Actions** tab, pick the latest
run, and download the `CalorieCam-debug-apk` artifact. Unzip it and install
`app-debug.apk` on your phone (allow "install from unknown sources").

Or build locally with Android Studio / `./gradlew assembleDebug`.

## First-run setup

1. Install and open the app — fill in your **profile** (name, age, height,
   weight, target weight, activity level).
2. Menu → **Settings** → paste your **Claude API key**
   (create one at [console.anthropic.com](https://console.anthropic.com)).
   Photo analysis costs a fraction of a cent per meal.
3. Follow [docs/google-sheets-setup.md](docs/google-sheets-setup.md) (~5 min)
   to create your Google Sheet endpoint, and paste its URL into Settings.

Then just tap **Snap a meal** whenever you eat.

## Tech notes

- Kotlin, Material 3, view binding, min SDK 26 (Android 8.0+).
- No backend of its own: photos go directly from your phone to the Claude API;
  rows go directly to your own Google Apps Script endpoint. The API key is
  stored only in the app's private preferences on your device.
- Calorie estimates from photos are approximate — treat them as a guide,
  not a medical measurement.
