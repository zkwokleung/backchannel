# Developing Backchannel

## Requirements

- JDK 17
- Android SDK, compileSdk 35 (`local.properties` needs `sdk.dir=…`)
- A device or emulator on Android 8.0+ (minSdk 26)

## Build & run

```bash
cd android
./gradlew assembleDebug              # per-ABI + universal APKs
./gradlew testDebugUnitTest          # unit tests
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk   # emulator
```

## Project layout

```
android/app/src/main/java/com/zkwokleung/backchannel/
├── engine/      YtdlpEngine — on-device yt-dlp: init, self-update, extraction, stream resolve
├── data/        Room entities/DAOs + repositories (channels, watchlists, playback)
├── playback/    PlaybackService (MediaSessionService + ExoPlayer), queue, stream resolution, PiP
├── ui/          Compose screens: channels, watchlists, player, settings + theme
└── AppContainer manual DI, owned by BackchannelApp
```

## Things worth knowing before changing code

**yt-dlp must be updated at runtime.** The binary bundled in youtubedl-android lags YouTube by
months and silently returns *zero entries* rather than failing loudly. `YtdlpEngine` updates
before the first extraction and daily afterwards; don't remove that gate without a replacement.

**Stream URLs are resolved late, per open.** Queue items carry a
`backchannel://stream?v=<id>&mode=<AUDIO|VIDEO>` URI; `ResolvingStreamDataSourceFactory` swaps in
a real googlevideo URL when ExoPlayer opens the source. URLs expire (~6h) and are bound to the
device that resolved them, so they are cached in memory only and never persisted. A 403/410 is
retried once with a forced re-resolve.

**Player client matters.** Stream resolution pins `player_client=android_vr,web`. The default web
client returns SABR-protected URLs that answer the initial request but reject the range requests
ExoPlayer issues when seeking — playback starts, then dies on the first seek or resume.

**Release builds need the ProGuard rules.** youtubedl-android parses with Jackson reflectively;
without the keep rules in `proguard-rules.pro`, R8 produces `class … is not a concrete class` at
init. The Python payloads (`lib*.zip.so`) must also stay unstripped — see `packaging.jniLibs`.
Always smoke-test `assembleRelease` on a device, not just `assembleDebug`.

**Navigation.** Bottom-nav tabs each keep their own back stack; use `switchTab` (not `navigate`)
for anything that jumps between tabs, or the destination nests inside the wrong stack and
saved-state restore bounces the user back to it.

## Release build

Create `android/keystore.properties` (gitignored):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Then `./gradlew assembleRelease`. Without that file the release build is simply unsigned.

## Testing on a device

The end-to-end path worth re-running after engine or playback changes:

add channel → uploads listed → play audio → screen off, still playing (`adb shell dumpsys
media_session | grep PlaybackState`) → kill app → reopen and confirm it resumes at the saved
position → switch to video → picture-in-picture.
