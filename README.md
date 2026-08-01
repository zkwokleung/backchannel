# Backchannel

> **Working name** — *Backchannel* (background + channels). Placeholder; rename freely.

A **personal, fully self-contained Android media player** for the YouTube channels *you* follow.
Browse your saved channels, build watchlists, and **listen to videos like a podcast** —
background / audio-only playback with lock-screen controls — or watch them with
picture-in-picture.

Backchannel ships **no content of its own** and needs **no server, no account, no cloud**.
Everything runs on your phone: [yt-dlp](https://github.com/yt-dlp/yt-dlp) is embedded directly
in the app and fetches only what you explicitly request.

---

## How it works

The app is **native Android (Kotlin)** and embeds a Python runtime via
[youtubedl-android](https://github.com/yausername/youtubedl-android), so **yt-dlp runs
on-device** — the same battle-tested approach used by apps like Seal. Because the phone itself
resolves stream URLs, the URLs are valid for the phone (they are IP-locked to whoever resolves
them), and Media3/ExoPlayer streams them directly from the source.

```
Android App (Kotlin / Jetpack Compose)
────────────────────────────────────────────────────────────
Channels · Videos · Watchlists  ──►  Room (local SQLite)
Now-Playing (background audio)  ──►  on-device yt-dlp ─► direct stream URL ─► Media3 ExoPlayer
Video player (PiP)              ──►  MediaSessionService · lock-screen controls
Settings                        ──►  yt-dlp version + in-app updates
```

## Stack

| Concern            | Choice                                                        |
|--------------------|---------------------------------------------------------------|
| UI                 | Jetpack Compose · Material 3 · Navigation Compose             |
| Extraction engine  | youtubedl-android (embedded Python + yt-dlp, updatable in-app)|
| Playback           | Media3 ExoPlayer · `MediaSessionService` (background audio, lock-screen, PiP) |
| Storage            | Room (SQLite) — channels, upload cache, watchlists, playback state |
| Project            | `android/` — single-module Gradle project                     |

## Status

**Working.** Add channels, browse uploads, background audio with lock-screen controls,
video with picture-in-picture, watchlists, and resume-where-you-left-off all function on device.

- **[docs/USAGE.md](docs/USAGE.md)** — install and use it
- **[docs/DEVELOPING.md](docs/DEVELOPING.md)** — build it, and the non-obvious constraints
- **[docs/PLAN.md](docs/PLAN.md)** — architecture and build plan

## Platforms

- **Android only** (APK / F-Droid distribution), minSdk 26 (Android 8.0). The embedded-Python
  approach that makes the app self-contained is not possible on iOS, and Apple's App Store
  rejects yt-dlp-backed apps.

## Self-contained model

Nothing leaves your device: subscriptions, watchlists, and playback history live in a local
SQLite database. There is no backend to run, nothing is shared or hosted, and the app talks only
to the services you point it at. This keeps Backchannel firmly in personal-use territory and is
why it can be open-sourced comfortably.

## Legal & scope

Backchannel is a **personal media client for content you are authorized to access**. It bundles
no video, hosts nothing, and fetches only what its operator requests — the same posture as the
yt-dlp tool it builds on.

- ✅ Personal, on-device use
- ❌ Not for re-hosting, re-distributing, or serving content to other people
- ❌ Not for circumventing DRM or accessing paid/age-gated content

You are responsible for complying with the terms of any service you use it with. This is not
legal advice.

## License

[MIT](LICENSE).
