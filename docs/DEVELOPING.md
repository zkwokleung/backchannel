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

**Don't pin a player client.** Stream resolution deliberately passes no
`youtube:player_client` extractor arg. Which clients hand out working URLs changes under
yt-dlp's feet: `android_vr,web` was once pinned here to dodge the web client's SABR-protected
URLs (they reject the range requests ExoPlayer issues when seeking), but android_vr later
degraded to serving only a single dead muxed format, which silently broke all playback on an
otherwise up-to-date app. yt-dlp's maintainers keep the default client set current with
YouTube — trusting it is what makes the extraction self-healing.

**Video is two streams.** YouTube no longer serves its combined audio+video files: their URLs
still resolve, but every download answers 403, no matter the client or headers. Video items are
therefore resolved as `bestvideo+bestaudio` and `StreamMediaSourceFactory` merges the two
progressive tracks inside ExoPlayer. The audio track from that one extraction is cached under
the AUDIO mode too, so the merged item's audio leg doesn't cost a second yt-dlp run.

**Never stream a googlevideo file open-ended.** An unbounded range request (`bytes=X-`) is
throttled server-side to a few KB/s — below even audio bitrate, so playback stalls after the
initial burst. Bounded chunks are served at full speed, which is why
`ResolvingStreamDataSource` reads through chained ~10 MiB range requests (yt-dlp's
`http_chunk_size` exists for the same reason). A side benefit: a URL that expires mid-playback
is caught and re-resolved at the next chunk boundary.

**Release builds need the ProGuard rules.** youtubedl-android parses with Jackson reflectively;
without the keep rules in `proguard-rules.pro`, R8 produces `class … is not a concrete class` at
init. The Python payloads (`lib*.zip.so`) must also stay unstripped — see `packaging.jniLibs`.
Always smoke-test `assembleRelease` on a device, not just `assembleDebug`.

**Navigation.** Bottom-nav tabs each keep their own back stack; use `switchTab` (not `navigate`)
for anything that jumps between tabs, or the destination nests inside the wrong stack and
saved-state restore bounces the user back to it. The player is deliberately not a tab — it is
pushed over whatever you were doing (`openPlayer`), so leaving it is a plain `popBackStack` that
lands back where you came from rather than on a tab's start destination.

**Release asset names are an API.** The in-app updater (`update/`) finds its download by parsing
the filenames `release.yml` stages:

| | |
|---|---|
| APKs | `backchannel-<version>-<abi>.apk` — `arm64-v8a`, `x86_64`, `universal` |
| Checksums | `SHA256SUMS.txt`, lines of `<sha256>  <filename>` |
| Version source | the tag minus `v`, compared as semver — **not** `versionCode`, which is the CI run number and means nothing outside CI |

Rename any of these in the workflow and `ReleasePayloadContractTest` fails, which is the point.
Changing them for real means shipping the updater change first, since older installs parse the
old names.

**The updater stays enabled in debug builds.** A debug install can never be replaced by a
release-signed APK, but hiding the feature would mean the whole path is only ever exercised in
production. `ApkInstaller.verify()` catches it at the last gate and says so specifically.

## Release build

Signing settings come from `android/keystore.properties` (gitignored) or, failing that, from
environment variables. With neither, `assembleRelease` produces an **unsigned** APK, which
Android will refuse to install.

```properties
# android/keystore.properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

The environment equivalents are `BACKCHANNEL_KEYSTORE` (path), `BACKCHANNEL_KEYSTORE_PASSWORD`,
`BACKCHANNEL_KEY_ALIAS`, and `BACKCHANNEL_KEY_PASSWORD`. `BACKCHANNEL_VERSION_NAME` and
`BACKCHANNEL_VERSION_CODE` override the versions checked into `build.gradle.kts`.

Then `./gradlew assembleRelease`, which emits per-ABI APKs (~17 MB) plus a universal one.

## Publishing a release

Pushing a `v*` tag runs `.github/workflows/release.yml`, which runs the unit tests, builds
signed release APKs, verifies each one's signature, and publishes a GitHub release with the
APKs and a `SHA256SUMS.txt`. A version with a suffix (`v1.0.0-beta.1`) is marked pre-release.
You can also trigger it manually from the Actions tab with a version input.

The tag drives the version: `v0.2.0` builds `versionName` `0.2.0`, and `versionCode` comes from
the workflow run number so it always increases.

**One-time setup.** Create a keystore and keep it somewhere safe — losing it means you can never
ship an upgrade to anyone who installed a previous build:

```bash
keytool -genkeypair -v -keystore release.jks -alias backchannel \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks    # macOS: base64 -i release.jks
```

Add four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 output above |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `backchannel` (or whatever alias you chose) |
| `KEY_PASSWORD` | key password |

The workflow fails fast with a clear message if `KEYSTORE_BASE64` is missing, rather than
publishing an unsigned APK nobody can install.

## Testing on a device

The end-to-end path worth re-running after engine or playback changes:

add channel → uploads listed → play audio → screen off, still playing (`adb shell dumpsys
media_session | grep PlaybackState`) → kill app → reopen and confirm it resumes at the saved
position → switch to video → picture-in-picture.
