# Backchannel — Install & Use

Backchannel is a single Android app. There is no server to run, no account to create, and no
configuration beyond installing it.

## Install

**From a release APK**

1. Download the APK for your device from the project's Releases page. `arm64-v8a` is right for
   essentially every modern phone; `universal` works everywhere but is ~3× larger.
2. Optionally check it against `SHA256SUMS.txt` from the same release
   (`sha256sum backchannel-*.apk`).
3. Allow installing from your browser/file manager when Android prompts, then open the APK.

**From source**

```bash
git clone https://github.com/zkwokleung/backchannel
cd backchannel/android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Requires JDK 17 and the Android SDK (compileSdk 35). No other setup.

## First launch

On first use the app updates its embedded yt-dlp before the first extraction. This takes a few
seconds and happens automatically — the version shipped inside the app is normally months old
and would silently return nothing. After that it re-checks once a day.

## Using it

**Add a channel** — Channels tab → **+** → paste an `@handle`, a channel URL, or a `UC…` ID.
The app fetches the channel's most recent 100 uploads and caches them on the device.

**Listen** — tap any video to start audio-only playback. It keeps playing with the screen off
and appears on your lock screen and in the notification shade with play/pause/skip controls.

**Watch** — the **⋮** menu on any video offers *Watch (video)*, or use *Switch to video* from
Now Playing. In the video screen, the picture-in-picture button (or pressing Home) shrinks it to
a floating window that keeps playing.

**Watchlists** — **⋮** → *Add to watchlist* to build a queue. Playing an entry plays the whole
watchlist in order; reorder entries with the arrows.

**Resume** — playback position is saved continuously. Reopen a video and it picks up where you
stopped; anything past 95% is marked finished and starts over.

**Refresh** — pull down (or tap the refresh icon) on a channel to re-fetch its uploads.

## Settings

- **yt-dlp version and update** — extraction breaks when YouTube changes things. Tap **Update**
  to fetch the newest yt-dlp without reinstalling the app. Try this first if anything stops
  working.
- **Battery optimization** — Android may kill background playback on some devices. If audio
  stops when the screen is off, open this and allow Backchannel to run unrestricted.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Couldn't find that channel" | Check the handle. `@veritasium`, `youtube.com/@veritasium`, and the `UC…` ID all work. |
| Channel adds but no videos appear | Update yt-dlp in Settings, then pull to refresh. |
| "Can't reach YouTube" | Connectivity. Cached channel lists still browse offline; playback needs a connection. |
| Playback stops when screen turns off | Allow unrestricted battery use (Settings → Battery optimization). |
| A video won't play | Age-restricted, members-only, and DRM-protected videos are not supported. |
| Everything broke at once | YouTube likely changed something: update yt-dlp in Settings. |

## Your data

Channels, watchlists, and playback history live in a SQLite database inside the app's private
storage. Nothing is uploaded anywhere. Uninstalling the app deletes all of it.
