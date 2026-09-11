/**
 * Mileage Claims backend — Google Apps Script web app.
 *
 * Bound to a Google Sheet. Staff phones (MileageClaim app) POST claims here;
 * the admin phone (MileageAdmin app) GETs the full list. After every claim
 * the sheet is re-exported as Mileage_Claims.xlsx in Google Drive.
 *
 * Deployment steps are in README.md next to this file.
 */

// Change this before deploying, and put the same value in both apps' Settings.
const SHARED_SECRET = 'change-me-to-a-long-random-string';

const SHEET_NAME = 'Claims';
const XLSX_NAME = 'Mileage_Claims.xlsx';
const HEADERS = [
  'Submitted At', 'Claim Date', 'Claim Time', 'Staff Name',
  'Destination', 'Purpose', 'Distance (km)',
];

/** Staff app: submit one claim. Body: {token, date, time, staff, destination, purpose, km} */
function doPost(e) {
  let body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return jsonOut_({ ok: false, error: 'Invalid JSON body' });
  }
  if (body.token !== SHARED_SECRET) {
    return jsonOut_({ ok: false, error: 'Invalid token' });
  }
  const km = Number(body.km);
  if (!body.date || !body.time || !body.staff || !body.destination ||
      !body.purpose || !(km > 0)) {
    return jsonOut_({ ok: false, error: 'Missing or invalid claim fields' });
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const sheet = claimsSheet_();
    sheet.appendRow([
      new Date(),
      String(body.date),
      String(body.time),
      String(body.staff),
      String(body.destination),
      String(body.purpose),
      km,
    ]);
    SpreadsheetApp.flush();
    // Best effort: the claim row is already saved; the xlsx catches up on the
    // next claim if the export fails (e.g. Drive service not enabled yet).
    try {
      refreshXlsx_();
    } catch (err) {
      console.error('xlsx export failed: ' + err);
    }
  } finally {
    lock.releaseLock();
  }
  return jsonOut_({ ok: true });
}

/** Admin app: ?action=list lists claims; ?action=ping tests the connection. */
function doGet(e) {
  const params = (e && e.parameter) || {};
  if (params.token !== SHARED_SECRET) {
    return jsonOut_({ ok: false, error: 'Invalid token' });
  }
  if (params.action === 'ping') {
    return jsonOut_({ ok: true });
  }
  if (params.action !== 'list') {
    return jsonOut_({ ok: false, error: 'Unknown action' });
  }

  const sheet = claimsSheet_();
  const lastRow = sheet.getLastRow();
  const claims = [];
  let totalKm = 0;
  if (lastRow > 1) {
    const tz = Session.getScriptTimeZone();
    const rows = sheet.getRange(2, 1, lastRow - 1, HEADERS.length).getValues();
    rows.forEach(function (row) {
      const km = Number(row[6]) || 0;
      totalKm += km;
      claims.push({
        submittedAt: row[0] instanceof Date
          ? Utilities.formatDate(row[0], tz, 'yyyy-MM-dd HH:mm')
          : String(row[0]),
        date: formatCell_(row[1], tz, 'yyyy-MM-dd'),
        time: formatCell_(row[2], tz, 'HH:mm'),
        staff: String(row[3]),
        destination: String(row[4]),
        purpose: String(row[5]),
        km: km,
      });
    });
    claims.reverse(); // newest first
  }

  const xlsxId = PropertiesService.getScriptProperties().getProperty('XLSX_FILE_ID');
  return jsonOut_({
    ok: true,
    count: claims.length,
    totalKm: totalKm,
    claims: claims,
    xlsxUrl: xlsxId ? 'https://drive.google.com/file/d/' + xlsxId + '/view' : '',
  });
}

/** Returns the Claims sheet, creating it with headers on first use. */
function claimsSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
  }
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
    sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
    sheet.setFrozenRows(1);
  }
  return sheet;
}

/**
 * Exports the spreadsheet as xlsx into Drive, updating the same file every
 * time (stable link for the admin app). Requires the "Drive API" advanced
 * service to be enabled in the Apps Script editor (Services > Drive API).
 */
function refreshXlsx_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const exportUrl = 'https://docs.google.com/spreadsheets/d/' + ss.getId() +
    '/export?format=xlsx';
  const blob = UrlFetchApp.fetch(exportUrl, {
    headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() },
  }).getBlob().setName(XLSX_NAME);

  const props = PropertiesService.getScriptProperties();
  const existingId = props.getProperty('XLSX_FILE_ID');
  if (existingId) {
    Drive.Files.update({}, existingId, blob, { supportsAllDrives: true });
  } else {
    const created = Drive.Files.create({ name: XLSX_NAME }, blob, {
      supportsAllDrives: true,
    });
    props.setProperty('XLSX_FILE_ID', created.id);
  }
}

/** Run this once from the editor to authorize scopes and create the xlsx. */
function setup() {
  claimsSheet_();
  refreshXlsx_();
  console.log('Setup complete. xlsx file id: ' +
    PropertiesService.getScriptProperties().getProperty('XLSX_FILE_ID'));
}

function formatCell_(value, tz, pattern) {
  return value instanceof Date
    ? Utilities.formatDate(value, tz, pattern)
    : String(value);
}

function jsonOut_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
