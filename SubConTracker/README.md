# SubConTracker

Android app that records a sub-contractor's work day:

1. **Worker name** — typed once, remembered for next time.
2. **Date & time stamp** — captured automatically the moment you tap
   **RECORD WORK DAY** (a live clock on screen shows what will be stamped).
3. **AM / PM** — pre-selected from the current time, changeable before recording.

Every record is appended as a row (`Worker Name | Date | Time | AM/PM`) to
**`SubConTracker.xlsx`**, which is:

- saved to the phone's **Downloads** folder on every record (works offline,
  no setup needed), and
- uploaded to **Google Drive** (same file, updated in place) when you tap
  **Connect Google Drive** and sign in.

The xlsx is written natively (an .xlsx is a zip of XML) — no Apache POI,
no extra 10 MB.

## Getting the APK

The GitHub Actions workflow `.github/workflows/subcontracker-apk.yml` builds
the APK on every push that touches this folder. Open the repo's **Actions**
tab → latest "Build SubConTracker APK" run → download the
**SubConTracker-debug-apk** artifact, unzip it, copy `app-debug.apk` to the
phone and install it (allow "install unknown apps" when prompted).

You can also build locally in Android Studio: open this folder and run
**Build → Build APK(s)**.

## Enabling the Google Drive upload

Recording to Excel in Downloads works out of the box. Drive upload needs a
one-time (free) Google Cloud setup, because Google requires each app that
signs in to be registered:

1. Go to https://console.cloud.google.com/ and create a project
   (e.g. "SubConTracker").
2. **APIs & Services → Library** → enable **Google Drive API**.
3. **APIs & Services → OAuth consent screen** → External → fill in the app
   name and your email → add your Google account under **Test users**.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**
   → Application type **Android** →
   - Package name: `com.subcontracker.app`
   - SHA-1: `3A:95:07:13:9F:16:0E:07:A6:05:25:A4:46:0B:30:20:B6:76:2B:AB`
     (this is the SHA-1 of the keystore committed in this folder, which
     signs every APK the workflow builds — so it never changes.)
5. Install the app, tap **Connect Google Drive**, and sign in.

After that, every record updates `SubConTracker.xlsx` in the Drive of the
signed-in account. The app only uses the `drive.file` scope (access to files
it created itself), which avoids Google's app-verification review.

> Note: the committed `subcontracker.keystore` (password `subcontracker1`)
> is a convenience signing key for this personal app, not a secret for
> anything else. If you ever publish to Play Store, generate a fresh private
> keystore and update the SHA-1 in Google Cloud.

## File map

| File | Role |
|------|------|
| `MainActivity.kt` | Record screen: name, live clock, AM/PM, save + upload |
| `HistoryActivity.kt` / `EntriesAdapter.kt` | List of recorded entries |
| `ExcelManager.kt` | xlsx read/write + Downloads export |
| `DriveUploader.kt` | Drive REST upload (create/update in place) |
| `SessionManager.kt` | Google Sign-In configuration/state |
