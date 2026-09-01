# mileage-backend (Google Apps Script)

The server side of the mileage-claim system. It lives inside a Google Sheet
in the admin's Google Drive:

- **`doPost`** — receives claims from the staff app
  ([`../MileageClaim/`](../MileageClaim/)) and appends one row per claim to
  the `Claims` sheet.
- **`doGet ?action=list`** — returns all claims as JSON for the admin app
  ([`../MileageAdmin/`](../MileageAdmin/)).
- After every claim, the sheet is re-exported as **`Mileage_Claims.xlsx`**
  in Google Drive (the same file is updated each time, so its link is stable).

Both endpoints require the shared secret, so only the two apps can read or
write claims.

## Deployment (one-time, ~5 minutes, admin's Google account)

1. Go to [sheets.new](https://sheets.new) and create a spreadsheet, e.g.
   **Mileage Claims**. It can live in any Drive folder.
2. **Extensions → Apps Script**. Delete the placeholder code and paste the
   whole of `Code.gs`.
3. Edit the first constant: set `SHARED_SECRET` to a long random string.
   This is the "password" you will type into both apps.
4. Enable the Drive advanced service (needed to keep the xlsx file updated
   in place): in the left sidebar click **Services +**, choose
   **Drive API**, and add it.
5. Run the `setup` function once (toolbar: select `setup`, press **Run**)
   and approve the authorization prompts. The log prints the id of the
   created `Mileage_Claims.xlsx`.
6. **Deploy → New deployment → Web app**:
   - Description: anything
   - Execute as: **Me**
   - Who has access: **Anyone** (access is still gated by the shared secret;
     "Anyone" is required so staff phones can POST without Google sign-in)
7. Copy the **Web app URL** (ends in `/exec`).

Give staff and the admin two values for the apps' Settings screens:
the web app URL and the `SHARED_SECRET` value.

## Where the data lives

- **Google Sheet `Mileage Claims`** — system of record, one row per claim:
  Submitted At, Claim Date, Claim Time, Staff Name, Destination, Purpose,
  Distance (km).
- **`Mileage_Claims.xlsx`** — Excel copy in Drive, refreshed after every
  claim. The admin app's *Open Excel file in Google Drive* button opens it.

## Updating the script

Paste the new `Code.gs`, then **Deploy → Manage deployments → edit →
New version → Deploy**. The URL stays the same. (Creating a *new* deployment
instead would change the URL and every phone would need reconfiguring.)

## Notes

- If the xlsx export ever fails (e.g. Drive service not enabled), the claim
  row is still saved; the export catches up on the next claim.
- To rotate the secret, change `SHARED_SECRET`, redeploy a new version, and
  update both apps' Settings.
