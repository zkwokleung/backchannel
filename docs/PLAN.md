# Backchannel — Architecture & Build Plan

## Context

A personal app to browse **your own saved YouTube channels** and **listen to videos like a
podcast** (background / audio-only) or watch them, with **watchlists**. Built on **yt-dlp**,
intended to be **open-sourced**, and kept in the low-liability "personal use" zone (no bundled
content, user-driven fetches, no hosting of any kind).

**Decisions locked:**
- **Platform:** Android only (APK / F-Droid), minSdk 26.
- **Architecture:** **fully self-contained — no backend.** yt-dlp runs **on the device** via
  [youtubedl-android](https://github.com/yausername/youtubedl-android) (bundled Python runtime;
  the approach proven by Seal). The phone resolves stream URLs itself, so the IP-lock on
  googlevideo URLs is a non-issue and the player streams URLs **directly** — no proxy anywhere.
- **Stack:** **fully native Kotlin** (no React Native). Every hard component — the yt-dlp
  engine, background audio, lock-screen controls, PiP — is a native-platform concern; a bridge
  layer would add risk without capability. Media3/ExoPlayer beats any cross-platform audio lib
  for this use case, and Android-only removes RN's cross-platform value.
- **Storage:** everything local — **Room** (SQLite) on the device.

**Outcome:** a single native Android app: save channels, build watchlists, and play videos as
background audio or video, resuming where you left off. yt-dlp is updatable **in-app at
runtime** so YouTube-side breakage is fixed without shipping a new APK.

## Tech Stack (`android/`)

- **Kotlin 2.x · Jetpack Compose · Material 3 · Navigation Compose** — UI
- **youtubedl-android** — embedded Python + yt-dlp; runtime self-update (stable channel)
- **Media3** — ExoPlayer + `MediaSessionService`: background/foreground audio, notification +
  lock-screen controls, queue; PiP via activity APIs for video
- **Room + KSP** — persistence; **kotlinx.serialization** — yt-dlp JSON parsing
- **Coroutines / Flow** — async; **Coil** — thumbnails
- Single Gradle module (`:app`), manual DI via an `AppContainer` (no Hilt — keep the build lean)

## Architecture

```
┌─ android/app ────────────────────────────────────────────────────┐
│  ui/        Compose screens: Channels · ChannelDetail ·          │
│             Watchlists · WatchlistDetail · NowPlaying ·          │
│             VideoPlayer · Settings                               │
│  playback/  PlaybackService (MediaSessionService + ExoPlayer)    │
│             queue · position persistence · lock-screen controls  │
│  engine/    YtdlpEngine: init · self-update ·                    │
│             resolveChannel/listChannelVideos/getVideoInfo ·      │
│             resolveStream (TTL cache, never persisted)           │
│               └─► youtubedl-android ─► yt-dlp on bundled Python  │
│  data/      Room: entities · DAOs · repositories                 │
└──────────────────────────────────────────────────────────────────┘
```

**The core engineering piece is the on-device yt-dlp engine** (`engine/YtdlpEngine`):
- `initialize()` / `update()` — set up the bundled runtime; self-update yt-dlp at runtime
- `resolveChannel(handleOrUrl)` — channel metadata
- `listChannelVideos(channelId, limit)` — fast flat extraction (`--flat-playlist`) of /videos
- `getVideoInfo(videoId)` — full metadata
- `resolveStream(videoId, mode)` — bestaudio (m4a) or best mp4: direct URL + HTTP headers for
  ExoPlayer; short-TTL in-memory cache (URLs expire ~6h; never persisted)

All engine calls are `suspend` functions on `Dispatchers.IO`; results parse via kotlinx.serialization.

## Data Model (Room)

- **Channel** — `youtubeId`, `handle`, `title`, `thumbnail`, `addedAt`
- **Video** (cache of channel uploads) — `youtubeId`, `channelId`, `title`, `durationSeconds`,
  `thumbnail`, `publishedAt`, `cachedAt`
- **Watchlist** — `name`, `createdAt`; **WatchlistItem** — `watchlistId`, `videoYoutubeId`,
  `position`, `addedAt`
- **PlaybackState** — `videoYoutubeId`, `positionSeconds`, `completed`, `updatedAt`
- **Download** — `videoYoutubeId`, `mode`, `status`, denormalized title/thumbnail/duration/channel,
  `filePath`, `sizeBytes`, `progressPercent`, `error`, `createdAt`, `completedAt`

## Phased Execution Plan

Each phase is a small reviewable slice; verify before the next.

- **Phase 0 — Scaffold & hygiene:** repo layout, `LICENSE`, `.gitignore`, `README`, branding. *(done)*
- **Phase 1 — App scaffold:** Gradle project, Compose + Material 3 theme (brand palette),
  bottom-nav shell (Channels · Watchlists · Now Playing · Settings), launcher icons/splash.
- **Phase 2 — yt-dlp engine (hard part):** youtubedl-android integration, `YtdlpEngine` API,
  runtime updater + auto-check, Settings shows version/update.
- **Phase 3 — Local data layer:** Room entities/DAOs/database, repositories.
- **Phase 4 — Channels:** add channel (resolve via engine), uploads list with cache + refresh.
- **Phase 5 — Audio player:** `PlaybackService` (MediaSessionService + ExoPlayer), queue,
  notification/lock-screen controls, screen-off playback.
- **Phase 6 — Video player:** in-app video surface + Picture-in-Picture, audio/video switch.
- **Phase 7 — Watchlists + resume:** watchlist UI, position reporting + resume wiring.
- **Phase 8 — Polish:** error/empty states, network handling, updater UX, docs, release build.
- **Phase 9 — Offline downloads:** yt-dlp writes audio/video to app-private storage, Room-backed
  queue in a `dataSync` foreground service, local files served to the player, Downloads screen,
  "Delete all" in Settings. Adds the ffmpeg artifact (arm64 split ≈ 54 MB).

## Verification

**Engine (Phase 2, the linchpin):**
- On the emulator/device: `getVideoInfo` for a known public video returns JSON; log yt-dlp
  version; run in-app update and confirm version bump.
- `resolveStream(id, AUDIO)` returns a googlevideo URL ExoPlayer can play.

**App (per phase):**
- `gradlew assembleDebug` compiles clean; install on emulator (Pixel AVD) via adb.
- Save a channel → uploads appear (Room persists across restarts) → queue to a watchlist →
  play as **audio with screen off** (`dumpsys media_session` shows active session + controls) →
  reopen and **resume** at saved position → switch to video + PiP.

**End-to-end acceptance:** add channel → browse uploads → add to watchlist → background-listen
with screen locked → resume later → play one as video, in PiP.

## Risks & Notes

- **yt-dlp breakage** when YouTube changes internals → **in-app runtime updater** + auto-check
  on launch.
- **Stream URL expiry (~6h)** → resolve on-demand + short-TTL in-memory cache; never persist URLs.
- **APK size:** the bundled Python runtime adds ~30–60 MB. Accepted tradeoff for zero-server;
  ship per-ABI splits (arm64-v8a primary) to keep downloads lean.
- **On-device extraction latency:** flat channel listing takes a few seconds — always off the
  main thread, show progress, cache aggressively in Room.
- **Queue advance in background:** resolving the next stream mid-playback happens inside the
  playback service; battery-optimization exemption documented for reliability.
- **Keep README/marketing neutral** — "personal media player for content you're authorized to
  access," no "rip/bypass/download any video" language.
