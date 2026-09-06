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
3. Set — both settings matter:
   - **Execute as:** Me (your account)
   - **Who has access:** **Anyone** ⚠️ — NOT "Anyone with a Google account"
     and NOT "Only myself". If it isn't "Anyone", the app gets a Google
     sign-in page instead of the script and nothing is written.
4. Click **Deploy**, approve the permissions prompt
   (it only writes to this one spreadsheet), and copy the
   **Web app URL** — it must end in **`/exec`** and look like
   `https://script.google.com/macros/s/AKfy.../exec`.

> "Anyone" is required so the app can post without Google sign-in. The URL
> is long and unguessable; anyone who somehow had it could only append rows
> to this one sheet, never read your Drive.

## 4. Paste the URL into the app

1. Open CalorieCam → menu (⋮) → **Settings**.
2. Paste the Web app URL into **Google Sheet sync URL** and save.

Done. Each saved meal now appears in your sheet with these columns:

| Name | Height (cm) | Weight (kg) | Target weight (kg) | Date | Time | Food | Calories (kcal) | Protein (g) | Carbs (g) | Fat (g) | Notes | Daily target (kcal) |
|------|-------------|-------------|--------------------|------|------|------|-----------------|-------------|-----------|---------|-------|---------------------|

If your phone is offline when you save a meal, the row is kept locally and
synced automatically the next time you open the app.

## Troubleshooting: the sheet doesn't update

First use **Settings → Test sheet connection** in the app — it sends a test
row and tells you exactly what's wrong. The usual causes:

1. **"Who has access" is not "Anyone".** Go to Apps Script →
   **Deploy → Manage deployments → ✏️ Edit**, change access to **Anyone**,
   and deploy. (Choosing "Anyone with a Google account" or "Only myself"
   makes Google demand a sign-in the app can't perform.)

2. **Wrong URL.** The URL in the app's Settings must be the **Web app URL
   ending in `/exec`** — not the `/dev` URL, not the script editor URL, and
   not the spreadsheet's own URL.

3. **You edited the script but didn't redeploy.** Code changes only go live
   when you create a **New deployment** (or edit the deployment and pick
   **New version**). Saving the script alone is not enough — and a new
   deployment gets a **new URL**, so re-paste it into the app.

4. **The script was never authorized.** In the Apps Script editor, select
   the `doGet` function and press **Run** once; approve the permission
   prompt. Then try again.

5. **Quick sanity check:** open your `/exec` URL in a normal browser tab.
   You should see `{"status":"CalorieCam endpoint is live"}`. If you see a
   Google sign-in page instead, it's cause #1; if an error page, causes
   #2–#4.

Rows saved while sync was broken are still on your phone (marked ⏳ in
History) and are pushed automatically the next time you open the app after
fixing the connection.
