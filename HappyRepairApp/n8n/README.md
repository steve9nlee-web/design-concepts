# HappyRepair — Google Drive + n8n flow

`happyrepair-drive-flow.json` is an importable n8n workflow that turns the
app's Excel export into automation:

```
Admin app ──"Export & share to Drive…"──▶ Google Drive folder
                                              │  (n8n watches it, checks every minute)
                                              ▼
                                     Download the .xlsx
                                              │
              ┌───────────────────────────────┼───────────────────────────┐
              ▼                               ▼                           ▼
   Sync all 7 categories into a     Low-stock check on the      Service-due check on the
   master Google Sheet (append       Inventory sheet → email     Reminders sheet → email
   or update, matched by ID)         alert if qty ≤ reorder      list of customers due
                                     level                       within 7 days
```

The Mechanic app closes the loop from the other side: its **Import from
Excel file…** button opens the Android file picker, where Google Drive
appears as a location — so a mechanic can load the admin's latest export
without cables or copying.

## One-time setup (about 15 minutes)

### 1. In Google Drive
- Create a folder for exports, e.g. **HappyRepair Exports**.
- Open it and copy the folder ID from the URL
  (`https://drive.google.com/drive/folders/`**`<THIS_PART>`**).

### 2. Create the master Google Sheet
Create a spreadsheet (e.g. **HappyRepair Master**) with 7 tabs, named
exactly, each with these header cells in row 1:

| Tab | Row-1 headers (in order) |
|---|---|
| `Customers` | `Date/Time, ID, name, phone, email, address` |
| `Vehicles` | `Date/Time, ID, plate, make, model, year, color, customer` |
| `Quotations` | `Date/Time, ID, quoteNo, customer, plate, status, validUntil, tax, notes, total` |
| `Job Orders` | `Date/Time, ID, jobNo, customer, plate, priority, status, mechanic, services, notes` |
| `Invoices` | `Date/Time, ID, invoiceNo, customer, plate, vehicle, amount, status` |
| `Inventory` | `Date/Time, ID, name, category, sku, qty, unit, cost, sell, reorderLevel` |
| `Reminders` | `Date/Time, ID, customer, phone, plate, dueDate, lastService, status` |

Copy the spreadsheet ID from its URL
(`https://docs.google.com/spreadsheets/d/`**`<THIS_PART>`**`/edit`).

Rows are matched by the `ID` column, so re-uploading an export **updates**
existing rows instead of duplicating them, and the master sheet keeps rows
that were deleted on the tablet — it's a running history.

### 3. In n8n
1. **Workflows → Import from File** → pick `happyrepair-drive-flow.json`.
2. Open each node with a ⚠ and attach credentials:
   - The two **Watch folder** triggers + **Download workbook** → a
     *Google Drive OAuth2* credential.
   - The seven **Sync …** nodes → a *Google Sheets OAuth2* credential.
   - The two **Email …** nodes → a *Gmail OAuth2* credential (or swap them
     for a plain *Send Email*/SMTP, WhatsApp, or Telegram node).
3. Replace the three placeholders:
   - `REPLACE_WITH_DRIVE_FOLDER_ID` — in both Watch-folder triggers.
   - `REPLACE_WITH_MASTER_SHEET_ID` — in all seven Sync nodes.
   - `REPLACE_WITH_YOUR_EMAIL` — in both Email nodes (where alerts go).
4. **Activate** the workflow.

### 4. Daily use
On the tablet: **Dashboard → "☁ Export & share to Drive…" → Google Drive →
pick the HappyRepair Exports folder → Save.** Within a minute n8n syncs the
master sheet and sends any alerts. That's the whole routine.

## Notes & limits
- The flow is **one-way**: tablet → Drive → Sheet/alerts. n8n never writes
  back into the tablet apps; to move data the other way, use the app's
  Import button on an .xlsx.
- Alert emails only go out when there is something to report (the Code
  nodes output nothing otherwise, which stops the branch).
- Uploading with the **same filename** each time and choosing Drive's
  "Replace/keep as separate file → Replace" makes the *updated file*
  trigger fire; saving as a new file fires the *new file* trigger. Both
  paths are wired, so either habit works.
- The service-due window is 7 days — edit the `7` in the **Find services
  due soon** Code node to change it.
- Node versions target current n8n (mid-2026); if your n8n is much older,
  the import may ask to upgrade node versions — accept it.
