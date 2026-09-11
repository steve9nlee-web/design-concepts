# n8n backend

`perimeter-session.json` is an importable n8n workflow. The phone POSTs one
finished shift to it; n8n writes the row to your Google Sheet.

This is the arrangement `project/HANDOFF.md` section 6 asks for: **the phone
never holds Google credentials.** They live in n8n, which is the only thing that
can reach Drive.

```
phone ──POST /webhook/perimeter-session──▶ n8n ──Google Sheets API──▶ your sheet
        x-perimeter-token header                 (credential lives here)
```

---

## 1. Prepare the sheet

Open the spreadsheet in **Google Sheets** (not as an `.xlsx` — the Sheets API
cannot write into an uploaded Excel file; open it once and use
**File → Save as Google Sheets**).

Name a tab `Sep_2026` and put these **exact** headers in row 1. The names must
match, because n8n maps incoming fields to columns by header name:

```
row_key   staff_id   name   date   login_at   logout_at
duration_min   duration_hhmm   site   ssid   bssid   bssid_match
lat   lng   accuracy_m   method   flag   device_id   received_at   drift_min
```

Copy the spreadsheet ID out of the address bar — the long string between
`/d/` and `/edit`.

## 2. Import the workflow

In n8n: **Workflows → ⋯ → Import from File** and choose
`perimeter-session.json`.

## 3. Fill in the three blanks

Credentials never travel inside an exported workflow, so three things need
setting by hand:

| Node | What to do |
|---|---|
| **Check token and shape the row** | Replace `PUT-YOUR-TOKEN-HERE` with a long random string. Keep it — the app needs the same one. |
| **Append or update the row** | Pick your **Google Sheets** credential, then set the document to your spreadsheet ID and the sheet to `Sep_2026`. |
| **Append or update the row** | Confirm **Column to match on** is `row_key`. This is what stops duplicates. |

Then **Save**, and switch the workflow **Active** — a webhook only answers on
its production URL when the workflow is active.

## 4. Copy the URL into the app

On the **Phone posts a session** node, copy the **Production URL**. It looks
like:

```
https://your-n8n-host/webhook/perimeter-session
```

Put that and your token into
`app/src/main/java/com/perimeter/attendance/config/SiteConfig.kt`:

```kotlin
endpointUrl  = "https://your-n8n-host/webhook/perimeter-session",
webhookToken = "the-same-long-random-string"
```

The **Test URL** (`/webhook-test/…`) only works while you are watching the
editor. Don't ship it.

## 5. Prove it works before touching a phone

```bash
curl -i -X POST https://your-n8n-host/webhook/perimeter-session \
  -H "Content-Type: application/json" \
  -H "x-perimeter-token: the-same-long-random-string" \
  -d '{
    "row_key":"EMP-0412|2026-09-03T01:00:00Z",
    "staff_id":"EMP-0412","name":"Test Person","date":"2026-09-03",
    "login_at":"2026-09-03T01:00:00Z","logout_at":"2026-09-03T09:30:00Z",
    "duration_min":510,"duration_hhmm":"8:30",
    "site":"HQ-AMPANG","ssid":"CORP-STAFF","bssid":"3c:07:54:aa:1d:02",
    "bssid_match":true,"lat":3.15780,"lng":101.71170,"accuracy_m":8,
    "method":"AUTO","flag":"OK","device_id":"curl-test"
  }'
```

You want `HTTP/1.1 200` and `{"status":"ok", …}`, and one new row in the sheet.

**Run it a second time, unchanged.** You should still have exactly one row —
that is `row_key` matching doing its job. If you get two rows, the matching
column is not set.

---

## What the workflow does with the data

- **Duplicates.** `row_key` is `staff_id|login_at`. A retry after a dropped
  connection updates the same row instead of appending a second one, so the
  offline queue can retry as often as it needs to without inflating anyone's
  hours.
- **Two-phase writes.** The app posts once at login with `logout_at` empty
  (`flag = OPEN`) and again at logout to close it. An open row is visible to
  whoever runs payroll rather than silently missing.
- **`received_at`.** n8n stamps its own time. The phone's timestamps are a
  claim; this is the record of when the claim actually arrived.
- **`CLOCK AHEAD`.** Flagged only when the phone claims a time in the *future*.
  A row arriving hours or days late is normal — that is the offline queue after
  a weekend with no signal — so lateness alone is never treated as suspicious.
  Flagging it would land honest staff with a suspect timesheet to defend.

## Worth doing before you rely on it

- **Put the token in an n8n credential** rather than in the Code node, so it is
  not visible to everyone who can open the workflow. n8n's **Header Auth**
  credential on the webhook node does this properly.
- **Restrict who can edit the sheet.** Anyone with edit rights can rewrite
  recorded hours, and nothing here would show it.
- **Decide your retention period** for the location columns before you launch.
  `lat`, `lng` and `accuracy_m` are personal data under most privacy regimes.
- **The token ships inside the APK.** Someone who unpacks the app can post rows
  as any `staff_id`. Closing that needs per-device credentials issued at
  enrolment, which is a bigger piece of work than this file.
