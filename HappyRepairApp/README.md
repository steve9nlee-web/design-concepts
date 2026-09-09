# HappyRepairApp — Admin & Mechanic APKs

Two real, signed, installable Android apps built from the HappyRepairApp
workshop-manager mock-up (javac → d8 → aapt2 → zipalign → apksigner, no
Android Studio needed).

| APK | Package | Who it's for |
|---|---|---|
| `apk/HappyRepair-Admin.apk` | `com.happyenterprise.repairapp.admin` | Owner / admin — full access |
| `apk/HappyRepair-Mechanic.apk` | `com.happyenterprise.repairapp.mechanic` | Workshop mechanics — restricted |

Both apps can be installed **side by side** on the same tablet (different
package names, different icons: navy **A** for Admin, orange **M** for
Mechanic).

## What each role can do

**Admin** (navy icon):
- Everything: Dashboard, Customers, Vehicles, Quotations, Job Orders,
  Invoices, Inventory, Reminders, Settings.
- Add, **edit**, and delete records.
- **Export all data to Excel (.xlsx)** — written straight into the tablet's
  Downloads folder via a native Android bridge, one sheet per category,
  every row timestamped. Optional auto-export after every save.

**Mechanic** (orange icon):
- Dashboard, Job Orders, Vehicles, Inventory, Reminders only.
- Can add and **edit** Job Orders (e.g. update status: Pending → In
  Progress → Ready for Pickup → Completed) and register Vehicles.
- Inventory and Reminders are **view-only**.
- No Customers/Quotations/Invoices/Settings, no delete, no Excel export.

## Installing on a tablet
1. Copy the `.apk` onto the device (USB, email, Drive, WhatsApp — any way).
2. Tap it in a file manager. If Android blocks it, go to
   **Settings → Apps → Special access → Install unknown apps** and allow it
   for the app you opened the file with, then tap the APK again.
3. Both are signed with a self-signed certificate (10-year validity), so
   Android shows "unknown developer" the first time — normal for apps
   installed outside the Play Store.

## Important: data is per-app and per-device
Each app stores its records in its **own** on-device storage (localStorage
inside the app's WebView). The Admin and Mechanic apps do **not** share a
database — even on the same tablet. If you need both roles working on the
same live data, the apps need a real backend (e.g. the Supabase setup
mentioned in the original notes); happy to wire that up as a next step.

## Rebuilding
```bash
export ANDROID_SDK=/path/to/android-sdk   # needs build-tools;34.0.0 + platforms;android-34
./build.sh
```
- `app-template.html` — single source for both apps; `__ROLE__` is baked in
  at build time (`admin` / `mechanic`) and drives the role permissions at
  the top of the script.
- `android/` — WebView wrapper (`MainActivity.java.tmpl`) with a
  `AndroidBridge.saveBase64()` JavaScript interface that writes the
  exported .xlsx into Downloads (MediaStore on Android 10+, direct file
  write below that), plus the manifest template.
- `xlsx.full.min.js` — SheetJS bundled into the APK so Excel export works
  fully offline.
- `happyrepair.keystore` — signing key (store/key password
  `happyrepair123`, alias `happyrepair`). Kept in the repo so future
  builds keep the same signature and **install as updates** over the old
  version instead of requiring an uninstall. Don't reuse this key for
  anything Play-Store-bound.
- `make_icon.py` — generates the launcher icons (needs python3 + Pillow).

Note for rebuilds on JDK 21+: keep `Bridge` a *static* nested class —
javac 21 emits a null-named synthetic-parameter attribute for inner-class
constructors that build-tools 34's d8 crashes on.
