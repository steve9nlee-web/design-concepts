# Trip Logger

Android app that auto-captures today's date & time and logs trips to your
Google Sheet
([this spreadsheet](https://docs.google.com/spreadsheets/d/1e12ex5iKQJEY2OMdDjZc3IF8pH2gZvKLAzKfdZ6ptek/edit?gid=0#gid=0)).

Each saved entry appends one row: **Date | Time | Trip Time | Company | Driver | Description**

## App features

- **Date & time** — captured automatically (live clock on screen).
- **Trip Time** — `AM 7am`, `PM 7pm`, `OT 4pm`, `OT 8pm`, `OT 9pm`, or
  **Key in time…** (opens a time picker).
- **Company** — `Sumtec` or `Hacks`.
- **Driver** — Arthur (main, default) then Ah Huat, Alex, Adrian in a
  scroll-down list, plus a **+ Add** button to add new drivers (remembered on
  the phone).
- **Description** free-text field.
- **Offline safe** — if there's no signal, entries are kept on the phone and
  uploaded automatically next time.

## One-time setup (5 minutes) — connect the app to your Google Sheet

The app writes to your sheet through a tiny Google Apps Script that lives in
your own Google account (no passwords or API keys inside the app).

1. Open your spreadsheet → **Extensions → Apps Script**.
2. Delete anything in the editor and paste the whole contents of
   [`apps-script/Code.gs`](apps-script/Code.gs).
3. Click **Deploy → New deployment → Select type: Web app**.
   - *Execute as*: **Me**
   - *Who has access*: **Anyone**
4. Click **Deploy**, authorize when asked, and copy the **Web app URL**
   (it ends in `/exec`).
5. In the Trip Logger app, tap **Settings** (top right) and paste that URL.

Done — every **Save** now appends a row to the sheet instantly.

## Installing the APK

Enable *Install unknown apps* for your browser/file manager, copy
`app-debug.apk` to the phone and open it. The APK is also built automatically
by GitHub Actions on every push — download it from the workflow run's
**Artifacts**.

## Building yourself

```bash
cd TripLogger
gradle assembleDebug   # or ./gradlew if you add the wrapper / use Android Studio
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Requires Android SDK (compileSdk 34) and JDK 17+. Min Android version: 8.0
(API 26).
