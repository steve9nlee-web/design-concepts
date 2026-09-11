# ScamCallGuard

An Android app that identifies and blocks scam calls using Android's official
**call screening** API (`CallScreeningService`, Android 10+). When a call comes
in, the number is checked against a local blocklist database; matches are
either rejected before the phone rings ("block" mode) or allowed through with a
high-priority warning notification ("warn" mode).

## Features

- **Automatic call screening** — holds the system `ROLE_CALL_SCREENING` role so
  every incoming call is checked, with no dialer replacement needed.
- **Block or warn mode** — silently reject flagged calls, or let them ring with
  an on-screen scam warning.
- **Local blocklist database** (SQLite) — numbers are stored normalized so
  `+1 (555) 123-4567` and `555-123-4567` match the same entry.
- **Manual lookup** — type any number to check whether it is flagged.
- **User reports** — add numbers to your own blocklist with a label.
- **Community feed updates** — pull a JSON blocklist from any HTTPS URL you
  configure (self-hosted or community-maintained).
- **Screened-call history** — see which calls were blocked or warned, and when.

## About Truecaller

Truecaller's caller-ID database is proprietary: there is **no public lookup
API**, and their official SDK only provides phone-number-based sign-in for your
own users, not spam lookups. Scraping or proxying their database violates their
terms of service, so this app does not do that.

Instead, the app uses a pluggable data source:

- The bundled local database, fed by your own reports and any JSON feed you
  trust (see format below). Public scam-number data (e.g. regulator complaint
  data) can be converted into this format.
- If you obtain licensed access to a commercial caller-ID API (for example
  [Truecaller for Business](https://www.truecaller.com/business)), wire it into
  `sync/BlocklistUpdater.kt` — that file is the single integration point for
  remote data.

## Blocklist feed format

```json
{
  "entries": [
    { "number": "+15550100", "label": "IRS impersonation scam", "category": "scam" }
  ]
}
```

`label` and `category` are optional. On each update, previously imported feed
entries are replaced by the new feed; user-added numbers are kept.

## Building

Requires JDK 17+ and the Android SDK (platform 34).

```bash
cd ScamCallGuard
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow (`.github/workflows/build-scamcallguard.yml`) builds
the debug APK on every push touching this project and uploads it as a workflow
artifact.

## Setup on device

1. Install the APK (enable "install unknown apps" for your file manager).
2. Open ScamCallGuard and tap **Enable call screening**, then choose
   ScamCallGuard as the caller ID & spam app.
3. Allow notifications so you can see block/warn alerts.
4. Add numbers manually or configure a feed URL and tap **Update from feed**.
