# SA Vault (info-keeper)

A small single-file web app for saving personal reference info behind a
voice-activated login.

## Features

- **Voice passphrase login** — on first run you record (or type) a spoken
  passphrase; afterwards you unlock by tapping the mic and saying it.
  Uses the browser's Web Speech API, with a typed fallback for browsers
  that don't support it. Only a hash of the passphrase is stored.
- **Dropdown rows** — each row is a type dropdown (Email, Website, Phone,
  Username, Address, Note) plus a value field.
- **Add / remove rows** — "+ Add row" appends another row; × removes one.
- **Save** — stores your rows in the browser's localStorage on your device.
  Nothing is uploaded anywhere.

## Run it

Open `index.html` in a browser (Chrome/Edge recommended for voice input —
the Web Speech API needs a secure context, so serve over HTTPS or
localhost for the mic to work, e.g. `python3 -m http.server`).

## Note on security

The voice login matches the *words* of your passphrase, not your
voiceprint — anyone who knows the phrase can unlock it. Treat it as a
convenience lock, not real security.
