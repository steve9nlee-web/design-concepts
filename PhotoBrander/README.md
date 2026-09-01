# PhotoBrander

Android app + n8n workflow that turns a phone photo into a branded marketing image:

1. **Upload** a photo from your phone (gallery photo picker or camera).
2. **Describe** the image you want (wording, style, changes).
3. The app sends the photo, description, and your **company logo + details**
   (set up once in the app's Settings) to an **n8n webhook**.
4. The n8n workflow calls **Gemini's image model** to generate a new photo with
   your described wording and automatically places your company logo and details
   on the image.
5. The finished image is sent **back to the same app**, where you can preview it
   and save it to your gallery.

```
Phone app ──(photo + description + logo + company details, multipart POST)──▶ n8n Webhook
                                                                                  │
                                                                     Build Gemini request (Code)
                                                                                  │
                                                                     Gemini image generation (HTTP)
                                                                                  │
                                                                     Extract image (Code)
                                                                                  │
Phone app ◀──────────────(generated PNG, webhook response)────────── Respond to Webhook
```

## Repository layout

- `app/` — Android app source (Kotlin, min Android 8.0 / API 26)
- `n8n/brand-photo-workflow.json` — importable n8n workflow
- `../.github/workflows/build-photobrander-apk.yml` — CI that builds the APK

## Getting the APK

The GitHub Actions workflow **Build PhotoBrander APK** runs on every push that
touches `PhotoBrander/` (and can be run manually from the Actions tab). When it
finishes, download the `PhotoBrander-debug-apk` artifact, copy `app-debug.apk`
to your phone, and install it (you may need to allow "install unknown apps").

To build locally instead, open `PhotoBrander/` in Android Studio, or run:

```bash
gradle -p PhotoBrander :app:assembleDebug
# APK at PhotoBrander/app/build/outputs/apk/debug/app-debug.apk
```

## Setting up n8n

1. In n8n, **Import from File** → choose `n8n/brand-photo-workflow.json`.
2. Set a **Gemini API key** (from [Google AI Studio](https://aistudio.google.com/apikey))
   as the environment variable `GEMINI_API_KEY` on your n8n instance
   (e.g. `docker run -e GEMINI_API_KEY=... n8nio/n8n`). Alternatively, edit the
   "Gemini Image Generation" node and paste the key directly into the
   `x-goog-api-key` header.
3. **Activate** the workflow, then copy the **Production URL** of the
   "Webhook (from app)" node — it ends in `/webhook/brand-photo`.

Notes:

- Your n8n instance must be reachable from the phone (public URL or same network).
- The workflow responds synchronously with the generated PNG; generation
  typically takes 10–60 seconds, and the app waits up to 5 minutes.
- The webhook accepts `multipart/form-data` with fields `photo` (file),
  `logo` (file, optional), `description`, `company_name`, `company_details`,
  so you can also test it with `curl -F`.

## Setting up the app

1. Install and open the app, then open **⋮ → Settings**.
2. Paste the n8n **webhook production URL**.
3. Enter your **company name** and **company details** (phone, website, address…).
4. Pick your **company logo** from your phone. Settings are stored on-device and
   automatically attached to every generation request.
5. Back on the main screen: choose or take a photo, type a description, and tap
   **Generate branded photo**. When the result appears, tap **Save to gallery**
   (saved under `Pictures/PhotoBrander`).

## Customizing

- **Different AI provider:** replace the "Gemini Image Generation" HTTP node
  (and the request-building Code node) with your provider of choice — e.g.
  OpenAI `images/edits`. Keep the final "Respond to Webhook" node responding
  with binary image data and the app needs no changes.
- **Logo/text placement:** the placement instructions live in the prompt built
  by the "Build Gemini Request" Code node — edit the prompt text there.
- **Async processing:** for very long generations, switch the webhook to
  respond immediately and deliver results via a second endpoint the app polls;
  the current synchronous design keeps the app and workflow simplest.
