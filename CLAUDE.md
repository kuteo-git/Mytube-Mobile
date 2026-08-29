# Mytube App — Project Charter

> Source of truth for every architectural decision. If a new decision contradicts
> this file, update this file rather than quietly going another way.
>
> The server this app talks to is a separate repository — Local Mytube, at
> `~/Documents/git/Youtube`. Its own `CLAUDE.md` is the authority on everything
> behind the API, and it is referred to below as **the server charter**.

## 1. What this is

A native client for a self-hosted media library that already has a web app. It
exists for one thing the browser cannot do, stated as risk 4 in the server
charter:

> *"Background playback on iOS is impossible from the web. Media Session + PiP
> are the web limits; a native app is the only path for background iOS
> playback."*

Kotlin Multiplatform with Compose Multiplatform, **Android and iOS**. Not a
box-ticking clone of the web app — the web app is better on a desktop and stays
the primary client.

## 2. Hard constraints

| | |
|---|---|
| Platforms | Android + iOS. **No desktop** — the browser already serves that well, and desktop JVM is the one target with no clear video path |
| Reach | **House wifi only.** The server is LAN-only, plain HTTP, no TLS, and `X-User-Id` is a header anyone can set |
| Storage | **Streams only.** Nothing is downloaded to the phone |
| Video tier | **HLS only.** The server's `local` file tier is phase 3 |
| Everything on Data2 | Source and toolchain both. The internal disk has ~13 GiB free and Xcode alone is ~20 GB |

**The accepted consequence of "wifi only" plus "streams only": background audio
works around the house, not on a commute.** That was decided with the trade in
view. Do not quietly add downloading to work around it — reopen the decision.

## 3. Architecture

Clean architecture, and the server charter states the rule as a property of
direction rather than of folder count:

> *"Clean architecture is about the direction of dependencies, not the number of
> processes. `domain` imports no DB, HTTP or framework."*

```
composeApp/src/
  commonMain/kotlin/com/mytube/app/
    domain/          entities, plain types. No Ktor, no serialization, no Compose
    application/     use cases; declares repository interfaces, knows no HTTP
    infrastructure/  implements them: Ktor, kotlinx.serialization, DTOs
    ui/              Compose. Never calls HTTP
  androidMain/       Media3/ExoPlayer, MediaSessionService, foreground service
  iosMain/           AVPlayer, AVAudioSession
  jvmTest/           the architecture guard — see below
```

- **`ArchitectureGuardTest` fails the build when an arrow points the wrong way.**
  A rule written down is a rule that gets forgotten once; the server's web app
  learned this twice and grew `untranslated.guard.test.ts` and
  `player-seek.guard.test.ts` for the same reason. It is a source scan, and it is
  crude on purpose.
- **No `@Serializable` outside `infrastructure`.** The moment a use case carries
  a wire annotation, the wire's shape and the logic's shape are one shape, and
  neither can change alone.
- **Domain types are not the server's JSON.** The gateway sends fields no screen
  here reads. Mapping at the edge means a renamed field breaks one file.

### The three platform seams

Designed once, with both platforms in view. That is the entire reason Android and
iOS are built together rather than one after the other.

| interface in `commonMain` | Android | iOS |
|---|---|---|
| `VideoPlayer` | Media3/ExoPlayer in `AndroidView` | `AVPlayer` in `UIKitView` |
| `NarrationPlayer` | second player, ducks ExoPlayer's volume | `AVAudioPlayer` on the same `AVAudioSession` |
| `BackgroundSession` | `MediaSessionService` + foreground service | `AVAudioSession` + `MPNowPlayingInfoCenter` |

`expect/actual` is how KMP supplies an implementation. It is not licence for `ui`
to reach for ExoPlayer directly.

## 4. Versions — read `gradle/libs.versions.toml` before changing anything

That file carries the full reasoning. The short version: **this is not "the
latest of everything"**, it is the one set that resolves, and four numbers are
deliberately older than what is published.

| | | why not newer |
|---|---|---|
| Kotlin | 2.3.20 | pinned *by Compose* — its klibs are built with it |
| Compose Multiplatform | 1.11.0 | 1.12.0 demands AGP ≥ 9.1.0 |
| AGP | 8.11.1 | **AGP 9.x is incompatible with the KMP plugin outright** |
| Gradle | 8.14 | AGP floor 8.13, Kotlin ceiling 8.14 |
| Ktor | 3.5.2 | 3.2.0 fails to dex below minSdk 30 |

Two traps worth keeping:

- **A KLIB version mismatch reports itself as a missing file.** "KLIB resolver:
  Could not find `<path>`" for a file that is plainly there means the compiler is
  older than the library. Read `default/manifest` inside the `.klib` and compare
  `compiler_version`. Three wrong turns were spent on the external volume, the
  configuration cache and filesystem case sensitivity before anyone looked.
- **Maven Central's search API returns stale versions.** It was wrong about
  Kotlin, Compose and Ktor in one sitting. Confirm with `maven-metadata.xml` or a
  direct request for the version directory.

## 5. The server API

87 REST endpoints; the app needs a fraction. The gateway speaks REST/JSON
outward — **there is no proto on this path**, and the web app's generated
`_pb.ts` files are unused by anything outside `gen/`.

- **Every request carries `X-User-Id`**, attached in one place. The web app's
  `shared/api/http.ts` explains why: *"a rule that has to be remembered forty
  times is a rule that will be forgotten once."*
- **The server address is typed by hand** on first run, like Home Assistant. The
  Mac's address is DHCP and the server charter lists a static LAN IP as still to
  be done; mDNS dies on ordinary routers with nothing left to type.
- **HLS URL carries the device ceiling**: `…/hls/master.m3u8?max=720`. A phone is
  capped at 720p, and on iOS the cap *cannot* be applied in the client — native
  HLS gives no way to limit a level, so the server writes a shorter playlist.

### Narration comes from the server, as a manifest

The web app drives narration in the browser — read cues, hash, look up the
translation cache, translate in batches, synthesise per cue, schedule playback.
That is 1,509 lines and it is **not** being ported.

```
POST /api/videos/{id}/narration    start or continue a pass
GET  /api/videos/{id}/narration    { status, done, total,
                                     cues: [{ startSeconds, durationSeconds, clipUrl }] }
```

- **Progressive, not all-or-nothing.** `GET` returns what is finished so far.
  Waiting for a whole hour-long video before the first word is minutes of
  staring at nothing.
- **The infrastructure already exists on the server**: clips live at
  `<mediaRoot>/<videoId>/narration-tts/<sha1>.wav` and are already served over
  `/media`; the translation cache is `GET/POST /api/videos/{id}/narration-cache`.
  What is missing is only the driver.
- **This is what makes background playback possible.** A thousand TTS requests
  from a phone with the screen off is something the OS kills. A manifest plus a
  few audio files is just playing music.

## 6. Scope

**v1 screens**: Home · Subscriptions · History · Watch · Search · Channel ·
Settings (reduced).

Deliberately absent, with reasons:

- **Storage, Activity** — these are screens for *fixing the server*, and people
  fix servers sitting at a computer, where the web app already does it well.
- **Playlists, Watch later, Saved** — wanted, but after playback and background
  audio are proven.
- **Equaliser and reverb** — 709 lines of Web Audio with no equivalent on
  Android. An equaliser corrects for speakers, and a phone is not a living room.
  **Narration ducking stays in v1**: without it the voice and the original audio
  play over each other and the feature is useless.

## 7. Language

All source, identifiers, **comments** and commit messages in English — the same
rule as the server charter, with no exception. Only display strings go through
i18n and have a Vietnamese translation. Conversation happens in Vietnamese; the
artifact does not.

Translations are **copied into Kotlin** rather than shared with the web app as
JSON. The web app's three guard layers are built on TypeScript's type system, and
trading them away to synchronise with an app that shows half the screens is a bad
bargain. Expect the two to drift; that is the accepted cost of running two
clients.

## 8. Testing

Written alongside, not after.

| layer | where | what it catches |
|---|---|---|
| Unit | `commonTest`, `kotlin.test` | pure decisions: rung choice, ducking levels, manifest parsing |
| Guard | `jvmTest` | dependency direction |
| UI | `commonTest`, `runComposeUiTest` | screen behaviour |

- **`runComposeUiTest` is marked Experimental** by JetBrains. Recorded as it is.
- **There is no official golden/screenshot support** in Compose Multiplatform. A
  third-party tool on the Android target may be tried; no library is named here
  until one has actually run.
- **A `jvm` target exists only to run tests.** Every other target needs hardware
  — `connectedAndroidTest` an emulator, `iosSimulatorArm64Test` a multi-gigabyte
  runtime — while `jvmTest` runs in seconds. That is the difference between tests
  that run on every edit and tests that run when somebody remembers to plug in a
  phone. It does not contradict "no desktop": what was dropped is shipping one.

**Nothing is called done because it compiled.** Background audio, lock-screen
controls and ducking have to be heard on real hardware with the screen off.

## 9. Toolchain

Everything on the external volume; `source env.sh` before working.

```
/Volumes/Data2/mytube-app/     this repo
/Volumes/Data2/dev/jdk/        Temurin JDK 21
/Volumes/Data2/dev/android-sdk/  platforms 35 + 36, cmdline-tools
/Volumes/Data2/dev/gradle/     GRADLE_USER_HOME
/Volumes/Data2/dev/konan/      KONAN_DATA_DIR — 1.3 GB that defaults to ~/.konan
```

- **`env.sh`, not `~/.zshrc`.** A shell profile outlives what it points at: that
  machine carried five `FLUTTER_ROOT` lines aimed at a directory deleted months
  earlier, found only while hunting for disk space.
- **compileSdk 36** because `androidx.navigationevent`, pulled in transitively,
  refuses to be consumed by anything older. 33 metadata checks failed at 35.
- **Xcode is not installed yet.** ~20 GB, and it goes to
  `/Volumes/Data2/Xcode.app` with `xcode-select -s`. Whether to also install a
  simulator runtime is decided by reading the size Xcode reports in Settings →
  Components — it lives in a root-owned path on the internal disk, and relocating
  it means a `sudo` symlink of a system path that has not been verified on
  macOS 26.
- **The cost, stated plainly**: when Data2 is not mounted, this project cannot be
  built. Risk 1 in the server charter now reaches the toolchain, not just the
  library.

## 10. Status

- Project builds for **Android (APK), iOS device and iOS simulator**, and
  `jvmTest` runs.
- `ArchitectureGuardTest` is in place; `domain` has its first types.
- **Nothing talks to the server yet.** The API client, the screens, the players
  and the narration manifest endpoint are all still to be written.
