# Vehicle Records

An Android app for keeping track of all your motorcars, motorcycles and vans in one place.

## What it records per vehicle

1. **Plate No**
2. **Make**
3. **Model**
4. **Color**
5. **Type** — Car | Motorcycle | Van
6. **Renewal dates and costs** for:
   - Road Tax
   - Insurance
   - Inspection

Each vehicle card on the home screen shows all three renewals with a color-coded countdown:

- 🟢 Green — OK (more than 60 days left)
- 🟡 Yellow — 60 days or less remaining
- 🔴 Red — due today or overdue

## Reminders

The app checks all renewals once a day (around 9am) and sends a phone
notification whenever anything is due, overdue, or within 60 days —
listing each vehicle, the item (road tax / insurance / inspection) and
days remaining. On Android 13+ the app asks for notification permission
on first launch; allow it to receive reminders.

## Using the app

- Tap **+** to add a vehicle.
- Tap a vehicle card to edit it.
- Long-press a card (or use the Delete button in the edit screen) to delete a vehicle.
- Tap a renewal date field to pick the date from a calendar; enter the cost next to it.

All data is stored locally on your phone — no internet access needed and nothing leaves your device.

## Building the APK

With the Android SDK installed:

```bash
cd VehicleRecords
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or push a change under `VehicleRecords/` and the **Build VehicleRecords APK** GitHub Actions workflow builds it and uploads `app-debug.apk` as a downloadable artifact.

## Installing

Copy `app-debug.apk` to your phone and open it. Android will ask you to allow installs from unknown sources the first time. Requires Android 7.0 (API 24) or newer.
