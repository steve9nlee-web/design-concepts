# World Monitor

A local-first geopolitical/OSINT dashboard: a real-time 3D globe combined with
AI-powered news correlation, live event data, and financial markets — running
entirely on your own machine. All AI processing (summarization, cross-event
correlation, instability scoring) runs through a local [Ollama](https://ollama.com)
instance; nothing is sent to a cloud AI API. Live data feeds (news, GDELT
events, flights, earthquakes, markets) do require internet access to their
public sources.

This is an MVP. See "Roadmap" below for what's deliberately out of scope for now.

## Features

- Interactive 3D WebGL globe (`globe.gl`)
- Real-time news tracking from ~16 curated public RSS feeds, geotagged by a
  lightweight country-keyword heuristic
- AI-powered cross-event correlation: local Ollama model clusters related
  articles into "stories" and explains the connection
- Country instability index computed from GDELT 2.0's Goldstein Scale /
  Average Tone / event volume, with an AI-written rationale per country
- Live overlays: news events, flights (OpenSky Network), significant
  earthquakes (USGS), country instability
- Financial ticker: major equity indices, commodities (CoinGecko), and crypto
  (CoinGecko)
- Settings panel to pick the local Ollama model and toggle globe layers

## Prerequisites

- [Node.js](https://nodejs.org) 18+
- [Rust](https://www.rust-lang.org/tools/install) (stable toolchain)
- Platform build tools for Tauri — see the [Tauri prerequisites guide](https://tauri.app/start/prerequisites/):
  - **Windows**: Microsoft C++ Build Tools (Desktop development with C++ workload)
  - **macOS**: Xcode Command Line Tools
  - **Linux**: standard WebKitGTK/build-essential packages per your distro
- [Ollama](https://ollama.com) installed and running locally, with at least
  one model pulled, e.g.:

  ```bash
  ollama pull llama3.2
  ```

## Running

```bash
npm install
npm run tauri dev
```

The first Rust build compiles ~479 crates and can take several minutes; after
that, incremental builds are fast. `npm run tauri dev` launches the native
app window with hot-reload for the frontend.

## Building

```bash
npm run tauri build
```

Produces a platform-native installer/bundle in `src-tauri/target/release/bundle/`.
This has only been built and tested on Windows so far — the codebase avoids
Windows-specific APIs, so macOS/Linux builds should work from the same source,
but haven't been verified on those platforms yet.

## Architecture

- `src-tauri/` — Rust backend (Tauri v2). Polls news/GDELT/flights/quakes/
  markets on independent background intervals, caches everything in a local
  SQLite database (`world-monitor.sqlite3` in the app data dir), and runs the
  Ollama AI pipeline on its own throttled interval.
- `src/` — React + TypeScript frontend (Vite, Tailwind, Zustand, `globe.gl`).
  Polls the local Tauri commands (cheap local reads) to stay in sync with the
  backend's cached state.

See `src-tauri/src/feeds/` for each data source's adapter — adding a new
overlay type is a new file there plus a small layer addition in
`src/components/Globe/GlobeView.tsx`.

## Roadmap (not in this MVP)

- Full 56-overlay catalog (currently: news, flights, earthquakes, instability)
- Live AIS shipping lanes — no free/keyless global AIS source exists; would
  need a paid provider (MarineTraffic, Spire, AISHub station-sharing)
- Historical time-scrubbing / replay
- Alerting/notifications
- Packaged installers for macOS/Linux (buildable from this source, not yet
  produced/tested since development happened on Windows)
