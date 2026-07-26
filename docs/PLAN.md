# Backchannel — Architecture & Build Plan

## Context

A personal app to browse **your own saved YouTube channels** and **listen to videos like a
podcast** (background / audio-only) or watch them, with **watchlists**. Built on **yt-dlp**,
intended to be **open-sourced**, and kept in the low-liability "personal use" zone (no bundled
content, user-driven fetches, no shared hosting).

**Decisions locked:**
- **Platform:** Android-primary (APK / F-Droid; iOS only via personal sideload later).
- **Architecture:** Mobile-only forces a **backend** — neither iOS nor Android can run yt-dlp
  cleanly on-device, and YouTube stream URLs are **IP-locked** to whoever extracts them, so the
  server must **proxy** the stream to the phone.
- **Hosting:** **Self-hosted** — each user runs their own backend (Docker); the app points at it.

**Outcome:** A monorepo with (1) a Dockerized FastAPI + yt-dlp server a user runs on a home
box/VPS, and (2) an Android Expo app that saves channels, builds watchlists, and plays videos as
background audio or video, resuming where you left off.

## Tech Stack

**Backend (`server/`)** — Python 3.12 + **FastAPI** + **Uvicorn**
- **yt-dlp as a library** (not CLI) — native format selection, progress hooks, caching control.
- **ffmpeg** — audio extraction / optional transcode.
- **SQLModel + SQLite** — saved channels, cached video lists, watchlists, playback state.
- **httpx** — upstream streaming for the proxy.
- Shipped as a **Docker image** + `docker-compose.yml`; yt-dlp auto-updates on container start.

**App (`app/`)** — **Expo (React Native) + TypeScript**, Android-first
- **react-native-track-player** — background audio, lock-screen/notification controls, queue.
- **expo-video** — video playback + Picture-in-Picture.
- **expo-router** — navigation. **@tanstack/react-query** — server data. **zustand** — player/UI state.
- **expo-secure-store** — persist server URL + API key.
- Requires an **Expo dev build** (`expo-dev-client` / EAS) — track-player & video are native
  modules and do **not** run in Expo Go.

## Architecture

```
Android App (Expo/TS)                Self-Hosted Server (FastAPI/Python)
─────────────────────                ───────────────────────────────────
Channels / Videos / Watchlists  ──►  REST API (channels, videos, watchlists, playback)
Now-Playing (track-player)      ──►  /stream/{id}?mode=audio  ──► yt-dlp resolve ──► proxy
Video player (expo-video)       ──►  /stream/{id}?mode=video  ──► googlevideo (range-aware)
Settings (server URL + API key) ──►  X-API-Key auth
                                     SQLite: channels, video cache, watchlists, playback
```

**The core engineering challenge is the stream proxy.** Because stream URLs are IP-locked to the
server, `/stream/{id}` resolves the direct googlevideo URL server-side, opens it with the
client's `Range` header forwarded, and streams bytes back with correct
`Content-Type` / `Accept-Ranges` / `Content-Range` so track-player and expo-video can seek. Start
by proxying `bestaudio` (m4a) directly for podcast mode — reach for ffmpeg transcode only if
format-compatibility issues appear (CPU cost).

## Data Model (SQLite)

- **Channel** — `youtube_id`, `handle`, `title`, `thumbnail`, `added_at`
- **Video** (cache of channel uploads) — `youtube_id`, `channel_id`, `title`, `duration`,
  `thumbnail`, `published_at`, `cached_at`
- **Watchlist** — `name`, `created_at`; **WatchlistItem** — `watchlist_id`, `video_youtube_id`,
  `position`, `added_at`
- **PlaybackState** — `video_youtube_id`, `position_seconds`, `completed`, `updated_at`

## Key Backend Modules (`server/`)

- `main.py` — FastAPI app + route registration
- `config.py` — env (API key, cache dir, port, yt-dlp opts)
- `db.py` — SQLModel engine + models above
- `ytdlp_service.py` — `list_channel_videos()` (uses `extract_flat='in_playlist'` for fast
  listing), `get_video_info()`, `resolve_stream(video_id, mode)` (bestaudio vs best video;
  short-TTL URL cache since links expire ~6h)
- `proxy.py` — range-aware streaming proxy
- `routes/` — `channels.py`, `videos.py`, `watchlists.py`, `playback.py`

## Phased Execution Plan

Each phase touches ≤5 files; verify + get approval before the next.

- **Phase 0 — Scaffold & hygiene:** monorepo layout, `LICENSE`, `.gitignore`, `README`
  with the neutral legal posture. *(done in this repo setup)*
- **Phase 1 — Backend core:** FastAPI skeleton, `config`, `db` models, channel-listing &
  video-info endpoints (`ytdlp_service`).
- **Phase 2 — Stream proxy (hard part):** `/stream/{id}` range-aware proxy, audio + video modes.
- **Phase 3 — Watchlists, playback state, API-key auth.**
- **Phase 4 — Dockerize:** `Dockerfile` (python-slim + ffmpeg), `docker-compose.yml`, volumes,
  yt-dlp auto-update, setup docs.
- **Phase 5 — App scaffold:** Expo dev build, `expo-router` nav, Settings (server URL/key),
  typed API client + react-query.
- **Phase 6 — Channels + video-list screens.**
- **Phase 7 — Audio player:** track-player background playback, lock-screen controls.
- **Phase 8 — Video player:** expo-video + PiP.
- **Phase 9 — Watchlists UI + resume-playback wiring.**
- **Phase 10 — Polish:** error/empty states, offline handling, docs pass.

## Verification

**Backend (per phase):**
- `uvicorn` up; hit endpoints with httpie/curl against a real public channel.
- Proxy: point **VLC** at `http://server/stream/{id}?mode=audio` — confirms playback independent
  of the app. Test seeking: `curl -H "Range: bytes=0-1023"` returns `206`.
- Docker: `docker compose up` from clean; volume persists SQLite across restarts.

**App:**
- Android device/emulator via dev build; enter server URL + key in Settings.
- Save a channel → uploads appear → queue to a watchlist → play as **audio with screen off**
  (lock-screen controls) → reopen and **resume** at saved position → switch to video + PiP.

**End-to-end acceptance:** add channel → browse uploads → add to watchlist → background-listen
with screen locked → resume later → play one as video.

## Risks & Notes

- **yt-dlp breakage** when YouTube changes internals → container auto-updates yt-dlp on start.
- **Stream URL expiry (~6h)** → resolve on-demand + short-TTL cache; never persist URLs.
- **Bandwidth doubles** (server pulls + pushes) — fine for self-host/personal scale.
- **Expo Go won't work** — track-player/expo-video need a dev/EAS build; documented in Phase 5.
- **Android background audio** needs a foreground service (track-player provides) + a note about
  battery-optimization exemption for reliable playback.
- **Keep README/marketing neutral** — "personal media player for content you're authorized to
  access," no "rip/bypass/download any video" language.
