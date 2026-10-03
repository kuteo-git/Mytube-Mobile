# Mytube App — Project Charter

> Source of truth for every architectural decision. If a new decision contradicts
> this file, update this file rather than quietly going another way.
>
> **The dated record lives in [`docs/changelog.md`](docs/changelog.md)**: every
> change and its measurements, from 2026-08-29 on, verbatim. This file holds
> what is true now and the rules that record taught (§11). When a change teaches
> a rule that should hold for every future change, add the entry to the
> changelog and one line to §11 pointing at it. Before changing an area, search
> the changelog for it — most of this app's faults have been paid for once
> already.
>
> The server this app talks to is a separate repository — Local Mytube, at
> `/Volumes/Data2/git/Youtube`. Its own `CLAUDE.md` is the authority on everything
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
| Video tiers | Tried in the order **live → hls → local**. `local` is a file the *household* downloaded, served over the LAN; it was "phase 3" until a downloaded video said YouTube had refused it (changelog, 2026-08-31) |
| Everything on Data2 | Source and toolchain both. The internal disk has ~13 GiB free and Xcode alone is ~20 GB |

**The accepted consequence of "wifi only" plus "streams only": background audio
works around the house, not on a commute.** That was decided with the trade in
view. Do not quietly add downloading to the phone to work around it — reopen the
decision.

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
    ui/shell/            AppShell, bars, glass material, press, BarTravel
  androidMain/           Media3/ExoPlayer, MediaSessionService, foreground service
  iosMain/               AVPlayer, AVAudioSession, MPNowPlayingInfoCenter
  jvmTest/               the guards — see §8
iosApp/                  Swift entry point, hand-written .xcodeproj, PlayerGlass.swift
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
- **No DI framework.** Constructor injection by hand from one composition root
  (`AppContainer`), the same as the Go services do in `main.go` and the web app
  does with module singletons. Koin is a service locator whose missing
  registrations fail at runtime, which is the opposite of this project's habit of
  turning rules into compile errors.

### The platform seams

Designed once, with both platforms in view. That is the entire reason Android and
iOS are built together rather than one after the other.

| in `commonMain` | Android | iOS |
|---|---|---|
| `VideoPlayer` (incl. `narrate(clips)`) | Media3 controller to a `MediaSessionService`, `PlayerView` in `AndroidView`; narration on two `ExoPlayer`s used in turn, ducking the video | `AVPlayer` in `UIKitView`; narration on two `AVPlayer`s in the same `AVAudioSession` |
| background + lock screen | `MediaSessionService` + foreground service, `MediaMetadata` | `AVAudioSessionCategoryPlayback` + `UIBackgroundModes: audio`, `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` |
| `ApplyFullscreen`, `KeepScreenOn`, `TabGlyph` | window calls | view controller / `idleTimerDisabled` / SF Symbols |
| `GlassPane` / `GlassItem` | paints `glassSurface` | SwiftUI `.glassEffect()` over the whole hosting view, iOS 26+ (`LocalNativeGlass`) |

- **Narration is on `VideoPlayer`, not a second port.** Only the player knows the
  playhead and only the player can turn the video down. An empty list switches
  narration off; the list is replaced, not appended to.
- **`stop()` and `release()` are different acts.** Release hands back this
  screen's hold while the sound carries on (leaving the screen); stop ends
  playback (the close button). Disposal must never stop. The one exception:
  `release()` also removes this instance's `MPRemoteCommandCenter` targets.

`expect/actual` is how KMP supplies an implementation. It is not licence for `ui`
to reach for ExoPlayer directly.

## 4. Versions — read `gradle/libs.versions.toml` before changing anything

That file carries the full reasoning. The short version: **this is not "the
latest of everything"**, it is the one set that resolves, and several numbers are
deliberately older than what is published.

| | | why not newer |
|---|---|---|
| Kotlin | 2.3.20 | pinned *by Compose* — its klibs are built with it |
| Compose Multiplatform | 1.11.0 | 1.12.0 demands AGP ≥ 9.1.0 |
| AGP | 8.11.1 | **AGP 9.x is incompatible with the KMP plugin outright** |
| Gradle | 8.14 | AGP floor 8.13, Kotlin ceiling 8.14 |
| Ktor | 3.5.2 | 3.2.0 fails to dex below minSdk 30 |
| lifecycle | 2.10.0 | 2.11.0 demands AGP ≥ 9.1.0, same as Compose 1.12.0 |
| Backdrop (`io.github.kyant0:backdrop`) | 2.0.0-alpha03 | 2.0.0 needs compileSdk 37, 2.0.1 needs Kotlin 2.4.10 + Compose 1.12 |

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

**Release versions: one number, four places.** `versionName` is the tag without
its `v`; `versionCode` and `CFBundleVersion` are the release count and must rise;
`CFBundleShortVersionString` equals `versionName`. All four move together on
every release.

## 5. The server API

The gateway speaks REST/JSON outward — **there is no proto on this path**, and the
web app's generated `_pb.ts` files are unused by anything outside `gen/`.

- **Every request carries `X-User-Id`**, through `identify(userId)`. The web app's
  `shared/api/http.ts` explains why: *"a rule that has to be remembered forty
  times is a rule that will be forgotten once."* (Today it is still called by
  hand at each call site — a known debt from the 2026-10-03 review.)
- **The server address is typed by hand** on first run, like Home Assistant. The
  Mac's address is DHCP; mDNS dies on ordinary routers with nothing left to type.
- **HLS URL carries the device ceiling**: `…/hls/master.m3u8?max=720`. A phone is
  capped at 720p, and on iOS the cap *cannot* be applied in the client — native
  HLS gives no way to limit a level, so the server writes a shorter playlist. No
  `?max=` on a live URL or a local file.
- **When the app needs something the gateway cannot do, the work is in the server
  repository first.** Narration, `?from=`, the Missed chip, playlists writes,
  metadata refresh and storyboards all went that way: there was nothing to call.
- **When a request appears to be ignored, read the handler before varying the
  request** — and check which build of the gateway is actually running.

### Narration comes from the server, as a manifest

The web app drives narration in the browser (1,509 lines); that is **not** ported.

```
POST   /api/videos/{id}/narration?from=<s>   start, or retarget a running pass
GET    /api/videos/{id}/narration            { status, done, total, clips: [...] }
DELETE /api/videos/{id}/narration            cancel (204 whether or not running)
```

- **Progressive**: `GET` returns what is finished so far; clips are handed to the
  player on every poll.
- **From the viewer's position, then back for the beginning.** A seek retargets;
  the "far enough" threshold (30s) lives on the server only.
- **Only the close button cancels.** The switch off and the miniplayer leave the
  pass running — it writes lines the next viewing would otherwise pay for again.
- **A broadcast is narrated as simultaneous interpreting**: lines are said in turn
  as they arrive (~30s behind), keyed by `startsAtEpochMillis`; the toggle is
  absent when the stream has no captions (`liveCaptions`), and a remembered
  preference never starts one.
- **This is what makes background playback possible.** A manifest plus a few
  audio files is just playing music; a thousand TTS requests from a locked phone
  is something the OS kills.

## 6. Scope

**Tabs**: Home · Playlists · Settings, with Search beside them. **Routes**: Watch
(a layer, not a route), Search, Channel, Playlist, Saved, History, Subscriptions,
Profile, Voice, Language, Setup.

Deliberately absent, with reasons:

- **Storage, Activity, ranking constants, the proxy** — screens for *fixing the
  server*, and people fix servers sitting at a computer.
- **Downloading to the phone** — §2.
- **Equaliser and reverb** — 709 lines of Web Audio with no equivalent on
  Android. **Narration ducking stays**: without it the voice and the original
  audio play over each other.
- **Adding comments** — the gateway writes them into the household's catalogue,
  never to YouTube; under real YouTube comments it reads as a reply nobody will see.
- **Cast** — there is no receiver.

## 7. Language

All source, identifiers, **comments** and commit messages in English — the same
rule as the server charter, with no exception. Only display strings go through
i18n and have a Vietnamese translation. Conversation happens in Vietnamese; the
artifact does not.

### The dictionary is an interface, not a key lookup

`Strings` declares every word the app shows as a **property**; `EnglishStrings`
and `VietnameseStrings` implement it; `LocalStrings` provides the current one.

- **A missing translation cannot exist.** Add a property and every language stops
  compiling until it is supplied.
- **Not Compose Resources.** XML, a codegen step, and a lookup that fails at
  runtime for a key one language lacks.
- **`UntranslatedGuardTest` catches what the type cannot**: a literal in a
  composable. It cannot see a literal *outside* a composable (a formatter, a
  label helper) — those take `Strings` as a parameter, like `trackLabel`.
- **Units and grammar belong to the language.** Count suffixes are `K/M/B` and
  `N/Tr/T`; formatters take `Strings`. The web app once printed "3 ngàys trước".
- **Each language is named in its own words**, always, and upstream labels (a
  channel's sort names) are not translated.

Translations are **copied into Kotlin** rather than shared with the web app as
JSON. Expect the two to drift.

## 8. Testing

Written alongside, not after.

| layer | where | what it catches |
|---|---|---|
| Unit | `commonTest`, `kotlin.test` | pure decisions: mapping, formatting, ViewModel branches |
| Guard | `jvmTest` | source scans for rules no type can hold |
| Preview | `@Preview` on every screen | how a screen looks in every state |
| Driven loop | emulator / simulator | what only a running app shows |

Guards, each **proven to fail** before it was believed: `ArchitectureGuardTest`
(dependency direction), `UntranslatedGuardTest` (literals in composables),
`PressGuardTest` (every `clickable(` answers a finger, or says `press-guard:
<reason>`), `ScrollRoomGuardTest` (a horizontal scroll row carries padding so a
press bloom is not clipped), `SubtitleGuardTest` (Media3's `SubtitleView` hidden ⇔
`rendersSubtitles = false`). They read source at run time, so `jvmTest` declares
`src` as a task input — otherwise Gradle calls them up to date. Guards count
**lines of code**, not comments.

- **No Compose UI tests.** Previews cover what a screen looks like; ViewModel
  tests cover what it does. Each screen is split `XScreen(viewModel)` →
  `XContent(state, callbacks…)`.
- **Every screen has previews, more than one**: loading, empty, failed, and a
  Vietnamese one.
- **A decision two places must agree on becomes a named pure function with a
  test** — `wholeSeconds`, `barTravel`, `tabAt`, `tabPress`, `dragOutcome`,
  `miniPlayerBottomInset`, `seekLineFromBottom`, `fillFromPinch`, `frameAt`,
  `scrubPreviewScale`, `shouldRecoverStall`, `channelToken`, `progressDue`,
  `rebalance`.
- **Regression tests go red on the reported symptom first.** Test bodies for wire
  mapping are copied from the running gateway, not invented.
- **A `jvm` target exists only to run tests.** It does not contradict "no desktop".

**Verification command:**

```sh
./gradlew jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

**Nothing is called done because it compiled.** Background audio, lock-screen
controls and ducking have to be heard on real hardware with the screen off.

## 9. Toolchain

Everything on the external volume; `source env.sh` before working.

```
/Volumes/Data2/mytube-app/          this repo
/Volumes/Data2/Xcode-26.6.0.app/    Xcode, selected with xcode-select
/Volumes/Data2/dev/jdk/             Temurin JDK 21
/Volumes/Data2/dev/android-sdk/     platforms 35 + 36, cmdline-tools
/Volumes/Data2/dev/gradle/          GRADLE_USER_HOME
/Volumes/Data2/dev/konan/           KONAN_DATA_DIR
/Volumes/Data2/dev/mytube-release.jks   Android release key, named by gitignored keystore.properties
```

- **`env.sh`, not `~/.zshrc`.** A shell profile outlives what it points at. The
  Xcode Kotlin build phase sources it too — Xcode inherits no `JAVA_HOME`.
- **compileSdk 36** because `androidx.navigationevent` refuses anything older.
- **Android release**: signed when `keystore.properties` exists, unsigned
  otherwise (never the debug key); `isMinifyEnabled = false` on purpose.
- **iOS**: `iosApp/iosApp.xcodeproj` is hand-written; the framework is static.
  `tools/ios-ipa.sh` archives and zips the IPA by hand (no `-exportArchive`,
  which embeds the 7-day profile). The household uses a free Apple ID, so the
  phone is kept signed by **AltServer/AltStore**; `tools/altstore-setup.sh`
  guides the first install and never asks for the password.
- **Simulator builds need `-destination 'platform=iOS Simulator,id=<udid>'`**;
  `-sdk iphonesimulator` alone also builds x86_64 and fails.
- **Driving the simulator**: `tools/sim-touch.swift` (CGEvent tap/drag/hold —
  `idb`'s tap does not reach this Compose scene; activate the Simulator first) and
  `tools/sim-ui.py` (labels and centres from `idb ui describe-all`). Find
  elements by label at the moment of the tap, not by remembered coordinates.
- **When Data2 is not mounted, this project cannot be built.**

## 10. Status (v0.1.11, 2026-10-03)

- **Runs on Android (emulator and phone) and iOS (iPhone 16e simulator and the
  household's iPhone, release build via AltStore).** 247 tests, five guards.
- Built: Home with chips (incl. Live and Missed), Search (library + YouTube),
  Channel, Watch (layer with drag-to-miniplayer, controls, subtitles, narration,
  storyboard scrub preview, pinch-to-fill, double-tap seek, comments, up next,
  autoplay with a queue), Playlists (create/rename/delete, save sheet), Saved,
  History, Subscriptions, Profile, Settings (server, voice, language, feed mix).
- Background audio, the lock screen and narration have been heard on real
  phones.
- **Known open items**: an iOS-only one-frame flicker of the watch layer during
  the drag (changelog 2026-09-21, unconfirmed fixed); `X-User-Id` attached by hand
  at each call site; `ui → data` import of `ServerNotConfigured`; defaulted flags
  on `WatchSession`; playlist and channel ViewModels accumulating in the activity
  store; three screens without previews; the glass is flat below Android 12 (blur
  needs API 31, lens API 33) — accepted.

## 11. Rules learned

Each line is a rule a past fault paid for. The changelog heading in brackets has
the measurement. Search `docs/changelog.md` for it before arguing with the rule.

### Honesty of the screen

- **Nothing drawn that is not wired, and nothing wired that is not drawn.** A dead
  button is forbidden (server charter §5); a null callback draws no control at
  all. An action that does something real must have a page that shows the result.
  [The overflow menu was a dead button; Save had nowhere to lead]
- **Never blame YouTube for something that is ours.** Every new wire field or tier
  gets read in the mapper and covered by a mapping test; four times a declared,
  unread field put "YouTube will not hand this over" on screen. [A live broadcast
  said YouTube had refused it; A downloaded video said…; The queue was a list of
  ids…]
- **Upstream (YouTube) rows are written to the catalogue before they are opened**
  (`POST /api/videos/external`), and an empty returned id is a refusal.
- **A state is a change of kind, not of shade.** `surface` and `surfaceHover` are
  six units apart; selected = inverted solid surface, and the content colour
  follows the surface. Filled vs outline glyphs for on/off. [The lit state had to
  be a change of shape; Save's "Saved" state was a blank white pill]
- **`Icon` tint repaints every path** — anything two-toned goes through `Image` or
  sits beside the icon. [Icon's tint repaints every path]
- **"None yet" and "still asking" are different answers**; an empty state says
  what would fill it; `Idle` is not an empty result.
- **A failure that can never succeed must not be swallowed** as fire-and-forget.
  Check wire types (int32 vs Double). [A report that could never succeed]
- **A playback error is shown** ("could not play" + Try again, in place of the
  controls), and a recovery in progress is not one.

### Compose state and lifetime

- **`remember` with no key captures the first value for ever**; read live values
  from snapshot state (`layoutInfo`), not from captured parameters inside
  `derivedStateOf`. [The feed stopped at 48]
- **A `remember` inside a lazy item is a cache**, gone when the item scrolls away.
  State that must outlive scrolling is hoisted above the list. [A `remember`
  inside a lazy item…]
- **A state read inside `SideEffect` subscribes nothing**; read in composition.
- **Do not unmount what holds scroll state** to show loading — keep `Ready` with a
  `switching` flag. [A topic switch destroyed the chip row]
- **`viewModel()` lives in the activity store and outlives the screen.** Per-video
  holders use `remember(key)` + `DisposableEffect` + explicit `close()`; this is
  correct only because the activity handles rotation itself (`configChanges`).
- **Composition stops on iOS in the background.** Anything that must happen with
  the screen off (autoplay advancing) runs in a coroutine on a long-lived holder,
  keyed by a sitting, never by constructing something in composition.
  [Autoplay stopped at the end of a video with the screen off]
- **Lazy keys must be unique**: appended pages are merged with `appendNew` /
  `distinctBy`; a repeated key is a SIGABRT, not a repetition.
- **The last question wins, not the last answer**: switching cancels in-flight
  jobs; pages merge into the state as it is *now*; `CancellationException` is
  rethrown, never caught as a failure.
- **A page's contents change by more than arrival** — anything composed under the
  watch layer must refresh on edits made elsewhere (`playlistEdits`).
- **No default on a flag where the rare value matters** (`startAtBeginning`,
  `showSurface`, `fromSeconds`): a defaulted flag is the one call site forgets.
- **Disposal must not stop playback**: the new screen composes before the old one
  disposes.

### Glass and rendering

- **Three materials**: `BarBackdrop`/`liquidGlass` *sample* (only for panes drawn
  outside the recorded layer); `glassControl`/`glassSurface` *paint* (anything on
  the page or over video on Android); `GlassPane` is *platform-drawn* on iOS 26
  (only for still chrome over the video). [One material, two kinds; The glass over
  the picture is finally glass]
- **A sampled backdrop inside the recorded layer is a Skia stack overflow, not a
  bad look.** Draw every sheet, alert and menu from `App.kt`, outside every
  recording. `glassSource()` is a no-op under `LocalGlassRecording` and when
  `LocalGlassSourceActive` is false (only the arriving page records). [A sampled
  backdrop inside the recorded layer is a crash; Only the arriving page records]
- **No popups** (`ModalBottomSheet`, `DropdownMenu`, `AlertDialog`): their own
  coordinate space breaks sampling. Use `GlassSheet`, `GlassAlert`, `MenuHost`,
  always composed and told whether they show, drawn last.
- **Liquid glass is mostly not blur**: blur 8dp, lens with depth and chromatic
  aberration; refraction height ≤ smallest corner radius. One `GLASS_BLUR`, one
  `TINT_GLASS`; modal and page grounds use `TINT_MODAL`. A full-screen ground has
  no rim (`drawPlainBackdrop`).
- **The backdrop records `drawRect(Tokens.bg)` first**, or gaps show as holes.
- **Panes that share an edge share height, tint, margin and radius** (`Size.topBar`,
  `GLASS_MARGIN`, `Size.miniGap`, concentric radii). Eight units between two
  stacked panes is a seam people report.
- **No design-system bypass**: no Material `Button`, `Slider`, `OutlinedTextField`
  in `ui/`; use `GlassButton`, `GlassSlider`, `GlassTextField`, `GlassPill`.
  Check error/empty/fresh-install states — they are where Material survived.
- **Only one place paints the status bar region**, last child of the root Box;
  `SystemBarStyle` names the background, not the glyphs.
- **A hand-added `CALayer` animates implicitly** — gesture-driven layers wrap
  frame changes in `withoutImplicitAnimation`. Symptom: the right value, late.
- **Two `VideoSurface`s on one player steal it**; exactly one holds the surface.
- **Media3 `PlayerView` draws subtitles itself** — hidden; captions come from the
  Compose `SubtitleOverlay` on both platforms.

### Gestures and press

- **A press moves what is painted.** `Modifier.pressable` everywhere; where the pane
  belongs to a container, the container claims it via `LocalPressHost`. Squash
  modifiers go *outside* `animateContentSize` and outside the painting node.
  A press blooms by `PRESS_INSET`, and scroll rows leave that much room.
- **Two gestures that differ get two `pointerInput`s; one gesture read two ways
  gets one recogniser** (tap/double-tap). Consume only once the gesture is
  certainly yours (pinch waits for a second pointer).
- **A full-screen sibling swallows touches even if it consumes nothing**; observe
  from an ancestor's Initial pass instead ("a lid vs a doorbell"). A duplicated
  root Box made every tap dead with correct pixels.
- **Tap targets**: 48dp tall; grow sideways. A readout is not a button.
  Drawn-later siblings win hit tests — `zIndex` when a control overlaps the seek
  bar's target. The whole visible field is the field, not just the text line.
- **Zero size never dismisses**; drag thresholds are fractions plus a velocity.
- **Back is one handler in `App`**, ordered outward; disabled (not empty) when
  nothing is left so the app can close. Local handlers only for non-navigation
  state (an open sheet). `Route.depth` sets slide direction; a page reached from a
  page is depth 2. `openChannel`, `collapseWatch` are the only ways to do those
  acts.

### Layout arithmetic

- **When two places must agree about a number, the number is a function**
  (`miniPlayerBottomInset`, `seekLineFromBottom`, `tabContentPadding`). A comment
  saying they must agree was never enough.
- **A number already applied somewhere is the easiest to double-count** — check
  the inset/gap is not inside the component already.
- **Clear what is drawn, not the touch target.**
- **Text in a fixed-height row sets `lineHeight`**, or the font's leading pushes
  the row taller / the ink off-centre.
- **A `Column` that runs out of room stacks what is left** — anything that can
  overflow scrolls.
- **Skeletons use the real composable's measurements**, and never draw what the
  host already holds open (the player's 16:9 slot).
- **Keyboard**: manifest `adjustNothing` + `imePadding()` on API 30+;
  `SOFT_INPUT_ADJUST_RESIZE` below 30 (no IME inset there). Pad the *centring
  box*, not the pane. Forms scroll with `heightIn(min = maxHeight)`.
- **A text field focuses at 0** — use `TextFieldValue` and put the caret at the
  end of a prefilled value; a fix in a component only covers its callers.
- **Platform-drawn text is narrower than Compose's measurement** — stacked lines
  cross with `alignStart`.

### Player

- **Seek goes into `setMediaItem`, not between it and `prepare`.** Resume reads
  the server's `watchPositionSeconds`, never `duration × fraction`.
- **`load` buffers; `play` is separate** on both platforms; `playWhenReady` is
  set from intent on every load.
- **A video that has ended is not stalled** (`shouldRecoverStall`); a broadcast has
  no end, no zero and no progress to report; draw LIVE, not a bar.
- **Lock screen**: duration travels on every tick; late artwork applies only if
  still current; remote-command targets are removed on release.
- **Subtitles are attached at load; language and disabled-flag move together**;
  never `SELECTION_FLAG_DEFAULT`.
- **Narration plays from two players in turn** (prepare the next on the idle one);
  on advancing to another video, `narrate(emptyList())` first.
- **Keep the screen on only while the expanded player is playing**, via the
  window flag / `idleTimerDisabled`, as state.
- **Preferences per device** (subtitles language, narration, autoplay) live in
  `PreferencesRepository`; a per-sitting choice (fill) does not persist.

### Measuring

- **A loop that is green for the wrong reason is worse than a red one.** Assert
  the other half of the state too; a signal absent in two situations cannot tell
  them apart. Use a cold trigger (an untouched up-next video) for loading states.
- **Pick the instrument that cannot be wrong about which node it is**: Compose
  semantics / `positionInRoot` probes, a marker colour, the seek bar's pixels.
  For a question about shape, a magnified crop; for text, read the text.
- **Version is part of the measurement**: check which binary is running (gateway
  build time; iOS bundle path changes; `--terminate-existing` on launch).
- **Android**: `adb shell input motionevent` holds a touch; two fingers via
  `sendevent` on `event1` slots with `ABS_MT_PRESSURE` (no `BTN_TOUCH`), mapping
  `(X,Y)→(1080−Y, X)` in landscape; `-gpu host` survives rotation; pause the
  video before diffing presses; screenshot before reading code when a control in
  the tree does not respond (keyboard panels sit outside the app window).
- **iOS device crash logs**: `xcrun devicectl device info files --domain-type
  systemCrashLogs`, then `device copy from`.
- **macOS is case-insensitive** — `a.png` overwrites `A.png`.
- **`npx tsc --noEmit` does not check the web project; `tsc -b` does.**
