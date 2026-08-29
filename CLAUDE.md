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
    domain/model/        entities. No Ktor, no serialization, no Compose
    domain/repository/   the ports — interfaces, declared by the layer that needs them
    domain/usecase/      use cases
    data/remote/         Ktor data source; data/remote/dto holds the wire shapes
    data/local/          settings storage, one expect/actual per platform
    data/repository/     implementations of domain/repository
    ui/<screen>/         Composable + ViewModel
  androidMain/           Media3/ExoPlayer, MediaSessionService, foreground service
  iosMain/               AVPlayer, AVAudioSession
  jvmTest/               the architecture guard — see below
```

- **`ArchitectureGuardTest` fails the build when an arrow points the wrong way.**
  A rule written down is a rule that gets forgotten once; the server's web app
  learned this twice and grew `untranslated.guard.test.ts` and
  `player-seek.guard.test.ts` for the same reason. It is a source scan, and it is
  crude on purpose.
- **No `@Serializable` outside `data`.** The moment a use case carries
  a wire annotation, the wire's shape and the logic's shape are one shape, and
  neither can change alone.
- **Domain types are not the server's JSON.** The gateway sends fields no screen
  here reads. Mapping at the edge means a renamed field breaks one file.
- **Nothing in `domain` or a ViewModel is nullable. DTOs are.** The wire really
  can omit a field; pretending otherwise turns a legal answer into a parse
  failure. So absence is decided once, in the mapper, and named rather than
  implied — `Video.hasPublishedDate`, `FeedPage.hasMore`, not `.isEmpty()`
  scattered across screens. One exception is meaningful rather than absent: an
  empty profile id stays empty all the way to the request, because the gateway
  falls back to a default when `X-User-Id` is *missing*, and that fallback is
  what makes a fresh install work.
- **No DI framework.** Constructor injection by hand from one composition root,
  the same as the Go services do in `main.go` and the web app does with module
  singletons. Koin is a service locator whose missing registrations fail at
  runtime, which is the opposite of this project's habit of turning rules into
  compile errors.

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
| lifecycle | 2.10.0 | 2.11.0 demands AGP ≥ 9.1.0, same as Compose 1.12.0 |

**The AGP 9.1 trap bites once per dependency.** Any androidx artifact of the
newest generation declares it. Before adding one, take the version *below* the
newest, or expect "requires Android Gradle plugin 9.1.0 or higher" and another
downgrade.

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

### The dictionary is an interface, not a key lookup

`Strings` declares every word the app shows as a **property**; `EnglishStrings`
and `VietnameseStrings` implement it; `LocalStrings` provides the current one.

- **A missing translation cannot exist.** Add a property and every language stops
  compiling until it is supplied, with the compiler naming the class and the
  field. A resource file with keys can always be missing a key. This is the web
  app's typed-keys layer taken one step further, and it exists because
  *half*-translating — not missing translations — is what shipped there.
- **Not Compose Resources.** It would work, and it means XML, a codegen step, and
  a lookup that fails at runtime for a key one language lacks. That trades the
  guarantee above for a familiar file format.
- **`UntranslatedGuardTest` catches what the type cannot**: a literal that was
  never put on the interface at all, sitting in a composable and rendering in
  English while nothing reports it. **Proven to fail** — replacing one
  `strings.tryAgain` with `"Try again"` turns the build red.
- **Units and grammar belong to the language.** Count suffixes are `K/M/B` and
  `N/Tr/T`; formatters take `Strings` rather than hard-coding either. The web
  app's version once appended an English plural and printed "3 ngàys trước".

Translations are **copied into Kotlin** rather than shared with the web app as
JSON. Expect the two to drift; that is the accepted cost of running two clients.

## 8. Testing

Written alongside, not after.

| layer | where | what it catches |
|---|---|---|
| Unit | `commonTest`, `kotlin.test` | pure decisions: mapping, formatting, ViewModel branches |
| Guard | `jvmTest` | dependency direction, and copy that never reached the dictionary |
| Preview | `@Preview` on every screen | how a screen looks in every state |

- **No Compose UI tests.** Decided directly, and `runComposeUiTest` is in any
  case still marked Experimental. Previews cover what a screen looks like;
  ViewModel tests cover what it does.
- **Every screen has previews, and more than one.** The states that matter are
  the ones that are hard to reach on a device — loading, empty, failed — plus a
  Vietnamese preview where the copy length differs, because a layout only ever
  seen in English breaks for half the household.
- **This forces the right structure.** A composable holding a ViewModel cannot be
  previewed, so each screen is split: `XScreen(viewModel, …)` unwraps the state
  holder and calls `XContent(state, callbacks…)`, which takes plain values.
- **A `jvm` target exists only to run tests.** Every other target needs hardware
  — `connectedAndroidTest` an emulator, `iosSimulatorArm64Test` a multi-gigabyte
  runtime — while `jvmTest` runs in seconds. It does not contradict "no desktop":
  what was dropped is shipping one.
- **Both guards are proven to fail.** An `io.ktor` import in `domain`, and a
  hardcoded `"Try again"` in a screen, each turn the build red. A guard nobody
  has watched fail is a guard nobody should believe.

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

- Builds for **Android (APK), iOS device and iOS simulator**; **33 tests pass**.
- **The app runs end to end on Android**: type the server address, and the home
  feed loads from the gateway with thumbnails and channel avatars.
- In place: the four layers for the feed, `AppContainer` as the composition root,
  `SettingsDataSource` for Android and iOS, the design tokens copied from
  `MASTER.md`, and the English/Vietnamese dictionary.
- **Still to write**: the watch screen and both players, background audio and the
  media session, the remaining five screens, a language switch, and the narration
  manifest endpoint on the server.
- **Never run on iOS.** It compiles for the device and the simulator; Xcode is
  not installed, so nothing has been launched there. Do not describe the iOS half
  as working.

## The watch screen is a layer, and dragging it down reveals the tab (2026-08-29)

The server charter says it plainly — *"On phones, the watch screen is a layer
over the previous tab; pulling it down reveals the tab underneath"* — and the
brief for this app named the gesture directly. Measured on the emulator: a drag
of a fifth of the screen springs back with the video still running at 0:19, and
a drag past the threshold lands on Home with that video at the head of Continue
watching, its red bar drawn.

- **Home is composed underneath, not rebuilt afterwards.** A drag reveals what is
  behind it. Restoring the tab only once the layer is gone means the first half
  of the gesture uncovers an empty background and the feed snaps in at the end,
  which reads as a reload rather than a video moving out of the way. So Home and
  Watch are one branch of the route, stacked in a Box.
- **The threshold is a third of the screen, and distance only — no velocity.** A
  flick is the same intent as a slow drag past the line, and reading velocity
  means a quick short flick meant as a scroll can dismiss. That is the failure
  people describe as an app closing on its own, and the cost of getting it wrong
  is throwing away the video somebody was watching.
- **A fraction, not a number of pixels.** A thumb's idea of "most of the way
  down" scales with the phone; 240px is decisive on a small screen and a nudge on
  a tablet.
- **Zero height never dismisses.** `onSizeChanged` has not fired on the first
  frame, and every comparison against a fraction of zero is true — without the
  guard the first pixel of the first drag closes the screen.
- **The drag is read above the content**, which is right while the watch screen
  does not scroll and must be revisited the moment it does: a page with comments
  needs the drag to start only at the top, or scrolling up closes the video.
- **What is still missing is the miniplayer.** Dismissing keeps the sound going,
  deliberately — `release()` lets go of the connection and does not stop the
  service — but with no miniplayer the only way back to the video, or to stop it,
  is the notification. The web app has one; this does not yet.
