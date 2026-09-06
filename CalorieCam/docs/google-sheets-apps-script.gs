/**
 * CalorieCam → Google Sheets bridge.
 *
 * Paste this into a Google Apps Script project bound to a Google Sheet
 * (see docs/google-sheets-setup.md), then deploy it as a Web App.
 * The CalorieCam Android app POSTs one JSON object per saved meal and
 * this script appends it as a row.
 */
function doPost(e) {
  var data = JSON.parse(e.postData.contents);
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('FoodLog')
    || SpreadsheetApp.getActiveSpreadsheet().insertSheet('FoodLog');

  // Write the header row once.
  if (sheet.getLastRow() === 0) {
    sheet.appendRow([
      'Name', 'Height (cm)', 'Weight (kg)', 'Target weight (kg)',
      'Date', 'Time', 'Food', 'Calories (kcal)',
      'Protein (g)', 'Carbs (g)', 'Fat (g)', 'Notes', 'Daily target (kcal)'
    ]);
    sheet.getRange(1, 1, 1, 13).setFontWeight('bold');
    sheet.setFrozenRows(1);
  }

  sheet.appendRow([
    data.name, data.height_cm, data.weight_kg, data.target_weight_kg,
    data.date, data.time, data.food, data.calories,
    data.protein_g, data.carbs_g, data.fat_g, data.notes, data.daily_target_kcal
  ]);

  return ContentService
    .createTextOutput(JSON.stringify({ status: 'ok' }))
    .setMimeType(ContentService.MimeType.JSON);
}

/** Open the Web App URL in a browser to check it is deployed. */
function doGet() {
  return ContentService
    .createTextOutput(JSON.stringify({ status: 'CalorieCam endpoint is live' }))
    .setMimeType(ContentService.MimeType.JSON);
}
