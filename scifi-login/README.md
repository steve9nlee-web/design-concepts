# Quantum Access Node — Sci-Fi HUD Login

A futuristic "quantum security" login concept inspired by a sci-fi HUD interface
reference video. Built as a single self-contained `index.html` (HTML + CSS + JS,
no build step, no dependencies beyond one Google Font), so it drops into any of
the three common delivery formats.

## The concept

A three-stage authentication flow:

1. **ACCESS NODE** — a dark glass access card framed by rotating orbital arcs
   (teal + magenta) over a drifting starfield. Fields: `IDENTIFIER`
   (placeholder `NODE_XXXX`) and `SECURITY TOKEN`, with an
   `INITIALIZE CONNECTION` action.
2. **ANALYZING QUANTUM SIGNATURE** — on submit the card yields to a radar-ring
   scanner: pulsing concentric rings, a sweeping arc, a glowing hex core, a
   live percentage + progress bar, and terminal-style log lines
   (`> ENCRYPTION LATTICE VERIFIED`, …).
3. **ACCESS GRANTED** — a glowing confirmation with the operator's identifier
   and a `TERMINATE SESSION` reset.

### Design tokens

| Token | Value | Role |
|---|---|---|
| Ground | `#04090c` | cyan-biased near-black backdrop |
| Panel | `rgba(10,20,26,.88)` | glass access card |
| Accent | `#3be0cd` | quantum teal — arcs, button, glow |
| Warning | `#e14b6a` | magenta counter-arc, errors |
| Dim text | `#7d9aa3` | HUD labels |
| Type | Share Tech Mono | all HUD text, wide letter-spacing |

## Use it in each format

**Website** — serve `index.html` as-is (GitHub Pages, Netlify, any static
host). It is fully responsive and works on mobile viewports.

**Android APK** — wrap in a `WebView`:

```kotlin
// MainActivity.kt — minimal WebView wrapper
webView.settings.javaScriptEnabled = true
webView.loadUrl("file:///android_asset/index.html")
```

Copy `index.html` into `app/src/main/assets/`. (For offline use, also inline
the font or rely on the monospace fallback stack that's already declared.)

**Desktop program** — load it in Electron (`win.loadFile("index.html")`) or
Tauri, or simply open it in a browser.

## Wiring real authentication

The page ships in **demo mode**: any non-empty credentials pass. The submit
handler in `index.html` marks the integration point — replace the direct
`runScan()` call with a `fetch()` to your auth endpoint, keep the scanner
running while the request is in flight, and call `showGranted()` on success
(the scan animation doubles as a natural loading state).

## Accessibility

- Honors `prefers-reduced-motion` (orbits nearly still, scan shortened).
- Visible keyboard focus states on inputs and buttons.
- Error and scanner regions announced via `role="alert"` / `aria-live`.
