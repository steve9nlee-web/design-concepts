/**
 * Perimeter — attendance row appender (Google Apps Script)
 *
 * SETUP
 * 1. Open the spreadsheet in that Drive folder (attendance_2026.xlsx).
 *    Apps Script cannot write to a real .xlsx — File > Save as Google Sheets
 *    once, and keep the Google Sheets copy as the live target. Export back to
 *    .xlsx whenever payroll needs a file.
 * 2. Extensions > Apps Script, paste this file, set SHARED_SECRET below.
 * 3. Deploy > New deployment > Web app
 *      Execute as:  Me
 *      Who has access:  Anyone
 *    Copy the /exec URL — that is the endpoint the mobile backend calls.
 * 4. Never put SHARED_SECRET in the mobile app. The phone talks to your
 *    server; your server holds the secret and calls this endpoint.
 */

var SHARED_SECRET = 'CHANGE-ME-32-RANDOM-CHARS';
var SHEET_NAME    = 'Sep_2026';
var KEY_COLUMN    = 17; // column Q — row_key, used for idempotency

function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.waitLock(30000); // serialise appends so two phones can't collide
  try {
    var body = JSON.parse(e.postData.contents);

    if (body.secret !== SHARED_SECRET) {
      return json({ ok: false, error: 'unauthorized' });
    }

    var required = ['staff_id', 'login_at'];
    for (var i = 0; i < required.length; i++) {
      if (!body[required[i]]) return json({ ok: false, error: 'missing ' + required[i] });
    }

    var sheet = SpreadsheetApp.getActive().getSheetByName(SHEET_NAME);
    if (!sheet) return json({ ok: false, error: 'sheet ' + SHEET_NAME + ' not found' });

    // login_at / logout_at arrive as ISO 8601 UTC, e.g. 2026-09-02T00:41:07Z
    var loginAt  = new Date(body.login_at);
    var logoutAt = body.logout_at ? new Date(body.logout_at) : null;
    var rowKey   = body.staff_id + '|' + body.login_at;

    // ── idempotency: same staff + same login instant is the same session ──
    var existing = findRowByKey(sheet, rowKey);

    var row = [
      body.staff_id,
      body.name || '',
      dateOnly(loginAt),                       // C  date
      timeOnly(loginAt),                       // D  login_at
      logoutAt ? timeOnly(logoutAt) : '',      // E  logout_at
      '',                                      // F  duration_min  (formula)
      '',                                      // G  duration_hhmm (formula)
      body.site || '',
      body.ssid || '',
      body.bssid || '',
      num(body.lat),
      num(body.lng),
      num(body.accuracy_m),
      body.method || 'AUTO',
      flagFor(body, logoutAt),
      body.device_id || '',
      rowKey
    ];

    var rowIndex;
    if (existing) {
      // A retry, or the logout half of a session already opened. Update in place.
      rowIndex = existing;
      sheet.getRange(rowIndex, 1, 1, row.length).setValues([row]);
    } else {
      rowIndex = sheet.getLastRow() + 1;
      sheet.getRange(rowIndex, 1, 1, row.length).setValues([row]);
    }

    // (Re)apply the duration formulas for this row
    sheet.getRange(rowIndex, 6).setFormula(
      '=IF(OR(D' + rowIndex + '="",E' + rowIndex + '=""),"",ROUND((E' + rowIndex + '-D' + rowIndex + ')*1440,0))');
    sheet.getRange(rowIndex, 7).setFormula(
      '=IF(F' + rowIndex + '="","",TEXT(F' + rowIndex + '/1440,"[h]:mm"))');

    sheet.getRange(rowIndex, 3).setNumberFormat('yyyy-mm-dd');
    sheet.getRange(rowIndex, 4, 1, 2).setNumberFormat('hh:mm:ss');

    return json({ ok: true, row: rowIndex, updated: !!existing, row_key: rowKey });

  } catch (err) {
    return json({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

/** Health check — open the /exec URL in a browser to confirm the deployment. */
function doGet() {
  return json({ ok: true, service: 'perimeter-attendance', sheet: SHEET_NAME });
}

// ── helpers ──────────────────────────────────────────────────────────────

function findRowByKey(sheet, rowKey) {
  var last = sheet.getLastRow();
  if (last < 2) return null;
  var keys = sheet.getRange(2, KEY_COLUMN, last - 1, 1).getValues();
  for (var i = 0; i < keys.length; i++) {
    if (keys[i][0] === rowKey) return i + 2;
  }
  return null;
}

/** Flag rules — keep these in one place so app and sheet agree. */
function flagFor(body, logoutAt) {
  if (body.flag) return body.flag;              // server may override
  if (!logoutAt) return 'OPEN';                 // session never closed
  if (Number(body.accuracy_m) > 30) return 'LOW GPS';
  if (body.bssid_match === false) return 'BSSID MISMATCH';
  return 'OK';
}

function dateOnly(d) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/** Excel/Sheets stores a bare time as a fraction of a day. */
function timeOnly(d) {
  return (d.getHours() * 3600 + d.getMinutes() * 60 + d.getSeconds()) / 86400;
}

function num(v) {
  return (v === null || v === undefined || v === '') ? '' : Number(v);
}

function json(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/*
EXAMPLE REQUEST (from your server, not the phone)

POST https://script.google.com/macros/s/AKfy.../exec
Content-Type: application/json

{
  "secret":      "CHANGE-ME-32-RANDOM-CHARS",
  "staff_id":    "EMP-0412",
  "name":        "Nadia Rahman",
  "login_at":    "2026-09-02T00:41:07Z",
  "logout_at":   "2026-09-02T06:26:19Z",
  "site":        "HQ-AMPANG",
  "ssid":        "CORP-STAFF",
  "bssid":       "3c:07:54:aa:1d:02",
  "bssid_match": true,
  "lat":         3.15780,
  "lng":         101.71170,
  "accuracy_m":  8,
  "method":      "AUTO",
  "device_id":   "iPhone-a91f"
}

RESPONSE
{ "ok": true, "row": 215, "updated": false, "row_key": "EMP-0412|2026-09-02T00:41:07Z" }

Note on timezones: login_at/logout_at are sent in UTC and written using the
script's timezone (File > Project settings > Time zone). Set that to
Asia/Kuala_Lumpur so the date and time columns read as local clock time.
*/
