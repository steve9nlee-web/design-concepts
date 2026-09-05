/**
 * Trip Logger — Google Apps Script Web App.
 *
 * Receives JSON from the Trip Logger Android app and appends a row to the
 * spreadsheet below. Deploy as a Web App (execute as Me, access: Anyone),
 * then paste the /exec URL into the app's Settings.
 */
var SPREADSHEET_ID = '1e12ex5iKQJEY2OMdDjZc3IF8pH2gZvKLAzKfdZ6ptek';
var SHEET_GID = 0; // first tab

var HEADERS = ['Date', 'Time', 'Trip Time', 'Company', 'Driver', 'Description'];

function getSheet_() {
  var ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  var sheets = ss.getSheets();
  for (var i = 0; i < sheets.length; i++) {
    if (sheets[i].getSheetId() === SHEET_GID) return sheets[i];
  }
  return sheets[0];
}

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var sheet = getSheet_();

    // Write the header row once, if the sheet is empty.
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(HEADERS);
      sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
    }

    sheet.appendRow([
      data.date || '',
      data.time || '',
      data.tripTime || '',
      data.company || '',
      data.driver || '',
      data.description || ''
    ]);

    return ContentService.createTextOutput(JSON.stringify({ ok: true }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: String(err) }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

// Visiting the URL in a browser shows a simple health check.
function doGet() {
  return ContentService.createTextOutput('Trip Logger web app is running.');
}
