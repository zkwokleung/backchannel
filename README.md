# Backchannel

> **Working name** — *Backchannel* (background + channels). Placeholder; rename freely.

A **personal, self-hosted media player** for the YouTube channels *you* follow. Browse your
saved channels, build watchlists, and **listen to videos like a podcast** — background /
audio-only playback with lock-screen controls — or watch them with picture-in-picture.

Backchannel ships **no content of its own**. It is a client that runs on hardware you
control and fetches only what you explicitly request, using [yt-dlp](https://github.com/yt-dlp/yt-dlp).

---

## How it works

Because mobile devices can't run yt-dlp cleanly on-device — and because YouTube stream URLs
are **IP-locked** to whoever resolves them — Backchannel is split in two:

```
Android App (Expo / TypeScript)          Self-Hosted Server (FastAPI / Python)
───────────────────────────────          ─────────────────────────────────────
Channels · Videos · Watchlists    ──►     REST API
Now-Playing (background audio)    ──►     /stream/{id}?mode=audio ─► yt-dlp ─► proxy
Video player (PiP)                ──►     /stream/{id}?mode=video ─► range-aware proxy
Settings (server URL + API key)   ──►     X-API-Key auth · SQLite
```

You run the **server** (a Docker container) on a home box or VPS. The **Android app** points
at your server. The server resolves streams with yt-dlp and **proxies** them to your phone.

## Components

| Path       | Stack                                                           | Role                                           |
|------------|-----------------------------------------------------------------|------------------------------------------------|
| `server/`  | Python 3.12 · FastAPI · yt-dlp (as a library) · ffmpeg · SQLite | Resolves + proxies streams; stores your data   |
| `app/`     | Expo (React Native) · TypeScript · react-native-track-player · expo-video | Android-first client                 |

## Status

🚧 **Planning / pre-bootstrap.** No application code yet. See **[docs/PLAN.md](docs/PLAN.md)**
for the full architecture and the phased build plan.

## Platforms

- **Android** — primary target (APK / F-Droid distribution; no App Store gatekeeper).
- **iOS** — possible later via personal sideload / TestFlight only. Apple's App Store rejects
  yt-dlp-backed apps, so iOS is not a distribution target.

## Self-hosting model

Each user runs **their own** backend. Nothing is shared or centrally hosted. This keeps
Backchannel firmly in personal-use territory and is why it can be open-sourced comfortably.

## Legal & scope

Backchannel is a **personal media client for content you are authorized to access**. It bundles
no video, hosts nothing for others, and fetches only what its operator requests — the same
posture as the yt-dlp tool it builds on.

- ✅ Personal, local/self-hosted use
- ❌ Not for re-hosting, re-distributing, or serving content to other people
- ❌ Not for circumventing DRM or accessing paid/age-gated content

You are responsible for complying with the terms of any service you use it with. This is not
legal advice.

## License

[MIT](LICENSE).
