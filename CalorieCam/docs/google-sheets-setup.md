# Connect CalorieCam to a Google Sheet on your Drive

Every meal you save in the app is appended **row by row** to a Google Sheet
stored in your Google Drive. (Google Sheets opens and exports as Excel —
in the sheet use **File → Download → Microsoft Excel (.xlsx)** any time.)

One-time setup, about 5 minutes:

## 1. Create the sheet

1. Go to [sheets.google.com](https://sheets.google.com) and create a new
   blank spreadsheet. Name it e.g. **CalorieCam Log**.

## 2. Add the bridge script

1. In the spreadsheet, open **Extensions → Apps Script**.
2. Delete any code in the editor and paste the full contents of
   [`google-sheets-apps-script.gs`](google-sheets-apps-script.gs).
3. Press **Save** (💾).

## 3. Deploy it as a Web App

1. Click **Deploy → New deployment**.
2. Click the gear icon next to "Select type" and choose **Web app**.
3. Set:
   - **Execute as:** Me (your account)
   - **Who has access:** Anyone with the link
4. Click **Deploy**, approve the permissions prompt
   (it only writes to this one spreadsheet), and copy the
   **Web app URL** (it looks like
   `https://script.google.com/macros/s/AKfy.../exec`).

> "Anyone with the link" is required so the app can post without Google
> sign-in. The URL is long and unguessable; anyone who somehow had it could
> only append rows to this one sheet, never read your Drive.

## 4. Paste the URL into the app

1. Open CalorieCam → menu (⋮) → **Settings**.
2. Paste the Web app URL into **Google Sheet sync URL** and save.

Done. Each saved meal now appears in your sheet with these columns:

| Name | Height (cm) | Weight (kg) | Target weight (kg) | Date | Time | Food | Calories (kcal) | Protein (g) | Carbs (g) | Fat (g) | Notes | Daily target (kcal) |
|------|-------------|-------------|--------------------|------|------|------|-----------------|-------------|-----------|---------|-------|---------------------|

If your phone is offline when you save a meal, the row is kept locally and
synced automatically the next time you open the app.
