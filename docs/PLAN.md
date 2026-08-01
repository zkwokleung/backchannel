# Backchannel — Architecture & Build Plan

## Context

A personal app to browse **your own saved YouTube channels** and **listen to videos like a
podcast** (background / audio-only) or watch them, with **watchlists**. Built on **yt-dlp**,
intended to be **open-sourced**, and kept in the low-liability "personal use" zone (no bundled
content, user-driven fetches, no hosting of any kind).

**Decisions locked:**
- **Platform:** Android only (APK / F-Droid). iOS is off the table — the embedded-Python
  approach below has no iOS equivalent and the App Store rejects yt-dlp-backed apps.
- **Architecture:** **fully self-contained — no backend.** yt-dlp runs **on the device** via
  [youtubedl-android](https://github.com/yausername/youtubedl-android) (bundled Python runtime;
  the approach proven by Seal and dvd). Because the phone resolves stream URLs itself, the
  IP-lock on googlevideo URLs is a non-issue: resolver and player are the same device, and the
  player streams the URL **directly** — no proxy anywhere.
- **Storage:** everything local — **expo-sqlite** database on the device.

**Outcome:** a single Expo app: save channels, build watchlists, and play videos as background
audio or video, resuming where you left off. yt-dlp is updatable **in-app at runtime** so
YouTube-side breakage is fixed without shipping a new APK.

## Tech Stack

**App (`app/`)** — **Expo (React Native) + TypeScript**, Android only
- **Expo Modules API (Kotlin)** — local native module `modules/ytdlp` wrapping
  **youtubedl-android**: init, runtime self-update, JSON extraction, stream resolution.
- **react-native-track-player** — background audio, lock-screen/notification controls, queue.
- **expo-video** — video playback + Picture-in-Picture.
- **expo-sqlite** — channels, cached video lists, watchlists, playback state.
- **expo-router** — navigation. **@tanstack/react-query** — async state over the native
  extractor + DB. **zustand** — player/UI state.
- Requires an **Expo dev build** (`expo-dev-client` / EAS) — track-player, expo-video, and the
  ytdlp module are native and do **not** run in Expo Go.

## Architecture

```
┌─ Expo App (TypeScript) ─────────────────────────────────────────┐
│  Screens: Channels · Channel detail · Watchlists · Now-Playing  │
│           Video player · Settings                               │
│                                                                 │
│  Data:    expo-sqlite ◄── repositories ◄── react-query          │
│  Engine:  modules/ytdlp (Kotlin, Expo Modules API)              │
│             └─► youtubedl-android ─► yt-dlp on bundled Python   │
│  Playback: track-player / expo-video ─► direct googlevideo URL  │
└─────────────────────────────────────────────────────────────────┘
```

**The core engineering piece is the on-device yt-dlp engine.** `modules/ytdlp` exposes to TS:
- `initialize()` / `updateYtdlp()` — set up the runtime; self-update yt-dlp (stable channel)
- `resolveChannel(handleOrUrl)` — channel metadata
- `listChannelVideos(channelId, limit)` — fast flat extraction (`extract_flat`) of the /videos tab
- `getVideoInfo(videoId)` — full metadata
- `resolveStream(videoId, mode)` — bestaudio (m4a) or best video: returns direct URL + HTTP
  headers for the player; short-TTL in-memory cache (URLs expire ~6h; never persisted)

All engine calls run off the main thread; results cross the bridge as JSON.

## Data Model (SQLite, on-device)

- **Channel** — `youtube_id`, `handle`, `title`, `thumbnail`, `added_at`
- **Video** (cache of channel uploads) — `youtube_id`, `channel_id`, `title`, `duration`,
  `thumbnail`, `published_at`, `cached_at`
- **Watchlist** — `name`, `created_at`; **WatchlistItem** — `watchlist_id`, `video_youtube_id`,
  `position`, `added_at`
- **PlaybackState** — `video_youtube_id`, `position_seconds`, `completed`, `updated_at`

## Key App Modules (`app/`)

- `modules/ytdlp/` — Expo native module (Kotlin) + TS typings (the engine above)
- `src/db/` — expo-sqlite schema/migrations + repositories (channels, videos, watchlists, playback)
- `src/player/` — track-player service, queue management, position reporting
- `src/api/` — react-query hooks bridging engine + repositories to screens
- `app/(tabs)/` — expo-router screens: Channels, Watchlists, Now-Playing, Settings

## Phased Execution Plan

Each phase is a small reviewable slice; verify + get approval before the next.

- **Phase 0 — Scaffold & hygiene:** repo layout, `LICENSE`, `.gitignore`, `README`, branding. *(done)*
- **Phase 1 — App scaffold:** Expo project + dev build, `expo-router` shell (4 tabs), theme,
  branding wired into `app.json`.
- **Phase 2 — yt-dlp engine (hard part):** `modules/ytdlp` native module wrapping
  youtubedl-android; TS API; runtime yt-dlp updater + auto-check.
- **Phase 3 — Local data layer:** SQLite schema + migrations, repositories, react-query wiring.
- **Phase 4 — Channels:** add channel (resolve via engine), uploads list with cache + refresh.
- **Phase 5 — Audio player:** track-player background playback, queue, lock-screen controls.
- **Phase 6 — Video player:** expo-video + PiP, audio/video mode switch.
- **Phase 7 — Watchlists + resume:** watchlist UI, position reporting + resume wiring.
- **Phase 8 — Polish:** error/empty states, network handling, updater UX, docs pass.

## Verification

**Engine (Phase 2, the linchpin):**
- On a real device/emulator: `getVideoInfo` for a known public video returns JSON; log yt-dlp
  version; run in-app update and confirm version bump.
- `resolveStream(id, 'audio')` returns a googlevideo URL that plays in the phone's player.

**App (per phase):**
- Android device/emulator via dev build (Expo Go explicitly unsupported).
- Save a channel → uploads appear (SQLite persists across restarts) → queue to a watchlist →
  play as **audio with screen off** (lock-screen controls) → reopen and **resume** at saved
  position → switch to video + PiP.

**End-to-end acceptance:** add channel → browse uploads → add to watchlist → background-listen
with screen locked → resume later → play one as video — all with airplane-mode-except-YouTube
level of self-containment (no other endpoints involved).

## Risks & Notes

- **yt-dlp breakage** when YouTube changes internals → **in-app runtime updater**
  (youtubedl-android supports updating the bundled yt-dlp without an app release) + auto-check
  on launch.
- **Stream URL expiry (~6h)** → resolve on-demand + short-TTL in-memory cache; never persist URLs.
- **APK size:** the bundled Python runtime adds ~30–60 MB. Accepted tradeoff for zero-server;
  ship per-ABI splits (arm64-v8a primary) to keep downloads lean.
- **On-device extraction latency:** flat channel listing takes a few seconds of CPU — run off
  the UI thread, show progress, cache aggressively in SQLite.
- **Queue advance in background:** resolving the next stream mid-playback must happen inside the
  playback (foreground) service context; battery-optimization exemption documented.
- **Expo Go won't work** — track-player/expo-video/ytdlp are native; dev/EAS build required
  (documented in Phase 1).
- **Keep README/marketing neutral** — "personal media player for content you're authorized to
  access," no "rip/bypass/download any video" language.
