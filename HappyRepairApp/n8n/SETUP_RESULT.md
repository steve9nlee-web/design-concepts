# n8n live-sync setup result (retry 3)

**Status: BLOCKED**

Date: 2026-09-10 (UTC)

## What happened

Retry 3 hit the exact same network block as retries 1 and 2. The very first
call — connectivity check `GET /api/v1/workflows?limit=20` against
`https://n8n.srv978288.hstgr.cloud` — was rejected by the session's egress
proxy with a CONNECT 403 before ever reaching the n8n server. The
agent-proxy status endpoint confirms: `connect_rejected — "gateway answered
403 to CONNECT (policy denial or upstream failure)"` for host
`n8n.srv978288.hstgr.cloud:443`. This is a network-policy denial at the
cloud-environment level, not an n8n error.

Because connectivity failed, none of the subsequent steps could run:

- Workflow `QQQPHLlnRh1gPSMG` ("HappyRepair — Live sync API") was NOT
  inspected or modified — it remains unpublished with no Google Sheets
  credential attached to its 8 Sheets nodes.
- The Google Sheets credential used by "HappyRepair — Drive sync & alerts"
  was NOT looked up or attached.
- The SYNC_KEY was NOT written into the Auth nodes.
- The Master sheet header fixes (adding `deleted` columns, renaming
  Job Orders J1 `notes` → `department`) were NOT applied.
- No end-to-end webhook tests were run.

## What the user needs to do

**Allow the n8n domain in the cloud environment's network access.** This is
the only blocker; every other input is ready. On claude.ai/code:

1. Open the environment selector above the message box.
2. Hover the cloud environment named **"Useful Project"** and click the
   gear icon.
3. Go to **Network access** → choose **Custom** → add the domain
   `n8n.srv978288.hstgr.cloud` → save.
4. Re-run this setup task. All context is in `HappyRepairApp/n8n/`
   (workflow template: `happyrepair-live-api.json`).

### Alternative: manual setup in the n8n UI

If you prefer to finish it by hand instead:

- Open workflow **"HappyRepair — Live sync API"** (id `QQQPHLlnRh1gPSMG`).
  - In both Auth code nodes, set the expected key to the SYNC_KEY already
    installed on the tablets (replace any `CHANGE_ME_SYNC_KEY` placeholder).
  - Attach your existing Google Sheets credential (the one the working
    "HappyRepair — Drive sync & alerts" workflow uses) to all 8 Google
    Sheets nodes.
  - Publish/activate the workflow.
- Fix row 1 of the Master sheet
  (ID `1eV14PZdB8fyYpxTWMsEpsIefalyHGZkfFyMUvKIWOW8`):
  - `Customers!G1` = `deleted`
  - `Vehicles!I1` = `deleted`
  - `Quotations!K1` = `deleted`
  - `'Job Orders'!J1` = `department` (replacing the old `notes` header)
  - `'Job Orders'!K1` = `deleted`
  - `Invoices!I1` = `deleted`
  - `Inventory!K1` = `deleted`
  - `Reminders!K1` = `deleted`
- Test: `GET https://n8n.srv978288.hstgr.cloud/webhook/happyrepair-data`
  with header `x-happy-key: <your SYNC_KEY>` should return 200 with 7
  arrays; without the header it should return 401.

## Test results (step 6)

Not run — blocked at the network layer before reaching the n8n API.

| Test | Result |
| --- | --- |
| GET /webhook/happyrepair-data with key → 200 + 7 arrays | not run |
| GET /webhook/happyrepair-data without key → 401 | not run |
| POST /webhook/happyrepair-save (JO-TEST9) → ok:true | not run |
| JO-TEST9 visible in data GET with department=Repairing | not run |
| Tombstone (deleted:"yes") accepted | not run |
