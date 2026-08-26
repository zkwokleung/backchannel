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

## Updating the app

Settings → **Backchannel** shows the installed version. Tap **Check** to ask GitHub for a newer
release; the app also checks quietly once a day and marks the row when one appears. Nothing is
downloaded until you tap **Update** and confirm — the dialog shows the version, the download size
and the release notes first.

The download is checksummed against the release's `SHA256SUMS.txt` and its signing key is
compared against the installed app's before Android's installer is shown, so a corrupted or
wrongly-signed download is refused with a reason rather than a system error. Android will ask you
to allow Backchannel to install apps the first time.

**Installing restarts the app and stops playback.** Nothing is lost — positions are saved
continuously — but finish what you are listening to first.

Two things worth knowing: a build installed from `assembleDebug` cannot be updated this way (the
keys differ; uninstall it and install a release APK), and manual download from the Releases page
still works exactly as before.

## First launch

On first use the app updates its embedded yt-dlp before the first extraction. This takes a few
seconds and happens automatically — the version shipped inside the app is normally months old
and would silently return nothing. After that it re-checks once a day.

## Using it

Swipe left or right anywhere on a tab's screen to move between Channels, Watchlists and
Settings, or tap the bar at the bottom.

**Add a channel** — Channels tab → **+** → paste an `@handle`, a channel URL, or a `UC…` ID.
The app fetches the channel's most recent 100 uploads and caches them on the device.

**Listen** — tap any video to start audio-only playback. It keeps playing with the screen off
and appears on your lock screen and in the notification shade with play/pause/skip controls. A
mini-player above the tab bar controls it from anywhere in the app; tap it — or the
notification — to open the full player, and drag the player down to put it back.

**Watch** — the **⋮** menu on any video offers *Watch (video)*, or use *Switch to video* from
Now Playing. In the video screen, the picture-in-picture button (or pressing Home) shrinks it to
a floating window that keeps playing.

**Watchlists** — **⋮** → *Add to watchlist* to build a queue. Playing an entry plays the whole
watchlist in order; the **⋮** menu on each entry moves it up or down.

**Resume** — playback position is saved continuously, and lists show how far through each video
you are. Reopen one and it picks up where you stopped; an item you played to the end is marked
finished and starts over.

**Refresh** — pull down (or tap the refresh icon) on a channel to re-fetch its uploads.

## Settings

- **Backchannel version and update** — see "Updating the app" above.
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
