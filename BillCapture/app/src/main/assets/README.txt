Place your Google Cloud SERVICE ACCOUNT key file in this folder and name it:

    google_credentials.json

(Google Cloud Console > APIs & Services > Credentials > Service Account >
Keys > Add Key > JSON — see Part 2, Step 3 of SETUP_AND_USAGE_GUIDE.md.)

Without this file the app still runs, but OCR extraction is skipped and you
type the bill fields in manually.

Do NOT commit google_credentials.json to source control.
