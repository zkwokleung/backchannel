# Backchannel

> **Working name** — *Backchannel* (background + channels). Placeholder; rename freely.

A **personal, fully self-contained media player** for the YouTube channels *you* follow. Browse
your saved channels, build watchlists, and **listen to videos like a podcast** — background /
audio-only playback with lock-screen controls — or watch them with picture-in-picture.

Backchannel ships **no content of its own** and needs **no server, no account, no cloud**.
Everything runs on your phone: [yt-dlp](https://github.com/yt-dlp/yt-dlp) is embedded directly
in the app and fetches only what you explicitly request.

---

## How it works

The app embeds a Python runtime via
[youtubedl-android](https://github.com/yausername/youtubedl-android), so **yt-dlp runs
on-device** — the same battle-tested approach used by apps like Seal. Because the phone itself
resolves stream URLs, the URLs are valid for the phone (they are IP-locked to whoever resolves
them), and playback streams directly from the source.

```
Android App (Expo / TypeScript)
────────────────────────────────────────────────────────
Channels · Videos · Watchlists   ──►  local SQLite
Now-Playing (background audio)   ──►  on-device yt-dlp ─► direct stream URL ─► track-player
Video player (PiP)               ──►  on-device yt-dlp ─► direct stream URL ─► expo-video
Settings                         ──►  yt-dlp version + in-app updates
```

## Components

| Path                   | Stack                                                        | Role                                    |
|------------------------|--------------------------------------------------------------|-----------------------------------------|
| `app/`                 | Expo (React Native) · TypeScript · react-native-track-player · expo-video · expo-sqlite | The entire application |
| `app/modules/ytdlp/`   | Expo Modules API (Kotlin) · youtubedl-android                | Native bridge that runs yt-dlp on-device |

## Status

🚧 **Planning / pre-bootstrap.** No application code yet. See **[docs/PLAN.md](docs/PLAN.md)**
for the architecture and the phased build plan.

## Platforms

- **Android only** (APK / F-Droid distribution). The embedded-Python approach that makes the app
  self-contained is not possible on iOS, and Apple's App Store rejects yt-dlp-backed apps.

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
