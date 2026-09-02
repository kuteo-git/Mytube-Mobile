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

## The watch screen grew controls, opinions and a rail (2026-08-29)

Measured on the emulator against the running library, one video (`gEWF0LL4IPA`,
2:20):

| | |
|---|---|
| tap the bar at 76% | **109.2s of 140s** |
| drag to 37% | **50.9s** |
| skip buttons | ±10s |
| Save | `pinned: true` on the server |
| Like, pressed twice | `LIKE` then `NONE` |
| progress, unprompted | `watchProgress 0.0749`, `watchPositionSeconds 10` |

- **A readout is not a control, and that was the whole gap.** The bar under the
  picture displayed a position and could not be moved, so the only way to go
  anywhere in a video was to reopen it. It is now over the picture and takes both
  a drag and a tap — **two `pointerInput` modifiers, not one branch**: the
  gestures are recognised separately, and deriving a tap from a drag that never
  passed the slop threshold is how the tap silently stops working.
- **A tap on the picture shows the controls; it does not play or pause.** The
  server charter draws that line for touch, and the reason is that a finger has
  no hover — a phone where tapping the picture pauses is a phone where checking
  how far through you are stops the video. Play/pause is a button.
- **A paused video keeps its controls.** Hiding them leaves a still frame with no
  sign the app is running, and the thing somebody paused for is usually the
  button to start again.
- **The lit state had to be a change of shape.** `surface` and `surfaceHover` are
  `0x212121` and `0x272727` — six units apart, which the design system uses for a
  pointer hovering and which is invisible as a state. Measured: pressing Like set
  the reaction on the server and *looked like nothing had happened*. The thumb
  and the bookmark are now filled when on.
- **The thumb is centred, and it was drawn in the wrong place for a release.**
  The parent `Box` centres its children, so a box occupying the filled fraction
  was centred in the bar rather than starting at its left edge: the thumb sat at
  55% over a video twelve per cent through, with the red fill beside it
  disagreeing. `align(CenterStart)` is load-bearing.
- **Every action is drawn first and sent second, and put back if refused.** These
  are statements about the viewer's own opinion; a control that waits for a round
  trip before lighting up feels broken. Keeping a lit button over a like the
  server rejected would be worse than never lighting it, so a failure reverts.
- **Progress is reported every ten seconds of playback, and once more on the way
  out.** Ten because what it feeds — Continue watching, and the ranker's WATCH
  signal — cannot tell; the report in `onCleared` is the one that matters most,
  because closing the screen is exactly when somebody stops watching.
- **Up next is fetched after the stream, not beside it.** It is a second round
  trip nothing on screen waits for, and running it concurrently would put it in
  front of the one call a viewer is actually waiting on. A rail that fails stays
  empty.
- **The rail is inside the same `LazyColumn` as the details.** Twenty entries is
  twenty thumbnails, and a `Column` would fetch every one before the viewer had
  scrolled to any.
- **Share is not drawn.** It needs a platform share sheet on each side, and §5
  of the server charter forbids a button that does nothing outright.

### `./gradlew check` cannot be green on this machine

`check` includes `linkDebugTestIosSimulatorArm64`, and linking a Kotlin/Native
binary needs **full Xcode** — this machine has only the Command Line Tools, so it
fails with `MissingXcodeException` on `xcrun xcodebuild -version`. This is not a
fault in the code and it is not fixed by a Gradle flag.

The verification command until Xcode is installed is therefore:

```sh
./gradlew jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

which covers the unit tests, both guards, the Android build and the iOS
*compile*. What it does not cover is iOS linking and `iosSimulatorArm64Test` —
recorded as uncovered rather than quietly dropped, because iOS is where this app
differs most.

## The miniplayer, and two things it uncovered (2026-08-29)

Dragging the watch screen away already left the sound playing, and that was half
a feature: the audio carried on with nothing on screen to say so, and the only
way back to the video — or to stop it — was the notification. So the watch screen
is now **collapsed, not dismissed**.

Measured on the emulator, one 2:20 video: drag down → the bar appears above the
tab bar with the picture still running, `state=PLAYING`; tap the bar → full
screen again; press X → `state=NONE(0)` and the server holds `56s / 0.399`, the
position the playhead was at.

- **`Route.Watch` is gone; a `WatchSession` sits beside the route.** A route is
  somewhere you are, and this is something that keeps playing while you go
  elsewhere. Modelling it as a destination is what made *leaving the screen* and
  *stopping the video* the same act.
- **The picture keeps playing in the bar** — the same `VideoSurface` pointed at
  the same player, because `PlayerView` rebinds on update and the connection
  never drops. A still thumbnail would be cheaper and would show a paused video
  as though it were playing.
- **`stop()` had to become its own thing, separate from `release()`.** That
  separation *is* the miniplayer: release hands back this app's connection while
  the sound carries on, which is what leaving the screen means; stop ends the
  playback, which is what the close button means. Measured before it existed:
  pressing close removed the bar and left the video playing with nothing on
  screen at all — worse than the fault the miniplayer was written to fix.
  - **Disposal must not stop.** Opening another video composes the new ViewModel
    — which loads and plays — *before* the old one's `onDispose` runs, so a stop
    there would kill the video that had just started.
  - On Android, `stop()` alone leaves the item loaded and Media3 keeps the
    session and its notification alive over a player with nothing to play;
    clearing the queue is what ends it. On iOS the equivalent is replacing the
    item with null, or Now Playing keeps showing a closed video.

### `viewModel()` was leaking a player per video

`androidx.lifecycle.viewmodel.compose.viewModel()` stores the instance in the
**activity's** `ViewModelStore`, where it outlives the screen entirely — so every
video opened left another `WatchViewModel` behind, each holding a live connection
to the playback service, and `onCleared` never ran on any of them. The final
progress report lived in `onCleared`, so it never ran either.

The watch screen now uses `remember(videoId)` with a `DisposableEffect` calling
an explicit `close()`. That is only correct because the activity declares
`configChanges` for rotation and is never recreated under it; without that line
in the manifest this would restart the video on every turn of the phone.

### A report that could never succeed, swallowed as though it might

The gateway declares `positionSeconds` as an **int32**. The app sent a Double, so
Go refused the whole body with 400 — and `recordProgress` is deliberately fire
and forget, swallowing failures on the reasoning that a missed report costs a
stale Continue watching entry. That reasoning is right for a network blip and
wrong for a request that can never work: measured, after forty-five seconds of
playback the server still held the position from before the app was opened, and
nothing anywhere said why.

`wholeSeconds` rounds at the edge and refuses negatives — a player reports a
position before it has loaded, and on some that is negative, which is not a place
in a video. It is a named function rather than an expression inside the request
builder so `ProgressBodyTest` can assert it without a network.

## The other three tabs (2026-08-29)

Measured on the emulator against the running library: Subscriptions lists the
household's channels alphabetically with counts (`1.8Tr`, `127N` under the
Vietnamese formatter), History shows what was watched with the red bar the
progress writes now feed, and Settings switches language — which survives a
force-stop and takes the tab bar with it.

- **One frame, three screens.** `tabContentPadding`, `ScreenTitle`, `EmptyState`
  and `TabScaffold` exist because the same arithmetic was about to be written a
  fourth time. Both bars float over the content, so every list starts below one
  and ends above the other, and the top bar is 56dp *plus* the status bar — the
  server charter records the web app learning four separate times that this
  number belongs in exactly one place.
- **Subscriptions lists channels, not their uploads.** New uploads from followed
  channels already have a fixed share of Home (`slotFreshSubscribed`), so
  repeating them here would be a second feed with the same contents and nothing
  to tell the two apart. This tab is for *reaching a channel*.
  - Sorted by name **here**, not asked for in that order: a list somebody scans
    for one channel wants one predictable order, and sorting on the server would
    change that endpoint for every other reader.
  - **A subscriber count is drawn only when there is one.** A row reading "0
    subscribers" states a fact nobody measured, and most rows here have none —
    a flat listing does not carry it.
  - **Pressing a row does nothing yet.** The channel screen is not built, and a
    row that navigates nowhere is the honest state until it is. The list is
    already the answer to "who do I follow", which is most of what the tab is
    for.
- **An empty tab says two things, not one.** A bare "nothing here" reads as a
  fault; the second line says what would put something here.
- **Settings is deliberately four rows.** Storage, Activity, the feed mix, the
  ranking constants, the proxy, narration and speech are screens for *fixing the
  server*, and people fix a server sitting in front of a computer. What is left
  is what belongs to this device: where its library is, and what language it
  reads in.
  - **The server row shows the address, not "Configured".** Somebody opening it
    is checking *which* machine, usually because it stopped answering.
  - **Each language is named in its own words, always.** Somebody who pressed the
    wrong row is looking at an interface they cannot read, and "English" written
    in English is the way back out.
  - **The selected row is a filled dot.** The same lesson the Like button cost:
    a background cannot carry a state when the two surface tokens are six units
    apart.
- **Empty means "follow the device", and it stays empty.** Resolving the language
  on first launch and writing it back would freeze a phone into whatever it
  happened to be set to that day. Nothing is drawn until the stored value has
  been read, so English never flashes at somebody who chose Vietnamese.
- **The miniplayer crosses tabs**, which is the whole point of holding the
  session above the routes. Measured: collapse on Home, switch to Subscriptions,
  `state=PLAYING` and the bar still on the tab bar.

## Search and the channel page (2026-08-29)

- **The top bar's search box was never a field.** It is a button shaped like one,
  and the search screen's real field lands exactly where it was — so the two
  never appear together and there is no inert box behind the one being typed
  into.
- **Typing is debounced by 300ms, and the pending job is cancelled.** A search is
  a full-text query *and* a recorded behaviour signal — the gateway records one
  per request — so typing "nothing phone" without this is thirteen queries and
  thirteen signals for one intent, and recsys would be learning every prefix of
  what somebody typed. Cancelling matters as much as the delay: without it the
  wait postpones each request without reducing how many are made.
  - The screen blanks only on the *first* search. Replacing results with a
    spinner on every keystroke makes a list that is mostly still correct flicker
    away while somebody refines a word.
  - `Idle` is its own state, not an empty result. Nobody has asked anything yet.
- ~~**There is no "On YouTube" half.** The web app splits its results, but the
  gateway has one search route and it answers from the catalogue only.~~
  **Wrong, and corrected on 2026-09-01** — `GET /api/discover` exists and the web
  app has always called it. See "Search reaches YouTube again" below.
- **The channel page's uploads come from YouTube, not the catalogue** — the
  gateway asks upstream because a scan only brings in the newest few dozen, so a
  page served from the catalogue would cap a channel at that number with nothing
  to say why. The consequences are visible in the DTO: an absolute
  `thumbnailUrl`, no channel per row, no user state.
  - So `imageModel` decides per image whether a path is inside the library or an
    address upstream. Deciding it in one function means a card does not have to
    know which kind of list it is in.
  - The channel is passed *into* the mapper because the endpoint sends one
    channel for the whole page, not one per row.
  - **The sort labels are not translated.** They arrive as "Latest", "Popular",
    "Oldest" in whatever language upstream answered in, each with an opaque token
    beside it; translating one would mean guessing which of the three it is, and
    being wrong the day a fourth appears.
  - **No banner**, though the gateway sends a `bannerPath`. It is 200dp of
    decoration above the one thing the screen is for, and on a phone it pushes
    the first video off the fold.
- **The channel page carries its own back arrow, drawn over every state.** It has
  no top bar and no tab bar, so without one a channel that would not load is a
  dead end — and Android's system back leaves the app entirely, while iOS has no
  system back at all. The way out has to be on the screen.
- **The miniplayer moved above the routes.** It now shows on Home, Search and the
  channel page, and deliberately not on the setup screen: somebody typing an
  address is fixing the connection this video came through, and a bar playing
  over that form is in the way of the one thing that screen is for.

## The iOS half exists in Kotlin and has nowhere to run (2026-08-29)

Everything on the Kotlin side is written and compiles for
`iosSimulatorArm64`: the player, the audio session, the lock-screen entry, the
settings store, the language, and `MainViewController` — the counterpart of
`MainActivity`, building the same container and handing it to the same `App`.
The Swift entry point and `Info.plist` are in `iosApp/iosApp/`.

**What is missing is the Xcode project, and it cannot be written here.** This
machine has the Command Line Tools and not Xcode, so there is no `.xcodeproj`,
nothing has ever run on a simulator or a device, and `linkDebugTestIosSimulator`
fails on `xcrun xcodebuild -version`. A hand-written `project.pbxproj` was
considered and refused: it could not be opened or built here, so it would be a
file claiming to work with nothing behind the claim.

None of the iOS behaviour below is measured. It is written to the platform's
documented contract and marked untested, which is not the same as working.

- **Background audio needs two things and either alone is silence**:
  `AVAudioSessionCategoryPlayback`, set when a player is built, and
  `UIBackgroundModes: audio` in the Info.plist. The category is set at player
  construction rather than at launch because it is global to the process, and
  claiming playback while nothing plays takes audio focus from whatever else the
  phone is doing.
- **The lock screen is two APIs, not one.** `MPNowPlayingInfoCenter` carries the
  text and the scrubber; `MPRemoteCommandCenter` carries the buttons. Setting
  only the first gives a lock screen that reads correctly and whose play button
  does nothing. This is the counterpart of Android's `MediaMetadata`, and it
  exists because that side measured what its absence looks like: a notification
  reading "Mytube is running".
  - **`togglePlayPause` is registered beside play and pause**, because headphone
    buttons and car stereos send toggle rather than either — a lock screen that
    works while a steering wheel does not is worse than neither.
  - **The artwork is deliberately not fetched.** `MPMediaItemPropertyArtwork`
    takes a `UIImage`, so showing one means downloading and decoding here, on a
    URL that is on the house wifi and unreachable the moment the phone leaves it.
    A lock screen with a title and no picture is complete.
  - **`release()` does not clear it and `stop()` does** — the same split as
    Android's, for the same reason: releasing hands back this app's hold while
    the sound carries on, and a lock screen that empties while audio plays is the
    miniplayer fault in reverse.
- **`NSAllowsLocalNetworking`, not `NSAllowsArbitraryLoads`.** The gateway has no
  TLS and the charter leaves media URLs unprotected because the LAN is trusted;
  the narrow key permits cleartext to local addresses and refuses it to the
  internet. `NSLocalNetworkUsageDescription` is required too — without it iOS 14+
  refuses the network with no error a person could act on.
- **Portrait only, matching Android.** The activity there declares
  `configChanges` for rotation, which is what makes holding the watch ViewModel
  in `remember` correct; letting iOS rotate would be the two platforms behaving
  differently in the one place that costs a restarted video.

### To finish it, on a machine with Xcode

1. Create an iOS App target named `iosApp` in `iosApp/`, using the `Info.plist`,
   `iOSApp.swift` and `ContentView.swift` already there.
2. Add the `composeApp` framework to it (`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` as a build phase, the standard KMP setup).
3. Then, and only then, the claims above become measurable — and the ones that
   matter are the charter's: play, lock the screen, and confirm the sound
   continues and the lock-screen controls work; take a call mid-video, hang up,
   and confirm it resumes.

## Narration on the client (2026-08-29)

The server does the reading, translating, synthesising and fitting; this app asks
for a pass, polls it, and plays the clips over the video. Measured on the
emulator against the running library: pressing the button started a pass the
server logged, and the label read **"Đang chuẩn bị 121/160"** — resumed from
what an earlier run had already written to disk rather than repeated.

- **`narrate(clips)` is on `VideoPlayer`, not a second port.** Only the player
  knows where the playhead is and only the player can turn the video down. A
  separate `Narrator` port would need both, so the seam would exist to be
  threaded through rather than to separate anything.
  - An **empty list switches narration off**, deliberately the same call:
    "narrate nothing" and "stop narrating" are one state, and two methods for it
    would be two states that can disagree.
  - The list is **replaced, not appended to**. The server's pass grows while it
    runs, so this is called with a longer list every few seconds.
- **The Android narrator polls rather than schedules.** A timer per clip has to
  be cancelled and rebuilt on every seek, every pause and every list update. Four
  checks a second against the playhead cannot drift, needs no special case for a
  seek, and picks up a clip appended a moment ago on the next tick.
- **Switching narration off does not stop the server's pass.** It is writing
  translations and audio to disk that the next viewing will use; abandoning it
  halfway means paying for the same lines twice.
- **The clips are handed over on every poll, not at the end.** A pass takes
  minutes and the first lines are ready in seconds.
- **The button is first in the action row**, against the convention that puts
  Like first. Its label grows to carry the pass's progress, and in fourth place
  that label ran off the edge of the phone — "Đang chuẩn bị" with the numbers cut
  off. A row that scrolls is not an excuse for hiding the one control the screen
  is for.
- **iOS returns `Unit` and says so.** The equivalent is a second `AVPlayer` in
  the same audio session and it is straightforward — and there is no Xcode
  project here, so nothing on that platform has ever played a sound. Writing it
  untested and calling it done would put the app's whole reason for existing
  behind an unmeasured claim.

**The ducking has not been heard.** The emulator runs with `-no-audio`, so what
is measured is the arithmetic (`levelsFor`, `clipAt`, with tests) and the fact
that clips are fetched and started. Whether the voice sits right against the
video is a question for a real phone, and it is the thing most likely to need a
number changed.

## Four bugs the phone found (2026-08-29)

Narration was confirmed audible on a real device; these came back with it. All
four were the same shape — something drawn that was not wired, or wired in a way
that removed it.

### A live broadcast said YouTube had refused it

`live` was parsed in the DTO and **never read** in `toDomain`, so a broadcast
fell through to `NothingPlayable`, which the watch screen turned into "YouTube
will not hand this over / no_tier". That is a lie in the worst direction: it
names an upstream refusal for a video upstream is serving perfectly well, and it
offers no retry.

Measured after the fix, on a real broadcast through this server: it plays,
`state=PLAYING`.

- **Live is checked before HLS.** The server never sends both — a broadcast
  answers with the live tier alone, because offering `hls` beside it would have
  the player climb toward a playlist built from adaptive tracks the broadcast
  does not publish. The order is belt and braces and it is the honest one.
- **No `?max=` on a live URL.** That parameter is read where the *ordinary*
  master playlist is written; the live master comes from a different route that
  has never seen it, and the charter's measurement of a live ladder is 144p–720p
  — already under a phone's ceiling.
- **A broadcast has nothing to resume to and nothing to report.** Its zero is an
  hour ago and its end is now, so it opens at the live edge; and its "fraction
  watched" is a position inside a sliding window, which would put a nonsense
  figure in Continue watching.
- **The bar is replaced by a red dot and the word LIVE.** The server charter
  records what drawing one anyway looks like: `position / duration` on a stream
  26 minutes into a live video is **155,700%** — solid red from the first second,
  reading "25:57 / 0:00" beside it.

### `indicator = {}` does not move an indicator, it removes one

All three pull-to-refresh screens passed it, with a comment saying the default
"would land under the floating top bar". The gesture worked and refreshed the
list, and **nothing on screen ever said so** — which reads as a pull that did
nothing, so people pull again. `TabRefreshIndicator` offsets it by the same
arithmetic as `tabContentPadding`.

### The overflow menu was a dead button

Drawn on every card and wired to nothing, which is the one thing §5 of the server
charter forbids outright. It now opens **Lưu · Không quan tâm**, both real
endpoints; measured, "not interested" removes the row and the feed goes on.

- **Null callbacks draw no button at all.** A card in a list of upstream results,
  where neither action means anything, has no dot rather than a dead one — the
  channel page and the up-next rail are that case.
- **History gets Save and not "not interested".** It is a record of what was
  watched, and telling the ranker off from a list of things somebody chose to
  watch is the wrong signal in the wrong place.
- **A removed row is not put back if the request fails.** It is gone from the
  viewer's page either way, and returning it a second later is the more confusing
  outcome; the next feed load is where the truth shows.

### Fullscreen did not exist

`ApplyFullscreen` is `expect/actual` — the case that rule is for, since there is
no object with methods, only a call into the platform's window. Measured: the
emulator reports `cur=2400x1080` and the screenshot comes back landscape with no
system bars.

- **It takes the state, not an event.** An event has nothing to undo, and a
  screen disposed while fullscreen would leave the phone sideways with no bars.
- **`LocalContext` is not the Activity** — Compose wraps it, sometimes several
  times. Unwrapping and returning null on failure means a preview, which has no
  Activity at all, draws instead of crashing.
- **In fullscreen the back arrow leaves fullscreen, not the video.** Somebody who
  filled the screen wants out of *that* first.
- iOS is a no-op that says so: rotation there is a property of the view
  controller and of the Info.plist, and neither can be run without Xcode.

## Compared with the web, screen by screen (2026-08-29)

Screenshots of the web app on a phone were the first real reference this project
has had. Reading them beside the app found more than the four bugs before it, and
almost all of it was the same failure: **the app was built from the design
tokens, and the tokens describe pieces rather than screens.** A card's radius was
right; the order and shape of the things around it were guesses.

The lesson is recorded rather than the corrections alone: for anything the web
app already does, read `web/src/features/watch/ui/*.tsx` and copy the structure.
Guessing at it produced eight differences on one screen.

### The control bar

It was a centre play button flanked by two ±10s circles — the shape a phone's
*system* player uses. The web app's is a thin progress line with **one row under
it**: play, next, the clock on the left; audio, settings, fullscreen on the
right. The old arrangement put the three most-pressed controls over the middle of
the picture, which is exactly where somebody is looking.

The "next video" slot holds forward-ten-seconds here. Next needs a queue this
app has not got, and a button that does something beats one that matches a
layout and does nothing.

### The menus belong on the player

The gear now opens a panel **under the picture**, so the video keeps running
above whatever is being changed — a sheet over the video would have somebody
changing the narration setting while looking at a grey rectangle.

Narration moved into it from the action row. As a pill its label had to carry the
pass's progress, and a label that grows in a row that scrolls is a label that
runs off the edge of the phone — measured: "Đang chuẩn bị" with the numbers cut
off. In the panel it is a switch with a progress bar, which can say *how far*.

**Subtitles and Autoplay are in the web's panel and deliberately not in this
one**: this app renders no subtitle track and holds no queue, so either row would
be a dead button. The equaliser is phase 2 and its button is not drawn.

### Icons

Three were wrong and each was wrong in the same way — a shape that reads as
something else. Subscriptions was a **stack of cards**, which is a playlist;
Settings was a **sun**, which is brightness; the mark was a bare red rectangle,
which reads as an image that failed to load. They are now two people, a cog, and
a rectangle with a play triangle in it. The search field gained the magnifier
segment that makes it read as a search box.

### The chip row is pinned

It was the first item in the feed's list, which is how a phone usually does a
header. The chips are the feed's *filter*, and a filter that scrolls away means
changing your mind costs a journey back to the top.

### The watch screen's order

Title → channel with subscriber count → actions → description box → comments →
up next, which is the web's order and was not this app's.

- **Like and dislike are one pill with a divider**, and the like carries its
  count. Two separate pills read as two unrelated opinions rather than one
  control with two directions — which is what they are, since pressing either
  clears the other.
- **Share exists**, through a platform share sheet, and shares the **YouTube**
  link: a LAN address only works inside this house.
- **The channel row has a subscriber line.** Without it the row is a name and a
  button, and the button is the only thing with any weight — so the eye goes to
  Subscribe rather than to whose channel this is.
- **The description box** clamps to two lines and opens on a press anywhere in
  it. The description is the one block whose length is out of the app's hands.

### Up next was a second feed

Full-width cards, in the shape the feed uses. The web's rail is a header naming
what plays next, two chips (**All / From <channel>**), and **horizontal** rows —
a 168dp thumbnail with the text beside it. The card shape is the difference that
matters: full-width cards fill the screen, so reaching the third suggestion is a
journey, and the rail competes with the video instead of sitting beside it.

The channel filter is **sent to the server**, not applied here: the endpoint
answers with a different *ranking* for a channel, not a subset of the unfiltered
one, so filtering locally would show the wrong videos in the wrong order and only
look right.

### Comments were missing entirely

Now read-only, with replies nested one level — YouTube nests no deeper. The
catalogue holds none for a video nobody has opened, so an empty section triggers
the import once and says *"checking"* meanwhile: "none yet" and "still asking"
are different answers, and saying the first while the second is true is the fault
this section is most likely to show.

**There is no "Add a comment" field**, and that is a decision rather than an
omission. The gateway accepts a post and writes it into *this household's own
catalogue* — it never reaches YouTube. Beside a keyboard on the web that is
defensible; under two thousand real YouTube comments on a phone it reads as a
reply to them, and it would be a reply nobody outside this house will ever see.

Commenters get a coloured initial rather than an image: the gateway sends an
empty `avatarPath` for every one of them, so `AsyncImage` would be a request per
comment for a 404 and a grid of grey circles.

## Subtitles, next, and resuming (2026-08-30)

Three things that were stand-ins. A stand-in named in a comment is still a debt.

### Subtitles

Side-loaded, not from the manifest: the captions live beside the video as `.vtt`
files and the HLS ladder the server writes carries no text tracks at all. Every
track is attached at **load**, because both platforms bind text to the *media
item* — adding one later means a new item and a restarted video — and choosing
one afterwards is a track selection, which costs no buffering.

- **Language and disabled-flag move together.** Setting only the language leaves
  the type disabled if it was off; disabling only the type leaves a language
  selected that nothing shows. Either alone makes the menu and the picture
  disagree.
- **No `SELECTION_FLAG_DEFAULT`.** A track marked default appears the moment the
  video opens, and subtitles nobody asked for is the wrong way round.
- **The row is absent when a video has no tracks**, rather than showing a
  Subtitles control whose every option is the state it is already in.
- **The chip says `VI (auto)`, not `VI-X-MT (auto)`.** The machine track is
  tagged `vi-x-mt` so nothing confuses it with a human Vietnamese track on disk
  — that is a *storage* distinction, and printing it raw put the subtag in a
  control two words wide.

Measured on the emulator: EN and VI (auto) offered, EN selected, captions drawn
over the picture.

### Next

The control bar's second button was forward-ten-seconds standing in for "next
video". It is now the first row of the rail below it, so the button and the list
always name the same thing, and it is **absent when the rail is empty** rather
than drawn dead.

### Resuming, and two faults under it

The position was computed as `durationSeconds × watchedFraction`. That looks
equivalent to the server's own `watchPositionSeconds` and is not: a video whose
duration the catalogue never learned — every video that arrived through a flat
listing — has `durationSeconds` 0, so the product is 0 and the video silently
restarts every time. It reads the server's number now.

Then two more, both found by measuring rather than reading:

- **A seek between `setMediaItem` and `prepare` is dropped.** Before prepare the
  controller's timeline is empty, so there is no window to seek within. A video
  left at 18 seconds of 74 opened at zero, every time, while the server held the
  right number all along. The start position goes *into* `setMediaItem`.
- **`startAtBeginning` had a default and the one call site did not pass it.** The
  compiler said nothing, and pressing next resumed the following video twelve
  minutes in — the precise behaviour the flag exists to prevent. The default is
  gone: it bought one short call site and paid for it with a fault nothing can
  catch.

Measured after: a video stored at 18s reopens and is at 26s ten seconds later;
pressing next on a video stored at 60s starts it at zero.

## Six more from the phone, and the player changes shape again (2026-08-30)

### The control bar is YouTube's, not the web app's

Asked for by name, with a screenshot. The previous version copied the *web* app's
single bottom bar — right for a page, cramped on a phone, where six controls
shared one row with the clock between them. It is now: chevron-down and gear at
the top, three transport controls on translucent discs in the middle, the clock
as a pill bottom-left, fullscreen opposite, and the progress line along the very
bottom edge.

- **The discs are load-bearing.** Over a moving picture a bare glyph disappears
  against whatever is behind it, and the middle of the frame is the one place
  that cannot be relied on to be dark.
- **A chevron, not a back arrow.** It collapses the video to the miniplayer
  rather than closing it; an arrow promises the opposite.
- **Previous and next are drawn faint when there is nowhere to go**, not removed
  — so the play button does not move under a thumb already reaching for it.
- **No cast button.** There is nothing to cast to: the library is reached over
  the house wifi by IP, and a Cast receiver is a second server this project does
  not have.
- **The trail lives in the app, not the ViewModel.** "Previous" is a fact about
  the *sitting*, and a ViewModel rebuilt for every video cannot remember what
  came before it. Not persisted either: offering to go back to something watched
  last week is not what the button says.

### The settings panel is a bottom sheet

It was anchored under the picture, which pushed the whole page down as it opened
— the title and channel row moved under a thumb already reaching for them. A
sheet rises over the page and leaves it where it was, and on a phone the bottom
is where a hand is. **No scrim**: the default dims the whole window, which would
grey out the video these settings are about.

Autoplay is real now, and fires from the ViewModel only when it is on *and* there
is somewhere to go — so the caller has no condition to re-check and the two
cannot disagree about when a video ends. "Finished" is `isPlaying` going false
with the playhead at the duration; there is no separate event on the port and
adding one would be a second way to say the same thing.

### Four things that were missing

- **The Continue watching rail had no overflow menu.** A card that looks like the
  others and has one fewer control is one somebody presses twice before deciding
  it is broken. The menu moved into `VideoCardMenu` so both cards draw the same
  one.
- **Save had nowhere to lead.** There was a Save action in every menu and no page
  showing the result — the same defect as a dead button, one step removed: the
  press did something real and nothing in the app could show it. `/api/pinned` is
  that page.
- **The channel avatar did nothing.** It is the one part of a card that *is* the
  channel; pressing it and getting the video is what people describe as "the
  avatar does nothing", because something did happen and it was not what they
  aimed at. On the watch screen the name is a target too — 40dp is small for the
  only thing on that row which is not a button.
- **Loading was a spinner.** A spinner in the middle of an empty screen says
  "wait"; the skeleton says *what* is coming, and the page then fills in rather
  than appearing. It pulses rather than sweeping a gradient: a sweep is a second
  animation to keep at 60fps on a television, and a fade between two greys says
  the same thing for one alpha.

### The feed mix

Three sliders, read from and written to the server — it is **one setting for the
household**, so a phone changing it changes everybody's Home. The fixed share is
read from the server rather than assumed: the web app carried its own copy once
and spent a release quoting a stale figure after a new fixed share took ten per
cent of the page.

**The other two shares are rescaled rather than each slider clamped.** Clamping
each to "100 minus the other two" makes the last few percent unreachable, and a
slider that stops before its end is one people push at. `rebalance` is pure and
tested, because the edges are where it goes wrong: moving one to 100 leaves
nothing to rescale, and three integers rounding to a total of 100 does not come
out even.

## The feed stopped at 48, and the chips jumped left (2026-08-30)

### `remember` with no key froze the end of the list

`atEnd` was computed inside `remember { derivedStateOf { … state.videos.lastIndex … } }`.
`derivedStateOf` re-reads *snapshot* state on every frame; `state.videos` is a
plain captured value, and a keyless `remember` captures the **first** one. So the
comparison was against the first page's size for ever.

What that looks like: `atEnd` goes true near the bottom of page one, loads page
two — and then can never go false again, because the visible index only grows.
`snapshotFlow` emits on *change*, so it never fires again. The feed stopped at 48
videos with nothing anywhere saying so.

Everything now comes from `layoutInfo`, including `totalItemsCount`, which is
snapshot state and grows with the list. There is no captured value left to go
stale. Measured: 28 swipes and still in fresh content.

### A topic switch destroyed the chip row

Pressing a chip set the state to `Loading`, which unmounts the composable holding
the chip row — so the row was rebuilt with its scroll at zero. Scroll right,
press "Science & Technology", and the row jumped back to "All" with the chip just
pressed off the screen.

`Ready` gained `switching`. The state stays `Ready` through a topic change: the
chips keep their scroll, and the skeleton is drawn **only where the videos will
go**. Measured: the row does not move and the selected chip stays in place.

`loadMore` refuses while switching — the first page of the new topic is already
in flight, and a second request would append it to itself.

## Icon's tint repaints every path (2026-08-30)

The CC button's "on" state was a filled white box with the letters knocked out in
black. `Icon` applies a **tint**, which replaces the colour of every path in the
vector — so the black letters turned white with the box and the button became a
solid white square.

The state is an underline now, and the glyph never changes. The rule is general
and worth keeping: **anything two-toned inside an `Icon` has this fault waiting
in it.** A two-colour glyph has to go through `Image`, or be expressed as
something beside the icon rather than inside it.

## Three preferences that were forgotten every video

Subtitles, narration and autoplay lived in the watch ViewModel, which is rebuilt
for every video — so all three reset on the next one.

They are per **device**, in a port of their own. The reasoning is the charter's
own for the equaliser: a phone in a pocket and a television in a room want
different answers, and none of this is a statement about taste the ranker should
hear. `ServerRepository` answers "where is the library and who is asking" —
questions settled before any request can be made — and folding these in would
leave that name true of half its members.

- **A remembered subtitle language is a language, not "the first track".**
  Remembering EN and being handed VI on the next video because that is what it
  carries is not what was asked for. A video without the remembered language
  opens with subtitles off, which is the honest answer to "show me English" when
  there is none — and the CC button is not drawn at all, because there is nothing
  to toggle.
- **Remembering narration on is the one preference with a cost**, and it is worth
  saying: opening any video then starts a server pass — translation and speech
  for every line. That is what somebody who turned it on asked for.
- **The switch and the pass are separate calls.** `toggleNarration` records what
  somebody wants; `startNarration` does the work. Folded together, the remembered
  case would have had to flip a boolean it already knew the value of just to
  reach the code that starts the pass.
- **A broadcast is never narrated on open.** The pass reads a caption file, and
  one that is still being spoken has none.

## "Watched" belongs on the Continue watching rail

It is the only list in the app whose whole membership rule is *not finished*, so
it is the one place a way out of it belongs. Recorded as fully watched rather
than only hidden: the ranker already drops anything past 95% from Home, so
telling it the truth is what makes the press outlive the app it was made in — the
local removal only covers the seconds until the feed is next fetched.

## A white status bar, and back finally means something (2026-08-30)

### The strip above the app is white

Measured before changing anything: the status bar, the top bar and the page were
all `#0F0F0F` — identical, no seam, nothing broken. So this was a preference, not
a fault, and it was asked for by name.

**It is two settings, not one.** The app paints the strip (`Tokens.statusBar`),
and `MainActivity` tells the system to draw its clock, battery and signal in
*dark* ink — `SystemBarStyle.light` means a light background. Painting the strip
alone leaves white glyphs on white. They must move together, and the token's
comment says so where somebody would change it.

### The system back gesture was never handled

Pressing back anywhere left the app. It is now **one handler in `App`**, and one
is the point: back is a question about the whole navigation state, and every
screen knows only its own part — the watch layer sits over a tab, the tab sits
inside a route. A handler on any of them would guess about the others.

Ordered outwards from the most recently opened thing: an expanded video collapses
to the miniplayer, then a screen returns to Home, then a tab returns to the first
one. After that the handler is **disabled** rather than doing nothing, so the
system closes the app as every other app does.

- **Every condition that enables it must have a branch.** It did not, for one
  build: a *collapsed* video counted as backable with nothing to do, so back was
  swallowed and the app could not be left. Measured — three presses, still on
  screen.
- **A collapsed video is deliberately not backable.** The miniplayer survives
  changing tabs and is closed by its own X; back closing it too would make the
  one control that means "stop" ambiguous.
- `ui-backhandler` is a separate artifact. It was already in the Gradle cache —
  something resolves it transitively — and is not on the classpath until asked
  for by name. Worth using rather than Android's: what "back" means is the same
  on both platforms, and `expect/actual` there would be a seam with nothing
  different on either side of it.

### Saved had no way out

Reached from Settings, with no tab bar of its own. Same back arrow as the channel
page, drawn over every state — a shelf that would not load was otherwise a dead
end, and iOS has no system back at all.

## The card takes YouTube's shape, and the white strip moves to one place (2026-08-30)

### The feed card

The reference for Home and History changed from the web app to the **YouTube
app**, with screenshots. Measured off them (iPhone, 1170px wide) rather than
eyeballed:

| | web card (before) | YouTube app (now) |
|---|---|---|
| thumbnail | 12dp radius, side margin | **edge to edge, square** |
| meta | two grey lines | **one**: `channel · views · age` |
| pressed | ripple over the card | `#272727` on the **meta row only**, full width |

- **Edge to edge is what makes a feed read as a column of pictures** rather than
  a list of tiles. Measured: the thumbnail reaches x=0 and x=1169.
- **One meta line, because two is four lines of text per card** under a two-line
  title, and on a narrow screen that turns the column of pictures into a wall of
  writing.
- **The pressed fill is on the row, not the card.** Measured by diffing the two
  screenshots: `#0F0F0F` → `#272727` across the whole meta band, with the
  thumbnail unchanged. A ripple over a photograph is invisible anyway.
- The bottom padding moved *inside* the meta row, because the fill has to reach
  the bottom of it — space between cards would leave a gap the highlight stops
  short of.

### One place paints the status bar

Painting it per screen was wrong twice in one sitting.

- **Three screens painted it and three did not.** Channel, Saved and Setup only
  *reserved* the height. That was invisible until the system glyphs were told to
  draw dark, and then the clock vanished on exactly those three — measured,
  `#0F0F0F` under dark ink.
- **Then it was painted at the root, above the watch overlay** — and the watch
  layer draws over the tabs with its own background, so the clock went
  dark-on-dark again the moment a video was open. It is now the **last child of
  the root Box**.

Sized from `WindowInsets.statusBars`, which is zero while the bar is hidden, so
fullscreen gets no white band without a condition saying so.

### The status bar names a background, and the root Box was drawn twice (2026-08-30)

Two faults shipped in one build, both from one line-based edit that removed the
white status-bar strip.

- **`SystemBarStyle.light`/`dark` describe the strip behind the glyphs, not the
  glyphs.** `light` was correct while that strip was white; with it gone the bar
  is transparent over `Tokens.bg` (#0F0F0F), where the dark ink `light` asks for
  is invisible. `dark` is what gives white ones. The two settings were always one
  decision and had to move together — only one of them moved.
- **A duplicated root `Box` swallowed every touch.** The deletion left the whole
  route `when` standing twice, the first copy trailed by a stray `when` that
  mutated `route`, `tab` and `watching` *during composition*. Nothing looked
  wrong: the second Box drew the same screens over the first. But a full-screen
  Box takes the touches that land on it, so the tab bar and the miniplayer were
  both dead while the pixels were correct. **A UI fault with no visual symptom is
  the argument against line-number edits** — the compiler was happy, the tests
  were green, and the only way to find it was to tap.

### iOS builds, and has never run (2026-08-30)

`iosApp/iosApp.xcodeproj` is hand-written rather than generated: the three Swift
files it wraps were already correct, and the KMP wizard would have replaced them.

- Measured: `xcodebuild -sdk iphonesimulator26.5` → **BUILD SUCCEEDED**,
  `Mytube.app` at 74 MB with 952 Kotlin symbols in the binary.
- **The framework is static**, so there is no `Frameworks/` directory and the
  74 MB is the executable. An empty embed step is the expected shape here.
- **The Kotlin build phase sources `env.sh`.** The toolchain is on the external
  volume and Xcode inherits no `JAVA_HOME` from the Finder, so without it the UI
  build fails where the terminal build succeeds — a difference that reads as a
  broken project.
- **No iOS runtime is installed** and the connected iPhone reports `iOS 26.5 is
  not installed`. Compiled and linked is all that has been shown; nothing has
  been run, and the several-gigabyte platform download is still the open
  decision.

## The drag had no destination, and the glass had two colours (2026-08-31)

Two faults reported from the phone, with screenshots of the web app beside them.
Both were about the same few pixels at the bottom of the screen.

### Nothing arrived; the layer underneath merely came into focus

`MiniPlayer` was composed only once `session.minimised` was already true, so for
the whole length of the drag **there was no bar**. The one thing changing on
screen was `WatchLayer`'s own ground: a `hazeEffect` whose blur ran down from
28dp to zero, revealing the tab out of focus and sharpening as the finger
landed. Described from the outside, accurately: *"kéo xuống thì nó không show
background của mini player, thay vào đó cái layer ở dưới bớt mờ dần."*

The web app does the opposite and it is the whole difference: the feed is sharp
from the first pixel, and the video visibly shrinks **into a bar that is already
there**.

- **The uncover blur is gone.** It was written to keep the video the thing being
  looked at, and what it actually produced was a screen where the destination
  does not exist until the gesture ends. All the layer does now is thin its
  paint, which is the honest description of one screen being uncovered by
  another.
- **The bar is drawn by `App.kt`, not by the layer.** Only `App.kt` knows where
  it lands — it already computes `landingFromBottomPx` from the navigation
  inset, the tab bar's height and how far that bar has scrolled away. So the
  layer reports its progress outward (`onDragProgress`) and `App.kt` draws the
  bar underneath it, alpha ramping linearly with the drag.
- **One modifier, two call sites.** The bar drawn under the drag and the bar left
  behind by it share the `align`/`offset`/`padding` chain. They have to land on
  the same pixel, because `landingFromBottomPx` is built from those same three
  terms; a placement that differed between them would put the picture down where
  the bar is not.
- **`showSurface` is false during the drag, and has no default.** Both platforms
  bind a player to exactly one surface — Android's `PlayerView`, iOS's
  `AVPlayerLayer` — so two `VideoSurface`s on one player means the second steals
  it and the first goes black. The watch screen holds it while the drag runs, and
  the real picture is travelling into precisely that box, so the black box is
  never seen empty. No default on the flag for the reason `startAtBeginning`
  lost its own: a flag defaulted to the common case is a flag the one call site
  that needed the other value forgets, silently.
- **The title that flew down is deleted.** `WatchScreen` drew its own title and
  channel tracking the shrinking thumbnail's right edge. With the bar present
  from the first pixel that is the same text twice, one copy sliding over the
  other. Text already in its final place does not need a second copy flying to
  meet it.

### Two tints stacked on one another

The miniplayer asked for `TINT_PANEL` 0.86 and the bars for `TINT_CHROME` 0.74,
on the reasoning that a panel carrying text wants more of the app's colour over
it than an edge content merely passes under. Fine in isolation, wrong in place:
the miniplayer *rests on* the tab bar and shares an edge with it, so the two read
as two surfaces that happen to be adjacent.

One constant now, `TINT_GLASS`, and it is the **higher** value — unifying
downward would have made the miniplayer's two lines less legible over a passing
thumbnail, while 10sp tab labels have room to spare at 0.86. Measured after: the
miniplayer band and the tab bar band both read 18–19 with no step at the seam.

### iOS has now actually run

The charter above says it never had. That is out of date: an iOS 26.5 runtime is
installed, and this was measured on the **iPhone 16e simulator** — the drag,
the spring-back with the video still playing, the bar landing under the picture,
and the seam. The Release build is signed and installed on the phone itself.

- **Gestures on the simulator are drivable from the terminal**, which is what
  made the above measurable rather than described. `simctl` has no touch input;
  synthesised `CGEvent` mouse drags over the Simulator window do. The device
  screen sits inside the window at 1:1 points with a bezel offset — calibrate by
  finding one landmark (the red mark in the top bar) in both a `simctl io
  screenshot` and a `screencapture` of the window, rather than assuming the
  screen fills it.

## Glass on the player, and a sheet that is finally made of it (2026-08-31)

Reported from the phone with a screenshot of YouTube's own player beside it.

### The seek bar had no frame to be the edge of

In fullscreen the bar stayed on screen after the controls had faded — a red
stripe across the bottom with nothing to explain it. The cause was a decision
that is still right everywhere else: the bar is drawn *outside* the scrim, so a
video playing with the controls hidden still says how far through it is. That
works because the bar is the **bottom edge of the picture**. Fullscreen has no
picture edge, so the same line is a stripe floating over the film.

- **It follows the controls in fullscreen and only there.** Outside fullscreen
  the hairline stays, because the reason for it is intact.
- **And it moves in from the edges**: 16dp at the sides, and the real
  `navigationBars` inset plus 16dp at the bottom. Not 34dp — that is an iPhone
  with a home indicator, and a phone with buttons reports 0. At the very bottom
  the bar's 32dp target overlaps the home-indicator swipe, so a finger seeking
  would leave the app.

### One material for everything over the picture

The controls carried three different fills — 0.45 for the transport discs, 0.55
for the clock pill, nothing at all behind the corner buttons. Three shades of
black on one frame is the fault corrected between the miniplayer and the tab bar
a day earlier, one screen over. `Modifier.glassSurface(shape)` is now the only
answer: 0.45 black with a 0.12 white hairline, which is what makes it read as
glass rather than as a hole punched in the picture.

- **It is not a blur and cannot be.** The picture behind these controls is a
  `UIKitView` on iOS and a `SurfaceView` on Android; Compose draws neither into
  its own layer, so Haze has no video pixels to sample. Worth stating because
  the request was for "liquid glass" — and the reference screenshot turns out
  not to be frosted either. `BarBackdrop` can frost the bars because what passes
  under *those* is a Compose-drawn feed.
- **CC and the gear share one pill**; the three transport discs stay separate,
  as in the reference. Two glyphs with nothing around them read as two unrelated
  marks; in a pill they read as one cluster, which is what they are — and the
  pill is where the extra width comes from.
- **The buttons are 56dp wide and still 48 tall.** The gear was reported as hard
  to hit and the room to fix that is horizontal: the row has width to spare,
  while growing downward reaches into the frame and, at the bottom, into the
  seek bar's own target.
- **Fullscreen gains the title and channel**, top left. Only there: everywhere
  else they are already the first thing under the picture.

### The sheet is glass, after a file explaining why it could not be

`SheetBackdrop.kt` recorded the failure honestly and drew the wrong conclusion
from it. `ModalBottomSheet` renders into its own popup layer with its own
coordinate space, so Haze — which positions from `positionInRoot` — drew the
slice of the app from the *top of the screen* into the sheet's place. That is
not a tuning problem and no parameter fixes it: **the sheet is not in the scene
Haze recorded.** The conclusion drawn was "a sheet is not made of glass". The
right one was "then do not use a popup layer".

`GlassSheet` is an ordinary child of the caller's full-screen `Box`, at real
coordinates. Measured: the settings sheet frosts the watch page behind it, and
the profile sheet frosts the feed.

- **Everything the platform sheet gave away had to be rebuilt** — the rise,
  tap-outside, drag-down, back. A sheet missing any one of them is a trap. All
  four are measured except back, which iOS does not have.
- **Drawn last, and that is now load-bearing.** A popup layer is on top wherever
  it is written; a child of a Box is on top only if it is written last. Left
  where it was, `ProfileSheet` opened underneath every screen and the miniplayer.
- **Always composed, told whether it is showing.** An `if` around it removes the
  node the exit animation would play on, so the sheet would vanish rather than
  slide away. Same reason `MutableTransitionState` drives it: composed already
  visible, there is nothing to animate *from*.
- **The back handler is local, against the one-handler rule.** That rule is
  about *navigation* state, which each screen knows only part of. Whether a
  sheet is open is not navigation — `settingsOpen` is a `remember` inside
  `WatchScreen` — and `ModalBottomSheet` handled its own back for the same
  reason. The innermost enabled handler wins.
- **The scrim is a parameter, not a constant.** The player's settings sit over
  the video they adjust, so dimming it would grey out the thing being changed;
  the profile sheet covers a feed it has no relationship with and dims it like
  any modal. One material, two answers about what is behind.
- **The watch screen registers its own haze source.** `hazeSource` wraps only
  `AppShell`'s content, and the watch layer is a sibling drawn over it — a sheet
  reading the ambient `LocalHaze` would frost the feed hiding behind the video.

### The glass was dark, not glassy; and the sheet stretched when the phone turned

Both reported after the first build reached the phone.

- **A single flat fill reads as a hole, not a pane.** 0.45 black with a 0.12 rim
  is *dark*, which is not the same thing. It is three layers now: a lighter base
  (0.32) that still keeps a white glyph legible, a diagonal white sheen fading
  out across the shape, and a brighter rim (0.22) over both. The gradient is what
  does the work — a flat translucent fill has no direction, and direction is what
  says light is landing on a surface rather than a window being cut in the
  picture. Diagonal rather than vertical: a vertical ramp on a 48dp disc reads as
  a shadow under it.
- **The blur was asked for again, and costed rather than refused.** It is
  reachable — a `UIVisualEffectView` inside `VideoContainer` above the
  `AVPlayerLayer` on iOS, a TextureView plus `RenderEffect` on Android. Both work
  by pushing the position and shape of every one of these controls down into the
  platform layer, which is a crack straight through the seam §3 exists to keep,
  and the Android half is unverified. Not done, for an effect the reference
  screenshot does not itself have.
- **The sheet kept its height and lost its proportion.** Content sized for
  portrait is ~246dp: a third of a portrait phone and two thirds of a landscape
  one, where it covered the video it is about. Capped on **both** axes, because
  they are two faults: `widthIn(max = 480.dp)` — above any phone in portrait, so
  that orientation is untouched — stops an 844dp band of glass holding two
  switches; `heightIn(max = 45%)` stops it swallowing the screen. The content
  scrolls when the cap bites, below the drag handle rather than around it, or the
  scroll container would take the dismiss gesture.

### Driving the simulator: activate first

Synthesised `CGEvent` clicks are swallowed while the Simulator window is not
frontmost — the first click only focuses it. Every measurement above needed
`osascript -e 'tell application "Simulator" to activate'` before the click, and
half an hour went into taps that appeared to do nothing. Note also that a tap
made while the controls are hidden only reveals them, so reaching a button is
two taps, not one.

### Still not done

Preview frames while scrubbing, asked for in the same round. The server has no
storyboard — grep of `services/` finds no sprite, VTT storyboard or preview
route — so it is a change in both repositories: sprites at ingest, a route, a
DTO, and the drawing here. Deliberately deferred, not forgotten.

## Liquid Glass on the tab bar, without splitting the app (2026-08-31)

The tab bar is drawn by SwiftUI on iOS 26 so it can be made of the system's
Liquid Glass. Everything else is still the one Compose tree it was.

### The measurement that decided the architecture

JetBrains' guide for this (`multiplatform/ios-liquid-glass.html`) is explicit:
*"you need a native SwiftUI shell, because Liquid Glass effects are rendered by
the system through native `TabView`, `NavigationStack`, and toolbar APIs"*, and
its worked example gives **each tab its own `ComposeUIViewController`**.

That is right for an app whose tabs are independent and wrong for this one.
`App` holds the watch session, the sitting's trail, one scroll position per tab,
the feed mix and the household's profile in a single composition; the miniplayer
crosses tabs precisely because it is a *sibling* of the shell rather than a child
of any tab; and the watch screen is a layer over the tab with Home composed
underneath it. Four compositions means all of it leaves the tree.

So the load-bearing question was asked as an experiment rather than assumed:
**does `.glassEffect()` sample a Compose scene rendered underneath it?** A
throwaway capsule over the feed answered yes — the thumbnails behind it are
visibly frosted. The charter's earlier finding still stands and is a different
arrangement: a `UIVisualEffectView` *inside* Compose through `UIKitView` punches
a hole in the scene and ends up looking at the window's background. Over the top
of the whole hosting view there is no hole.

With that, nothing had to move. `ShellBar.swift` draws one bar over the Compose
view and `ShellBridge` carries the four facts the two sides must agree on.

### What crosses, and what deliberately does not

- **Labels come from Kotlin.** A Swift `Localizable.strings` beside the `Strings`
  interface would be a second place a translation can go missing, which is the
  one thing §7 builds that interface to make impossible.
- **The selected tab crosses both ways.** Compose changes it too — switching
  profile resets to Home, and so does back — and without the push the platform
  bar would keep a highlight over a screen that had moved on.
- **Pressing the current tab still scrolls to the top.** Written on the Kotlin
  side of the bridge as well, because the platform bar cannot reach `tabScroll`
  and leaving it out would make the two bars differ on one gesture.
- **Swift runs the slide, Kotlin decides it.** Kotlin already reduces the scroll
  to a boolean; pushing the animated fraction instead would put a layout value
  across the language boundary every frame. The cost is that 220ms is written
  twice, and both copies say so.
- **The bar's height stays Kotlin's.** Every list in the app ends above it,
  computed from `Size.topBar`; Swift asks for the number rather than keeping one.

### `present` is the whole difference between a bar in the scene and a bar over it

A platform bar is on top of everything Compose renders. So it is taken away — not
merely covered — wherever Compose draws something that owns the bottom of the
screen: the search, channel and saved screens, which never had a tab bar; an
expanded video; and an open `GlassSheet`. Getting this wrong is a modal sheet
with a tab bar floating on it.

### Two things measured after the first build

- **Glass takes its tone from what is behind it.** `.regular` over a pale
  thumbnail turned the pane nearly white and the four white labels vanished into
  it. It carries `.tint(Color.black.opacity(0.55))` now — the platform's version
  of `TINT_GLASS`, and the same lesson.
- **The home indicator's inset belongs inside the glass.** Compose reserves the
  bar's row *plus* the inset; leaving the inset outside the pane left a strip of
  unfrosted feed along the bottom edge.

### The debt, stated

**Two shells now live side by side**: iOS 26 gets the SwiftUI bar, everything
below it keeps the Compose one, chosen by a single `#available` in
`ContentView.swift` and carried by one defaulted parameter,
`App(nativeTabBar =)`. `MainActivity` does not pass it, so the Android shell is
provably the one it always was.

The alternative was raising the deployment target to an iOS released this year,
in a house with more than one phone. Worth revisiting when that stops being true.

### Haze is gone; the glass has a lens now (2026-08-31)

`dev.chrisbanes.haze` is replaced by **Backdrop** (`io.github.kyant0:backdrop`,
Apache-2.0), a Compose Multiplatform library that draws a copy of a recorded
layer with effects on it. Haze blurred; this refracts.

- **The version is not the newest, and the reason is this project's toolchain.**
  2.0.1 needs Kotlin 2.4.10 and Compose 1.12.0; 2.0.0 needs `compileSdk 37`,
  which is past what AGP 8.11.1 supports at all — and AGP cannot move, because
  AGP 9.x is incompatible with the KMP plugin outright. `2.0.0-alpha03` is the
  last version published before the library moved to a toolchain this project is
  locked out of: Kotlin 2.3.10, Compose 1.10.1, `minCompileSdk=36`. Both floors
  are *below* ours, which is the safe direction. It is an alpha and that is a
  fact about support, not about the code.
- **`lens()` is the point.** A blur says the content continues underneath; a lens
  bends what is behind the pane's edges the way thick glass does, and that is the
  difference between frosted and *liquid*. It needs `RuntimeShader`, so it is
  **Android 13+**; blur is Android 12+. Below that the library draws what it can,
  which is what the app looked like before.
- **The effect order is fixed and the docs say so**: colour filter, then blur,
  then lens. Reversed, the lens refracts an unblurred image and the blur then
  smears the refraction flat.
- **The backdrop must have the page's background drawn into it.**
  `rememberLayerBackdrop { drawRect(Tokens.bg); drawContent() }` — without the
  first line the recording has transparent pixels wherever a screen does not
  paint, and the glass shows them as holes. It is the first thing the library's
  own guide warns about.
- **`shape` must be a `CornerBasedShape`**, because the lens refracts along the
  corners. A square bar is `RoundedCornerShape(0.dp)`.
- **Measured on the iOS 26.5 simulator**: it runs, and the thumbnail behind the
  miniplayer is visibly soft where it was sharp. It did **not** reproduce the
  `SkRecordCanvas::onDrawTextBlob` segfault the hand-rolled `GraphicsLayer`
  attempt hit, which was the risk worth checking first — this library manages the
  recording rather than re-recording a layer mid-frame.
- **Still not the video.** Like Haze, it samples a Compose layer, and the picture
  is an `AVPlayerLayer` in a `UIKitView` on iOS and a Media3 `PlayerView` on
  Android. The player's own controls keep `glassSurface`, which is paint.

**Not yet run on Android.** The lens is the half of this that only Android has,
and it is the half that has not been seen.

## Everything floats: capsules, and one glass in two kinds (2026-08-31)

The reference stopped being YouTube for this part and became **Apple Music on
iOS 26**, screenshot by screenshot. Its bars are not bars: they are capsules
inset from the edges with the page running underneath and down both sides.

### The three panes

Top bar, miniplayer and tab bar are now floating capsules sharing one margin and
one shape (`GLASS_MARGIN`, `GLASS_SHAPE` in `BarBackdrop.kt`). Three panes
stacked up one edge of the screen, and a different inset on any of them reads as
a mistake rather than as a margin.

- **A true capsule, `percent = 50`.** A fixed radius was tried first on the worry
  that a 28dp curve would eat the outer items; it does not, because a capsule's
  left edge is at x=0 across the whole middle of its height and everything in
  these rows is vertically centred. What it *does* eat is the corner of anything
  reaching the pane's top or bottom edge — which is why the miniplayer's picture
  is inset further from the left than from the top.
- **The status bar left the top bar.** The clock and the battery sit on the page
  now, which is what every iOS 26 app does and what a floating capsule forces.
- **The chip row left it too**, and that reverses "one bar, one material, one
  movement" — right while the bar was a full-width band, impossible now: a
  horizontally scrolling row inside a 28dp radius is clipped at both ends every
  time it moves. The chips lose nothing, each being its own pill already, and the
  movement is still shared because the offset is on the Box around both.
- **`consumeTaps` moved onto the panes.** It used to swallow every touch across
  the full width; beside a capsule there is now page, and a tap there belongs to
  the page.

### The miniplayer's round window

The picture in the bar is a circle, which needs `VideoSurface(fill = true)` —
`RESIZE_MODE_ZOOM` on Android, `AVLayerVideoGravityResizeAspectFill` on iOS.
Fitted instead of filled, a 16:9 frame inside a circle is a stripe with two black
caps, which reads as a broken image.

- **The drag lands on it.** The travelling picture now lerps its aspect ratio
  from 16:9 to square and crops from the first pixel of the gesture, and its
  fraction is *measured* against the layer's real width rather than the
  hard-coded 0.3 that was "derived against a phone's width". Without that the
  video arrives as a wide frame and is replaced by a round one in a single
  frame — the handover the whole gesture exists to hide.
- **The progress line sits on the capsule's bottom edge, inset by the corner
  radius.** That number is arithmetic, not taste: a `percent = 50` capsule's
  bottom edge is straight only between its corners, exactly `height / 2` in from
  each side. Less and the line runs into the curve and is clipped; more and it is
  short of the shape for no reason.

### One material, two kinds — and the rule for choosing

- **`BarBackdrop` samples**, and belongs to surfaces that float *over* the page
  and are outside the recording the shell makes: the two bars, the miniplayer,
  the sheet.
- **`glassControl` paints**, and belongs to everything *on* the page: the action
  pills, the chips, the description box, the up-next header, the sort options,
  the sheet's own rows. A sampled material is impossible for these — they are
  inside the layer the shell records, so a button would be sampling a recording
  of itself.
- **`glassSurface` paints too, but from black**, for the controls over the video.
  Same idea, inverted, because the backdrop there is bright and moving.
- **Selected is a change of *kind*, not of shade.** `glassControl(selected =
  true)` swaps to the solid inverted surface. That is the lesson the Like button
  cost: `surface` and `surfaceHover` are six units apart, which is invisible as a
  state.

### The player's scrim is gone

Black at 0.4 over the whole frame, and the note beside it already knew it was
wrong: *"darkening the whole picture to fix that is punishing the video for the
controls."* It was the only answer available before the controls were made of
glass. Measured after removing it: the picture is untouched and every glyph still
reads, because each one now sits on a pane that does the work the scrim was
doing.

Two things had been legible only because of it and gained panes in the same
change — **the chevron**, and **the fullscreen title and channel**. They were the
last bare glyphs on the screen, and finding them is the check to repeat before
adding anything to this screen: if it is not on a pane, the scrim is not coming
back for it.

`GLASS_BASE` went back up to 0.40 (from 0.45 → 0.32 when the sheen and rim were
added, then → 0.40 here). The pane is now the only thing between a white glyph
and a sunlit shot.

## The glass over the picture is finally glass, on iOS 26 (2026-08-31)

Reported from the phone: the player's controls have Liquid Glass turned on and
**do not blur**. That was true, it was documented, and the documentation drew the
wrong conclusion from a correct measurement — the same shape of mistake
`SheetBackdrop.kt` made about bottom sheets a day earlier.

The correct part: `BarBackdrop` samples a layer Compose records, and the picture
is an `AVPlayerLayer` in a `UIKitView`, which Compose never draws into that
layer. No parameter changes that, and `glassSurface` is paint because of it.

The part nobody had tried: **the same `.glassEffect()` that draws the tab bar,
laid over the whole hosting view, samples the video.**

- **Measured before a line of it was written**, with a throwaway capsule over a
  playing video and **two screenshots 1.2 seconds apart**. The two-shot part is
  the measurement: the content inside the capsule was blurred *and it changed*.
  One screenshot cannot tell a live sample from a stale snapshot, and a snapshot
  would have been worse than no glass — a frozen frame of a video that is still
  playing, sitting under the pause button.
- The failure the charter already records is the opposite arrangement and still
  fails: an effect view *inside* Compose through `UIKitView` punches a hole in
  the scene and looks at the window's background.

### Compose owns the layout; the platform owns the paint

`GlassPane` and `GlassItem` are the seam, and the rule is one sentence: **Swift
is told where to draw, never how big a thing is.** The composable is still
composed — it measures and places exactly as it does on Android — and
`drawWithContent {}` suppresses only its painting. What crosses is the rectangle
it landed on.

That is what makes this different from the two routes costed and refused
before (a `UIVisualEffectView` inside `VideoContainer`, a TextureView plus
`RenderEffect`): those work by pushing *the position and shape of every control*
down into the platform layer. This pushes a rectangle Compose computed.

- **The content has to cross too, and that is the real cost.** The platform layer
  is above everything Compose renders, so a glyph left in Compose would be
  blurred by the pane it belongs to. So `GlassItem` names an SF Symbol, and these
  seven controls look slightly different on iOS 26 than on Android — the debt
  "iOS first" already accepted, now visible.
- **A readout is not a Button.** The clock and the fullscreen title cross as text
  with `interactive = false`; wrapping them in a `Button` would draw a control
  that looks pressable and does nothing.
- **Disabled is faint *and* dead.** `onPress = null` for a transport disc with
  nowhere to go, so the platform cannot report a press Kotlin would act on.
- **The live badge's red travels as `0xAARRGGBB`.** A Swift copy of `Tokens.brand`
  is a second place it can be wrong.
- **Every piece of state is a key on the publish, not just the position.** A
  rectangle that has not moved fires no `onGloballyPositioned`, so a clock keyed
  only on its bounds would show the time it had when the controls appeared.
- **A pane leaves the platform layer when its composable leaves the tree**, and
  it fades on the way out. Without the fade the glass stays solid through
  Compose's own fade and then pops, which reads as the picture blinking.
- **The underline is drawn over the glyph, never stacked above it.** A `VStack`
  was the first version and was reported at once: the mark takes a row of its
  own, so the glyph sits off the centre of its own button whether or not the mark
  is showing. This is Compose's `Box(contentAlignment = BottomCenter)`, and the
  two have to agree because Kotlin sized the rectangle.

### What it is not for

A pane inside a scrolling list *works* — it is an ordinary layout node — and it
is the wrong tool twice over: it pushes a rectangle across the language boundary
on every frame of the scroll, and the platform layer is not clipped by the list
it appears to be inside, so a card scrolled under the top bar is still drawn over
it. **Everything on the page keeps `glassControl`**, which is paint and costs
nothing. The division in "One material, two kinds" now has a third entry:
sampled (`BarBackdrop`), painted (`glassControl`, `glassSurface`), and
platform-drawn (`GlassPane`) — the last only for chrome that stays still over a
picture Compose cannot see.

### Measured on the iPhone 16e simulator

Seven panes: chevron, the CC/gear cluster, three transport discs, the clock, the
zoom button. The picture behind each is visibly soft — the pause disc smears the
face under it, the clock pill smears the shirt — where the painted version was
flat black.

And the two things a platform layer breaks if it is wrong, both checked because
a UI fault with no visual symptom is the one this arrangement invites:

| | |
|---|---|
| tap between the discs | controls hide, and the panes leave with them |
| drag down | the video collapses to the miniplayer, still playing |

`./gradlew jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` is green, so the Android shell is provably the one it always was.

**Not on Android, and not below iOS 26.** `LocalNativeGlass` is false there and
every pane paints `glassSurface`, which is what this looked like yesterday.

## The tab bar came back to Compose, and the glass stopped being frosted (2026-08-31)

Reported from the phone, in one sentence each: the miniplayer is a different
material from the tab bar, and the chips are transparent with no blur at all.
Both were true, and the fix for the first was to undo the day's other decision.

### A platform bar is one pane of a set

`ShellBar.swift` drew the tab bar with iOS 26's own material, and the entry
recording that is still right about what it measured. What it missed is that the
bar has **three siblings** — the top bar, the chip row and the miniplayer —
stacked up the same two edges of the same screen. The other three cannot be
platform-drawn: the miniplayer holds a live `AVPlayerLayer` and the chip row is
a horizontally scrolling list, and both would have to leave the Compose tree.

Four panes in two materials reads as a mistake, which is how it was reported. So
the tab bar is Compose's again, `ShellBar.swift` and `ShellBridge.kt` are
deleted, and `App(nativeTabBar =)` became `App(nativeGlass =)` — the flag now
means the one thing that is still platform-drawn, the panes over the video, which
stay that way because a video is the one backdrop Compose cannot sample.

**The tab bar loses the system's material and the app gains one that is the same
everywhere, Android included.** That is the trade, and it is the right way round:
the panes are read against each other, not against another app.

### Liquid glass is mostly not blur

The material was `blur(16dp)` against `lens(12, 24)` — inherited from Haze, where
blur was the only tool there was — and beside the platform's own bar it read as
*frosted*. The first two attempts at fixing that raised the blur, to 12 and then
to 20, and both were wrong.

**Measured, in one screenshot carrying both bars at once**: under iOS 26's tab
bar a map showed through with the road numbers and "Peterborough" perfectly
legible. The system's material barely blurs. It is a tint, a refraction at the
rim and a specular edge — what makes it read as glass is that you can still see
through it. The library's own bottom-bar tutorial says the same in numbers: 4dp
of blur against a 16/32 lens.

So: `blur(8dp)`, `lens(16, 32, depthEffect = true, chromaticAberration = true)`.
The last two are what the earlier version never turned on — the depth bends the
middle of the pane as well as its edges, and the aberration splits colour at the
rim the way thick glass does.

- **`refractionHeight` is capped at the shape's smallest corner radius.** The
  library's own constraint, and it is why a 32dp chip cannot carry the bars'
  16dp refraction: there is nothing to bend along a corner tighter than it.
- **The blur is 8 rather than the tutorial's 4 because of what passes under each
  pane.** The tab bar crosses thumbnails, where 4 already reads as glass; the
  miniplayer crosses the feed's own captions, and 13sp text under 4dp is still
  sharp enough that the pane looks like a clear window with writing behind it.
  Reported exactly that way, twice.
- **`GLASS_BLUR` is one constant, like `TINT_GLASS`.** A chip that blurred less
  than the bar above it is the same seam in a different parameter.

### The chips could always have sampled

They were `glassControl` — paint — on the reasoning that anything *on the page*
is inside the layer the shell records, so a control would be sampling a recording
of itself. That reasoning is right about the page and wrong about the chips:
`AppShell` records `content()` alone, the screens, and the chip row is a sibling
of the top bar drawn over that recording and outside it.

`Modifier.liquidGlass` is that material for a control rather than a bar. The
watch page's action pills, the description box and the sort options keep
`glassControl`, because those genuinely are inside the recording.

**Selected is still a change of kind**, not a brighter pane: a selected chip is
the app's inverted surface, solid and light. The Like button's lesson, unchanged.

## A downloaded video said YouTube had refused it (2026-08-31)

Reported from the phone: search "phuong", open *PHƯƠNG MỸ CHI x DTAP | 'THIÊN
ĐƯỜNG VỚI NGƯỜI THƯƠNG'*, and the watch screen says **"YouTube will not hand this
video over"**.

Measured against the running gateway, that video answers:

```json
{"local":{"url":"/media/pYTHEpMod8E/1080p.mp4","mimeType":"video/mp4","seekable":true}}
```

`mediaState` is `READY` — the household has **downloaded** it — so the server
offers the file and no HLS ladder at all. `StreamDto` declared `local` and
`toDomain` never read it, so it fell through to `NothingPlayable`.

**This is the third time a tier has been declared and not read**, and the second
time it has shipped: `live` was the first, corrected on 2026-08-29, and the note
there says the fault is *"a lie in the worst direction"*. This one is worse than
that one. The earlier lie named an upstream refusal for a video upstream was
serving; this named an upstream refusal for **a file on the household's own
disk**, in the house, over the LAN.

- **Local plays now, and it is the last tier tried.** The order is live → hls →
  local. Not because local is worse but because the tiers are not equivalent:
  HLS is adaptive and carries the phone's 720 ceiling on the URL, while this is
  one whole file at whatever height was downloaded, usually 1080. On house wifi
  that is fine, and it is still the wrong default when a ladder was offered.
- **No `?max=` on it.** There is nothing to cap: it is a file, not a playlist.
- **§2 of the charter said local was phase 3.** That scope decision is what
  produced the message, so it is revised here rather than worked around — a video
  in the library that cannot be played, with a sentence blaming YouTube for it,
  is not a smaller feature. Downloading *to the phone* is still not done and is
  still the thing §2 refuses.
- **`StreamMappingTest` now covers all five answers**, with bodies copied from the
  running server rather than invented. Nothing in the type system catches a
  `when` branch that was never written; three times is enough to write the test
  that does.

### Two more from the same screen

- **The avatar on a search result did nothing.** `VideoCard` has taken
  `onOpenChannel` since the feed learned this lesson; the search screen was
  written afterwards and never passed it, so every avatar in the list opened the
  video. The charter's own words for it: *"something did happen and it was not
  what they aimed at"*.
- **The keyboard stayed up over the results.** It comes up on arrival, which is
  right — somebody who pressed a search box meant to type — and it has to leave
  at the first sign that typing is over. Scrolling is that sign, and half the
  screen is keyboard. It is dismissed on scroll and again when a result is
  opened, because this screen stays composed under the watch layer and a keyboard
  left up is drawn over it.

## The bars flickered under a resting thumb (2026-09-01)

`rememberBarsVisible` was `listState.lastScrolledForward`, which is the list's
own answer to "which way did the last movement go" — and it answers that about
**the last pixel**. A finger resting on a moving list wobbles a pixel each way,
so the bars came back and left again under a thumb whose owner was holding
still. Reported as the scroll not being smooth.

**The fix is not a debounce or a throttle.** Both are about *time*, and nothing
here is too fast: a bar that appeared 200ms after the wobble is the same fault,
late. What is wrong is that a movement of any size counts as a change of mind.

So the bars now need a **distance**: 48dp of travel one way to flip them, and
48dp back to flip them again. It accumulates and **resets on a reversal** rather
than summing, so a slow drift never adds up to a flip while a decisive short
flick always does.

- **48dp is about a finger's width**, and a third of a card's thumbnail: past
  anything a resting thumb does, short of a real flick.
- **A change of item counts as one decisive movement, not as a number.** Within
  one item `firstVisibleItemScrollOffset` is a real distance; across items the
  next one has its own height and its own zero, so the delta there is a value
  this cannot know. It is scored as exactly one threshold in that direction.
- **The top still wins outright.** `!canScrollBackward` is checked before any of
  this, and it is still `derivedStateOf` for the original reason: it is read on
  every frame of every fling.

## The controls became a design system (2026-09-01)

Reported in one batch, and all of it the same thing: the glass arrived one
surface at a time — the bars, then the player, then the chips — and every
control the *platform's* widgets drew stayed where it was. A Material slider
with a gap in its track, an outlined text field with a notched border, a
dropdown with a 4dp corner, a pull-to-refresh disc in default grey with the only
drop shadow in the app. Beside a floating capsule of glass each of those reads
as a control borrowed from another app.

`GlassComponents.kt` is where the shapes and surfaces live now, so a screen asks
for a control rather than for a Material one it then has to talk out of its own
appearance. The next time the material changes, it changes in one file.

- **`GlassRadius`**: `control` (a true capsule — chips, fields, buttons, bars),
  `panel` (20dp — a menu), `sheet` (28dp top corners). The sheet's radius is
  larger than the panel's because it is the width of the screen: the same number
  that reads as generous on a 200dp menu reads as almost square on a 390dp sheet.
- **`GlassSlider`** replaces Material's `Slider` *and* the hand-rolled
  `LevelSlider` written to escape it. That one already had the right argument —
  one bar filled to the value, not two bars with a gap — and then drew the bar in
  a flat surface colour, which is the same argument left half finished. The track
  is glass; **the knob is solid**, because a translucent knob on a translucent
  track is two panes that disappear into each other.
- **`GlassTextField`** is `BasicTextField` on a pane, with the label above it
  rather than floating in a notch — there is no border for a Material label to
  notch into.
- **`GlassRefreshIndicator`** grows with the pull and carries a determinate ring
  while the finger is still deciding, spinning only once there is something to
  wait for. It also had to be told what else floats under the top bar: on Home
  the chip row is pinned there, and the old fixed offset drew the disc *behind*
  the chips — measured, peeking out from under "Gaming".
- **The overflow menu is paint, not a sampled backdrop**, and that is the third
  place this rule has come up. A popup renders in its own layer with its own
  coordinate space, so a backdrop read there draws the slice of the app from the
  top of the screen — the failure `SheetBackdrop.kt` recorded. A sheet was worth
  moving out of a popup to escape it; a menu of three rows is not.
- **`GlassOption` was written and then deleted.** Nothing in the app is shaped
  like it: the two lists that exist are a tick list (Settings' languages, which
  is what the platform's own Settings does) and a chip row. A design system with
  a component nobody calls is the dead button one level up.

### The tab bar's labels sat high

Reported as the top padding being smaller than the bottom, and it was — but not
in any padding. A `Text` with no line height carries the font's own leading, and
for 10sp that is about 14sp with more slack under the glyphs than over them. The
column is centred in the bar, so the *box* was centred while the ink sat high in
it. `lineHeight = 10.sp` is the fix.

### The drag rounds, crops and lands on the window

The travelling picture already shrank, moved and cropped. Two things were still
wrong at the end of it:

- **The corners were square until the last frame**, which then swapped a
  rectangle for a round window. They now round with the drag, in `percent` rather
  than dp: the box is shrinking, and a fixed radius would be a different
  proportion of it every frame. The box is square by the end, so 50% *is* the
  circle exactly when it arrives.
- **It landed a thumb's padding above the window.** `travel` ends at the
  capsule's top edge, and the round window is inset `MINI_THUMB_PAD` below that —
  so the last 8dp were crossed in one frame. `MINI_THUMB_PAD` is public now,
  because the drag is the second thing that needs it.

## Autoplay stopped at the end of a video with the screen off (2026-09-01)

Reported precisely, which is what made it findable: *"nó chỉ xảy ra khi app in
background thôi, foreground thì okie"*.

`WatchViewModel` was one instance per video, built by `remember(session.videoId)`
— and `remember` is composition. **Composition on iOS stops when the app leaves
the foreground**, so advancing meant constructing an object that could not be
constructed: the sound ran to the end and stopped, and the next video appeared
only when somebody unlocked the phone and looked at the app.

Background audio is the one thing §1 of the charter says this app exists for, so
a sitting has to be able to move on without a frame being drawn.

- **The ViewModel now owns the sitting, not one video.** `videoId`,
  `startAtBeginning` and `autoPlay` are `var`s, and `advanceTo(next,
  fromTheStart)` loads the next video into the same player. It runs in
  `viewModelScope`, which is a coroutine and not a frame.
- **`WatchSession` gained a `sittingId`**, and that is what the `remember` is
  keyed on. `copy` carries it, a fresh `WatchSession(...)` does not — so "open
  this video" starts a new sitting and "the last one finished" does not, and the
  player is never torn down between two tracks meant to run on.
- **The route is told after the fact.** `onFinished` still fires and still owns
  the trail and the session; it may simply be running while nothing is composed.
  The session is then the source of truth going the other way — a
  `LaunchedEffect(session.videoId)` calls `advanceTo`, which checks the id first,
  so the background case costs nothing when composition catches up.
- **Pressing next, picking from the rail and pressing previous all `copy` now.**
  Same sitting, same player. Previous passes `fromTheStart = false`, because
  going back means returning to where you were.

### And the panes over the video were swallowing taps

Found while testing the above: `PlayerGlass` gave each pane a `Color.clear` to
size its stack, and **a `Color` in SwiftUI is a shape that fills its space and
takes touches**. Every tap landing on a pane was eaten by it — the button under
the finger never fired, and neither did the Compose control still composed
underneath. The `.frame` that follows already sizes the stack, so the fill was
never needed.

## A sampled backdrop inside the recorded layer is a crash, not a bad look

The rule was written as an appearance problem: a control inside the layer the
shell records would be "sampling a recording of itself". Giving the
pull-to-refresh pane `liquidGlass` while it still lived inside the list measured
what it actually costs — **on launch**:

```
EXC_BAD_ACCESS  Could not determine thread index for stack guard region
SkRasterPipeline::run … SkBlurImageFilter::onGetOutputLayerBounds …
SkCanvas::internalSaveLayer …
```

A stack overflow inside Skia's image-filter bounds walk. The filter contains
itself and the walk never ends.

So the indicator moved out rather than being tuned: the screens publish how far
the pull has got (`PullGlass`) and **`AppShell` draws the pane**, as a sibling of
the recording, exactly where the bars are drawn and for exactly the same reason.
Measured after: the pane is real glass, the feed shows through it, and it grows
with the pull.

**The overflow menu on a card is still paint, and cannot be otherwise today.** It
lives in a popup — its own layer with its own coordinate space — which is the
failure `SheetBackdrop.kt` recorded, *and* its anchor is inside the recorded
layer, which is the crash above. Real glass there needs the platform route
(`GlassPane`, as the player's controls use), with an anchor rect published from
an invisible node and the rows drawn by SwiftUI. That is a piece of work with a
dismiss gesture in it, and it is deliberately not started here.

So it is `menuSurface`: **the theme's own surface at 0.92 with the panes'
hairline**, and not `glassControl`. That one is built for a control *on* a page —
white at 0.09 — and over a bright thumbnail it turned the menu into a grey smear
with sharp video showing through it. A menu is a sheet of the app's colour that
happens to be slightly see-through, which is what it now says it is rather than
imitating a material it cannot have.

### And the pane it replaced would not go away

The first version of `PullGlass` read `state.distanceFraction` **inside**
`SideEffect`. A state read there subscribes nothing — the effect runs *after* a
recomposition, it does not cause one — so the pane was only ever updated when
something else happened to recompose that slot. While a finger drags a list that
is constantly; the instant it lifts it stops, and the pane kept the last fraction
it had been handed and stayed on screen. Reported as the loading not hiding. The
read moved into composition, where it subscribes.

## Settings became a menu, and four smaller things (2026-09-01)

### A settings screen is a list of answers, not a page of controls

It was one long page: an address, a shelf, two sliders, a text field and a list
of languages, all scrolling past each other under one title. What belongs on a
phone is a **menu** — a row per subject, the subject on its own screen — which
is what the platform's own Settings does and what makes each row's *value*
legible without reading the control that sets it.

Four rows now: the server's address, the saved shelf, the Vietnamese voice
(showing the voice's name), and the language (named in its own words). Behind
them, `VoiceScreen` and `LanguageScreen`.

- **`DetailScaffold` is the third time this shape was written**, after the
  channel page and the saved shelf, so it is a function now: a title, a back
  chevron drawn over the content, and the argument both of those recorded — these
  screens have no tab bar, Android's system back leaves the app and iOS has no
  system back at all, so the way out has to be on the screen.
- **The setup screen gets the same arrow, and only when there is somewhere to go
  back to.** It is two things — the first thing on a fresh install, where an
  arrow would lead nowhere, and a row in Settings. `onBack` is null in the first
  case rather than a button that does nothing.
- **And it stopped being Material.** It drew an `OutlinedButton`, a `Button` and
  an `OutlinedTextField` — a container colour, a border and an elevation from a
  design language that is not this one, on the first screen anybody sees.
  `GlassButton` has two weights and no third: `primary` is the app's inverted
  surface, everything else is a pane of glass. A screen with three kinds of
  button is a screen where none of them means anything.
- **The miniplayer follows onto these pages**, as it does onto the saved shelf
  and a channel — the sound carrying on across screens is the point of holding
  the session above the routes.

### Save's "Saved" state was a blank white pill

`glassControl(selected = true)` swaps to the inverted surface — solid, light —
and the pill's content stayed `Tokens.text`, which is white. So the saved state
was a white capsule with an invisible bookmark and an invisible word on it. The
content follows the surface now. Every other selected surface in the app already
did this; this one was the exception.

### Like and dislike lost their count

The count was there because the web app's is, and because an empty space where a
number belongs reads as a number that failed to load. On a phone that loses to
the row it is in: the row scrolls sideways and the widest thing in it was a
figure nobody presses. The count is a fact about *other people* and it is still
on the page, under the title beside the date, where facts about the video live.

### The channel's sort row lit nothing

Pressing Popular appeared to do nothing at all. It was doing the request — what
it could not do was **stay lit**: every answer carries a fresh set of
`sortOptions` with fresh tokens, so the token that was sent matches none of the
ones that come back, and `lit = option.token == selected` was false for all
three the moment the answer landed.

Lit by **position** now. The order is the server's own and is stable within a
channel; the token is not, and was never a name for anything.

- **The list is a skeleton while a different order is on its way**, and the
  header is not. The page used to dim to 0.45 whole, which says "something is
  happening" and not *what* — and it left the old order legible underneath, so
  the first thing to change when the answer arrived was a list somebody was
  already reading.
- **What is still wrong is not in this app.** Measured against the gateway: for
  one channel all three tokens return the same thirty videos in the same order,
  while for another (`UCsT0YIqwnpJCM-mx7-gSA4Q`) Popular genuinely differs. So the
  request path here is right and the sort itself is a question for the server.

### The miniplayer was not glass on three screens

`AppShell` registers the backdrop source around the four tabs, and for a while
that was the whole app. It is not: search, a channel and the saved shelf are
routes of their own with no shell around them — and **the miniplayer floats over
all three**. There it was sampling a recording nothing was writing to, so the one
pane that crosses every screen was the one that stopped being glass whenever
somebody opened a channel.

`Modifier.glassSource()` is that registration, applied by each of those screens
and by `DetailScaffold`. Only one route is on screen at a time, which is what
makes a shared recording safe.

## Seven from one round of use (2026-09-01)

- **The setup screen slid the wrong way.** It was ground with Home in `depth`,
  on the reasoning that it is where the app starts before there is a library. It
  is also a row in Settings, and there it was the one screen in that menu whose
  animation disagreed with its neighbours. Depth 1 now; a fresh install slides in
  from the right on first launch, which is what arriving somewhere looks like.
- **The saved shelf's menu offered "Saved".** The same action needs a different
  word on that screen: in a feed the menu offers to keep something, and on the
  shelf every row is already kept, so the item stated what was already true and
  gave no verb to press. `saveLabel` is the caller's, and `removeFromSaved` is on
  the dictionary in both languages.
- **The player's settings sheet was cut off in fullscreen.** The cap is 45% of
  the screen, which is ~380dp in portrait and ~175dp on a phone turned sideways —
  and fullscreen is the one place this app is ever landscape. Two fractions now,
  0.45 and 0.8. What the cap protects is what is behind the sheet, and behind it
  is a video whose subject is in the middle of the frame either way.
- **The profile picker did nothing.** `if (profiles.size > 1)` — and the list is
  fetched once at launch, unguarded, so a server that was asleep for those two
  seconds left the household with no members and an avatar that did nothing for
  the rest of the run. The fetch is guarded, and pressing the avatar **asks
  again** rather than shrugging.
- **The chosen member was a grey band.** `surfaceHover` across the full width, a
  flat rectangle inside a sheet made of glass and the only thing on it that was
  neither. It is the same translucent pane every selected control uses, inset so
  it reads as a row picked up rather than a stripe painted behind one.
- ~~**The channel's sort row: measured, and half of it is not this app.** … That
  is a question for the gateway.~~ **Wrong, and corrected on 2026-09-01** — the
  request path was this app's fault all along; see "An ordering is a
  continuation" below. The measurement was real and the conclusion drawn from it
  was not: three tokens answering identically was the *symptom*, and testing
  `sort=`, `order=` and `sortBy=` was testing three spellings of a parameter the
  gateway does not read at all. What was
  this app's, and is fixed, is the *lighting* — see the entry above — and the
  spacing: the header's bottom padding and the row's own top padding were both
  there, so the cluster sat 24dp below the counts and 8dp above the first card.
  The header contributes none now and the row owns both sides.
- **The channel banner is drawn, and it is not a band.** It was left out because
  "a banner is 200dp of decoration above the one thing the screen is for" — right
  about the way YouTube draws one, and not what this is: the picture fills the
  space the header already occupies, cropped from its centre, ending under the
  counts. It costs no height at all. **Blurred and tinted, in that order**: a
  banner is somebody else's composition with its own text and its own focal
  point, and reading a name over it needs the picture to stop being a picture —
  while tinting before the blur would only make a pale banner paler.
  `bannerPath` had to be carried from the DTO through the domain to get here; the
  gateway had been sending it all along.
  **Reversed on 2026-09-01** — see below. It cost no height, which answered the
  objection it was drawn to answer, and produced a different one.

## Search reaches YouTube again (2026-09-01)

Reported in one sentence: search does not offer YouTube the way the web does.
It did not, and the reason recorded for that was a mistake of fact — *"the
gateway has one search route and it answers from the catalogue only"*. There are
two: `GET /api/search` reads the catalogue, `GET /api/discover?q=&limit=` drives
yt-dlp against YouTube, and the gateway's own note says it is not a fallback:
*"it runs on every search, not only when the library comes up empty — topics
decide what the feed offers, and searching is how someone deliberately looks past
that."*

A library search that cannot reach past the library is half a search, and the
half it drops is the one somebody types a name into a search box to get.

- **Two calls, two states, two failure modes.** `SearchState` for the library,
  `UpstreamState` for YouTube. One state carrying both would mean a screen that
  cannot show the half that worked — and the half that works is usually the
  local one, answered off an index in milliseconds while the other is a yt-dlp
  run over the internet. Both directions are tested.
- **`ExternalVideo` is its own domain type.** Upstream sends a different set of
  facts and every difference is visible: no channel *id*, so the name is text
  rather than a target; no avatar, and a `Video` with an empty avatar path is a
  request per row for a 404; no published date, so the meta line is two facts
  rather than three; and a `thumbnailUrl` that is **absolute**, pointing at
  YouTube rather than at a path inside the library. Squeezing it into `Video`
  would have meant inventing a `Channel` nobody sent.
- **Opening writes the row first and navigates second.** `POST
  /api/videos/external` takes the *address* — not an id this app builds a URL
  from — and answers with the catalogue id. Only metadata is written; the
  download starts when the player asks how to play it. Navigating first would
  put "YouTube will not hand this video over" on screen for a video that is on
  its way, which is the lie this app has now told twice for other reasons. So
  the card carries a spinner over its picture and refuses a second tap.
  - **`inLibrary` skips the round trip**, and it is *not* what decides whether a
    result is drawn. Hiding an upstream result already listed above is a question
    about this screen, so it is answered with the ids on this screen — the web
    app does the same.
  - **An empty `videoId` is a refusal wearing a success's clothes.** The gateway
    answers 200 with `{"videoId":""}` when it could not resolve the address, and
    navigating to an empty id opens the watch screen on nothing.
- **Upstream has no cursor.** `ytsearchN:` takes a count, so "more" is the same
  search asked for at a larger size (+20), and the answers have run out when one
  comes back shorter than what was asked for. A new query resets the size —
  without that, refining a word inherits however large the last search had grown.
- **No overflow menu on an upstream card.** Saving one means writing the row and
  *then* pinning it: two requests for one press, on a video the household has not
  decided to keep. The web app draws the button; the rule this app already
  follows — a card whose actions mean nothing draws no dot rather than a dead one
  — is the one that wins here.
- **A pasted video link already works and needed nothing.** The gateway reads an
  address out of the query itself and answers with that one video, or with
  nothing when the library already holds it. Measured against the running server:
  a `watch?v=` URL comes back as exactly one result, and the library half — which
  finds nothing for the text of a URL — hides itself.
- **A pasted channel link leaves the screen.** `GET /api/channels/resolve?q=`
  answers with a channel id or `null`, and it is asked of *every* query because
  only the gateway can tell: it reads the address and checks the catalogue's
  handles, which covers 1,626 of this library's 1,690 channels with no upstream
  request at all. Measured: `youtube.com/@mkbhd` → `UCBJycsmduvYEL83R_U4JriQ`,
  and "nothing phone" → `null`.
  - **A value the screen acts on, not a callback fired from the ViewModel.** The
    resolve runs in a coroutine that outlives a frame; navigating from there
    would navigate whether or not this screen was still on top of the app. The
    screen clears it once it has gone, or coming back would send the viewer
    straight out again.
  - **Leaving, not stacking.** Back from a channel already returns to Home, so
    there is no search page left behind that would only redirect here again —
    which is what the web's `replace` buys.
  - **A failed resolve is silence.** Nothing is wrong with a search that could
    not be checked for being an address, and the two halves that matter have
    already answered.
- **Both halves are drawn, both are tested.** `DiscoverMappingTest` parses a body
  copied from the running gateway rather than invented — the rule
  `StreamMappingTest` was written under — and `SearchViewModelTest` covers the
  debounce collapsing thirteen keystrokes into one request of each kind, each
  half failing while the other answers, the page-size arithmetic, and every
  branch of opening.

## Back from a channel, and a third avatar that did nothing (2026-09-01)

Both reported from the watch-history page, and both are faults of the same kind:
something that was made to work once, on the screen it was reported on, and then
written again from scratch on the next screen.

- **The avatar in history opened the video.** `VideoCard` has taken
  `onOpenChannel` since the feed learned this, the search screen was corrected
  for it a day later, and history — which was a tab then and is a page now — was
  never passed it. The charter's own words, now on their third screen:
  *"something did happen and it was not what they aimed at."*
- **Back from a channel always went Home.** Every other page in this app is
  reached from exactly one place, so `route = Route.Home` is right for all of
  them; a channel is reached from a feed card, a search result, the saved shelf,
  watch history and the watch screen, and sending all five to Home throws away
  the list somebody was reading.

`channelFrom` records where the page was opened from, and `openChannel` is the
**one** way to reach it — five call sites set the route by hand and four of them
would have forgotten the other line. The system back gesture reads the same
value, because it is one handler for the whole navigation state and this is part
of that state.

**One level, deliberately.** It is a `var`, not a stack: the only way to a second
channel is through a video, and opening a video leaves the route standing rather
than pushing onto it. A stack would model a history nothing here creates.

### And back from a channel slid the wrong way

The route transition reads its direction from `depth`, as `target >= initial`.
That worked while every page was reached from Home: one level in slides from the
right, and back reverses it. A channel opened from watch history is a pair at
*equal* depth, and equal counts as forward — so leaving the channel animated as
though it were another step in.

`Route.Channel` is depth **2** now, the only route that is, because it is the
only page reached from another page rather than from a tab. The fix belongs in
`depth` rather than in the transition: the direction is a fact about the pair,
and the pair was being described wrongly.

### The banner is gone again (2026-09-01)

Drawn a day earlier as a blurred, tinted ground behind the channel header, on the
argument that it costs no height. That argument was sound and the result was not:
a channel's banner is somebody else's composition, and 24dp of blur under the
page's own colour makes a smear whose only job is to sit behind a name that reads
perfectly well on plain ground. Reported in three words and they were right.

**`bannerPath` stays on the DTO and on the domain type.** It is what the gateway
sends and carrying it is the mapper's job; the decision not to draw it belongs on
the screen that would. That is also what makes drawing it again cheap, if a third
argument ever turns up.

## An ordering is a continuation (2026-09-01)

Reported again, with the right instinct attached: *"tao nghĩ là api ở mobile
sai"*. It was.

The gateway reads exactly one thing — `r.URL.Query().Get("pageToken")` — and this
app sent the ordering as `sort=` beside it, so every ordering was dropped in
silence and answered with the default one. The web app has always sent the sort
token **as the first page token**, and says why: *"that is how YouTube models it
— an ordering is just another continuation."*

Measured against the running server on `UCsT0YIqwnpJCM-mx7-gSA4Q`, the channel
whose orderings genuinely differ:

| | |
|---|---|
| `?pageToken=<popular>` | `GNZBSZD16cY, 36m1o-tM05g, …` |
| `?sort=<popular>` | the Latest list, unchanged |
| page two of that continuation | `A6Dkt7zyImk, …` — still Popular |

- **The wire has one slot; the app has two intentions.** *"Show me this ordering
  from the top"* replaces the list, *"show me more"* appends to it, and the
  screen has to keep them apart. So `channelPage(channelId, sortToken,
  pageToken)` keeps both and `channelToken` folds them at the edge, where shapes
  are allowed to differ. A cursor wins when there is one, because it already
  carries the ordering it was handed out inside — the third row of the table is
  that claim, measured rather than assumed.
- **A named function, not an expression in a request builder.** The same reason
  `wholeSeconds` is one: nothing in the type system catches a query parameter
  nobody reads — both are strings and both requests succeed with a 200 — so
  `ChannelTokenTest` holds it to the three cases without a server.
- **The lesson is about the conclusion, not the parameter.** A day earlier this
  was measured, found to answer identically for all three orderings, and written
  down as the gateway's problem. Trying `sort=`, `order=` and `sortBy=` was three
  spellings of a guess; reading the eleven lines of `handleChannelVideos` would
  have ended it. **When a request appears to be ignored, read the handler before
  varying the request.**

## The bookmark asks which collection (2026-09-01)

The bookmark wrote one bit — `POST /api/videos/{id}/pinned`, *keep this file when
the disk fills* — and there was nowhere to say **which** collection, so the
household could not keep music apart from news. Pressing it now opens a sheet.

### The database was built for this and the API was not

Migration `0015_playlists.sql` created `playlists` and `playlist_items` in
2024, with a `position` column whose comment already said it is *"appended to at
the end when somebody adds a video here"* — and above the catalog repository's
interface sat a doc comment for a `SetPlaylistItem` **declared nowhere**. The
gateway exposed two GETs. Nothing in either client could create a playlist, add
to one, or remove from one, and the running server answered `{"playlists":[]}`.

So the work is in the server repository first: four RPCs, four routes, and one
new read. That order is not a preference — neither client had anything to call.

- **`GET /api/playlists?videoId=` is the whole reason the sheet is one request.**
  Every row then carries `containsVideo`. The alternatives were costed and
  refused: `GET /api/videos/{id}/playlists` is a second request for a screen
  that is useless without the first, and merging on the client is N requests for
  one bit each. Without the parameter the flag is absent and every existing
  caller is untouched.
- **A `when` for the tiers, an `EXISTS` for ownership — and the ownership guard
  was wrong.** The first version of the add was
  `INSERT … SELECT $1, $3, COALESCE(MAX(position), -1) + 1 FROM playlist_items
  WHERE playlist_id = $1 AND EXISTS (…owner…)`. Measured against the running
  gateway, **it inserted into another member's playlist**: an aggregate SELECT
  with no GROUP BY returns one row however the WHERE went, so MAX of nothing is
  NULL and the position comes out 0. The rows come `FROM playlists` now, which
  makes "not yours" no row at all. The position is still computed inside the one
  statement, because two clients adding at once would otherwise pick the same
  number.
  - Measured after: add twice → one row; two videos → positions 0 and 1; another
    member → 404 on add, remove and delete alike; deleting the playlist takes
    its items (`ON DELETE CASCADE`).
- **Idempotent on purpose, both ways.** Adding what is already there and
  removing what is not are both success: the sheet can be saved twice, and a
  duplicate key surfacing as a failure would be the app reporting a fault where
  the outcome is exactly what was asked for.

### The sheet

`SavePlaylistSheet` over `GlassSheet` — an ordinary child of the root `Box`,
which is why `App.kt` draws it **last**, and why the sheet is hoisted there at
all: six screens open the same one.

- **Save applies the difference, one request per change.** Not one call carrying
  the final state: a tap is one or two changes, and an endpoint that takes the
  whole state is one that empties a playlist the day a client is wrong about
  what was in it. Unticking really removes — a tick that does not is a control
  that lies.
- **The first row is not a playlist.** It is the saved shelf, the pinned set,
  which is not a row in `playlists` — and *that* is why it cannot be renamed or
  deleted, rather than a rule invented for the UI. Modelling it as a `Playlist`
  with a made-up id would put a row in the list that no call can reach and make
  every call site remember which id was magic.
- **The pinned bit is passed in, not fetched.** The card that opened the sheet
  already knows it (`Video.saved`), and the watch screen's pill hands it out on
  the way in. A second request for a fact in hand is a slower sheet for nothing.
- **A new playlist arrives ticked.** Somebody who has just named a list for this
  video means to put it there; asking again is asking the same question twice.
- **An upstream result ensures first, and the *returned* id is what the adds
  use** — `SearchViewModel.openExternal`'s pattern. An empty id back is a
  refusal wearing a success's clothes, and adding with it would write rows
  naming no video, so it adds nothing.
- **`LIST_MAX_HEIGHT` is measured, not chosen.** At 280dp the Save button came
  up below the fold on a household with six playlists — the sheet caps itself at
  45% of the screen, and the title, the button and the home indicator's inset
  are the rest of it. 196dp is what is left.
- **Selected is a change of kind**, and the content colour follows the surface.
  That is the Like button's lesson and the Save pill's: six units of grey is
  invisible as a state, and white text on the inverted pane is invisible
  outright.

### The two pages, and one row in Settings

The **"Đã lưu" row became "Playlist"** — one row, not two. The shelf did not
disappear; it is the first row *on* the playlists page, which is where it
belongs once there is more than one collection.

- `Route.Playlist` is **depth 2**, like `Route.Channel` and for that entry's
  reason: it is reached from a page that is itself one level in, and at equal
  depths `target >= initial` reads leaving as going deeper.
- **Opening a video from a playlist passes the page's ids as the queue.**
  `WatchSession.queue` already existed for the channel page, so next and
  autoplay stayed inside the list with **no player code changed at all**.
- **A failed removal puts the row back**, unlike the feed's "not interested".
  That list is a ranking and the row is gone from the page either way; this page
  *is* the playlist, so a video still in it that is not drawn is the screen
  lying about its own contents.
- **The overflow is absent on the shelf** rather than present and refusing.

### The card menu no longer says "Saved"

The row's label used to follow `video.saved` — Save, then Saved. It is
"Lưu vào playlist" whatever the answer now, because a video already on the shelf
can still be wanted in a collection, and the old label would say the question had
been answered. The three per-screen `toggleSaved` methods went with it: the sheet
is the one writer of that bit, and `WatchViewModel.markSaved` only redraws the
pill from what the sheet applied.

**Upstream cards gained a menu with exactly one item.** They had none, on the
rule that a card whose actions mean nothing draws no dot — and one action does
mean something here.

## The alert is glass too, and one of them crashed the app (2026-09-02)

Reported in one batch after the first playlists build reached the phone. The
interesting half is not the alert; it is where an alert may be drawn.

### A sampled backdrop inside the recorded layer is still a crash

The charter already records this from the pull-to-refresh pane — *"a stack
overflow inside Skia's image-filter bounds walk. The filter contains itself and
the walk never ends"* — and it was paid for a second time here. `GlassAlert` was
written as the last child of each screen's own
`Box(Modifier.fillMaxSize().glassSource())`, which is exactly the node that
registers the layer every floating pane samples. Measured: pressing **Rename**
dropped the app to the springboard, twice, with nothing in the console.

The fix is the shape `PullGlass` already found: the alert is a **sibling** of the
recorded box, not a child of it. Two boxes, and the nesting is load-bearing.

The save sheet never had the fault because it is drawn from `App.kt`, outside the
recording — which is why the same material worked there and crashed one screen
over.

### Everything a question needs, rebuilt

`GlassAlert` is not `AlertDialog` for the reason `GlassSheet` is not
`ModalBottomSheet`: a popup layer has its own coordinate space and a sampled
backdrop in one reads the slice of the app from the top of the screen. So the
scrim, tap-outside, back, and both halves of the animation are written here.

- **`imePadding` goes on the centring box, not on the pane.** What has to move is
  the *space the pane is centred in*. Padding the pane keeps it centred on the
  whole screen and merely pushes it up at the end, which on a short phone leaves
  the field under the keyboard — the one thing an alert with a field must never
  do. Measured with the software keyboard up: the pane sits in the middle of what
  is left.
- **The caret goes to the end of a prefilled name.** A `String`-valued
  `BasicTextField` focuses at position **zero**, so the first letter typed into a
  rename landed in front of the word being edited — measured, "test" became
  "xtest". Only the caller knows where the caret belongs, so `GlassTextField`
  gained a `TextFieldValue` overload and the alert passes the selection.
- **Return confirms.** Without it the key dismisses the keyboard and uncovers the
  button that was always going to be pressed next.
- **The keyboard is asked for rather than waited for**, keyed on `visible`: a
  `FocusRequester` fired once at composition leaves every later opening without a
  cursor, because the composable stays in the tree between them.

### The sheet's "+" creates and adds in one press

Pressing it closes the sheet and opens the alert — two panes of glass over each
other, one asking which lists and one asking for a name, is two questions at once
and the lower one is not answerable. Saving **creates the playlist and puts the
video in it**: leaving the add to a later press of the sheet's own Save would
make a named-but-empty playlist the outcome of cancelling, and that press is not
even on screen. The new row is then ticked *and* part of the diff's baseline, so
pressing Save afterwards sends nothing about it.

**It is prepended, not appended.** The sheet's list is capped and scrolls, so
appending put a playlist somebody had just named below the fold — the whole point
of showing the sheet again is that they can see it happened. It is also the
server's own order, which is `updated_at DESC`.

### Four faults of state that outlives a route

`viewModel()` stores in the activity's store, which outlives the screen. That is
what makes the miniplayer possible and it is also what these four were:

- **A deleted playlist stayed on the page**, opened nothing, and was gone only
  after a restart. `PlaylistsViewModel` is hoisted into `App.kt` now and told
  which row went — told rather than refetched, because this side already knows
  the id and a round trip would draw the stale list while it ran.
- **Both playlist screens ask again on arrival, quietly.** A playlist made from
  the sheet on Home is otherwise missing from the page until the app restarts.
  Quiet because blanking a list that is already correct, to redraw the same rows,
  reads as a page that failed and recovered.
- **The playlists page's scroll is the caller's**, for the tabs' reason: a
  collection opens *over* it, so a `rememberLazyListState` inside it dies there
  and coming back put the list at the top.
- **`Route.Saved` is depth 2 now.** It is reached from the playlists page rather
  than from Settings, and `forward` is `target >= initial` — so at equal depth
  leaving it animated as another step inward. The third time this exact trap has
  been sprung, after `Route.Channel` and `Route.Playlist`.

### The modal glass is darker than the bars'

One tint was wrong for two jobs. A bar is an edge content passes *under*, and
being able to read the feed through it is what makes it glass. A sheet is a
surface somebody stops at and answers, and at the bars' 0.75 the thumbnails
behind it competed with its own rows. `TINT_MODAL` is 0.90 — then 0.95, because
the reference given was the player's settings sheet, which is this same material
over a page that happens to be almost black. The rim and the lens still read at
the edges, which is what keeps it a pane rather than a panel.

The sheet also takes **62% of the screen** rather than the player's 45%: that one
is two switches over a video that must stay the subject, and this one is a list
somebody scans. At 45% a household of twelve collections showed three.

**The rows carry a round, centre-cropped picture** — the miniplayer's window and
its reasoning, since a 16:9 frame fitted into a circle is a stripe with two blank
caps. The saved shelf has no first video, so it draws its own bookmark; a
playlist with nothing in it yet draws the collection mark rather than an empty
grey circle.

## A sheet that was never drawn, and the tabs change hands (2026-09-02)

### The save sheet lived inside the watch session

Reported from the phone: a card's menu → *Save to playlist* did nothing, and
opening a video afterwards made the sheet rise on its own.

`SavePlaylistSheet` was written inside `if (session != null && browsing)` — the
branch that exists only while something is playing. With nothing playing the
press set the target and there was **no parent to draw it**; opening a video then
composed that branch with the target still set. It is a sibling of that branch
now, the last child of the root `Box`. Six screens open this one sheet and none
of them is about a video that happens to be playing.

### A recording inside a recording is a segfault, and now it cannot happen

Moving the playlists page from Settings to the tab bar crashed the app on sight:

```
EXC_BAD_ACCESS  SkRecordNoopSaveLayerDrawRestores → SkRecordOptimize
                → RenderNode.endRecording → LayerBackdropNode.draw
```

The page carried `glassSource()` because it *was* a route, and `AppShell` already
records every tab's content — so the same composable was correct in one place and
fatal in the other. The charter already recorded the sibling shape from
`PullGlass` and `GlassAlert`; this is the same rule failing in a new direction,
and the third time it has been paid for.

**So the rule is enforced rather than remembered.** `AppShell` provides
`LocalGlassRecording` around the tab it draws, and `glassSource()` is a no-op
when something above is already recording. A screen keeps asking whatever it is
used as, and the second ask does nothing.

### Playlists is a tab; subscriptions is a row in Settings

They swapped places. Both are lists somebody scans for one name; the difference
is how often — a household opens its own collections many times a sitting, and
opens the list of who it follows when it is looking for a channel, which is what
a menu of answers is for. New uploads from those channels already have a fixed
share of Home, so nothing about the feed changed by moving it.

- **`onBack` is nullable on the playlists page.** A tab is not reached from
  anywhere, so an arrow there leads nowhere — the setup screen's rule, and the
  same flag decides the content padding, because a tab ends above the tab bar and
  a page does not.
- **Back from a playlist or the shelf returns to the tab**, not to a route that
  no longer exists.
- **The tab icon is stroked, not `PlaylistIcon`.** That one is filled, drawn for
  a badge over a picture; a filled glyph beside two stroked ones is the icon
  fault this app has already had three times.

### The glass grew a size, a shape and a colour

- **The sheet floats.** `GLASS_MARGIN` at the sides and `max(navigationBars, 16)`
  under it, so it is inset like the three bars rather than welded to the bottom
  edge — and its corners are rounded on all four.
- **`PANEL_RADIUS` is 32dp and is the default** for anything that is not a
  capsule: the sheet and the alert. It is **concentric** with an iPhone 16e's
  47.33pt display corner across that 16dp margin — a radius of 47.33 inside a
  16dp inset draws a corner fatter than the phone's, and the gap then pinches at
  the corners and opens along the edges.
  - `GlassRadius.menu` is the one exception at 28dp, because a menu is narrow and
    the curve that reads as generous across a 390dp sheet arrives while the first
    row's text is still there.
- **The one call to action is the brand's red.** `GlassButton(primary = true)`
  used to wear the inverted surface, which is what *selected* means everywhere
  else in this app — so a button said "this is on" rather than "press this".
- **`GlassPill` is the design system's pill**, moved out of `WatchActions` when
  the playlist page needed the same one. Play all and Shuffle are two of them,
  neither louder than the other: they are two equal ways into one list, and a
  filled button on either would call the other a fallback.
- **Shuffle is an ordering, not a mode.** `queue.shuffled()` is handed over *as
  the queue*, so next and autoplay stay inside the shuffled order — a random
  first video followed by the list in its own order is what "shuffle" means to
  nobody. Nothing in the player changed.

### A playlist row says it is a playlist before it is read

The reference was YouTube's own card: a strip of the picture behind showing above
the thumbnail, and a small dark badge in the corner. The badge is
`BadgeBackground` — the same black 0.80 a video card's duration wears, because
two badges over two thumbnails in one list must not be two materials.

The saved shelf gets no strip: it is one set, not a stack of collections.

### The detail pages had two headings between them

`DetailScaffold` drew the title *beside* the arrow while Saved, History, a
channel and the search results all put a `ScreenTitle` at the head of their list
under a floating `DetailBack`. Two screens out of five in the settings menu
therefore looked like a different app. The scaffold now does what the other four
do, and `DetailTopRow` is gone.

## The chip for what was missed (2026-09-02)

Home mixes everything into one ranked page, so a new upload from a followed
channel competes with the rest of the library. **Bỏ lỡ / Missed** is a standing
answer to one question: what did the channels this household follows post in the
last day that nobody has watched?

The work was in the server repository first, because there was nothing to call —
`GET /api/feed/missed`, and its charter entry holds the ranking decisions. What
belongs here:

- **Its own repository method, not a topic.** `feed(topic =)` asks the gateway
  for the household's mix; this is a different endpoint answering a different
  question, and the mapper is the same because the gateway deliberately answers
  in the same shape.
- **`Chip.Missed` is not a `Category`**, exactly like `Chip.Live` and for both of
  its reasons: the server answers it from another endpoint, and **the chip is
  absent when the answer is empty**. "Nothing was missed" is a true and useful
  answer, and it is said by the chip not being there rather than by a blank grid.
- **One request, two jobs.** `missed()` is asked on every load — that is what
  decides whether the chip exists — and when the chip is the selected one, that
  same answer *is* the page. Nothing is fetched twice, and the two can never
  disagree about what is in the list.
- **A failure there leaves Home standing.** It is asked to decide whether one
  chip is drawn, and a feed that will not render because of that is a screen lost
  to a decoration.
- **The window is the server's**, read from its config per request. The app sends
  no `hours`: a number the app carries is a number that needs a release to change.
- `loadMore` is the one place the kind of chip decides where a page comes from.
  Everything else reads a token it was handed.

**The refresh gesture is answered on the server.** The app sends no page token on
a load, and the gateway mints a new rotation for the channel round-robin each
time — so pulling the list down leads with a different channel while every
channel still gets a row before any gets a second. Nothing on this side changed
to get it.

## What a finger gets back, and a mark for the app (2026-09-02)

### Glass moves when it is touched

Every control here is a pane of glass and pressing one did **nothing at all**:
`indication = null` everywhere, because Material's ripple is ink spreading
through paper and this app is not made of paper. That removed the wrong answer
and left no answer — a button with no acknowledgement is one people press twice.

There is no library for this. No Compose Multiplatform package draws an
iOS-26-style press, and the version of `backdrop` this project is pinned to
cannot move — 2.0.0 wants `compileSdk 37`, which is past what AGP 8.11.1
supports, and AGP cannot move because 9.x is incompatible with the KMP plugin.
So it is written here, on the library already in use.

- **`drawBackdrop` takes lambdas for everything** — shape, effects, highlight —
  and they are read at *draw* time. A press therefore costs a redraw and no
  recomposition, which on a chip row is the difference between 2% of movement
  and re-composing a `LazyRow` item at 120fps.
- **`GlassPress` is a holder, not a modifier**, because two things read it from
  two places: the layout scales the node and the *material* deepens its lens.
  One object is what keeps the halves in step.
- **Two springs, not one.** Pressing is quick and damped — meeting a surface;
  releasing overshoots — a sheet under tension let go. One symmetric curve reads
  as an animation playing rather than as a material responding.
- **Painted surfaces brighten instead**, since paint cannot refract.
- **`pressableGlassControl` and friends fold the material, the squash and the
  click into one modifier.** Written apart, the next control written gets the
  material and no press — which is exactly how fifteen of them ended up silent.
- **Measured**: holding the Share pill takes it from mean 0.436 to 0.486 while
  the Save pill beside it does not change by a single pixel. On the tab bar, the
  held item differs by RMSE 0.055 against 0.003 for its neighbours.

Cards keep their own 0.98 spring and are deliberately untouched: a card is a
picture, and that number says "the touch landed here", not "this is a button".
The player's controls over the video are untouched too — on iOS 26 they are
drawn by SwiftUI, so a Compose squash would show on Android only.

### The overflow menu is real glass now

It was `DropdownMenu` with a painted surface, and the two reasons written down
for that were both true and both about *where a popup draws*: its own coordinate
space reads the wrong slice, and its anchor sits inside the recorded layer where
sampling is a segfault. Neither is a fact about menus. So the menu moved to where
the sheets and the alert are — an ordinary child of the root `Box` — and
`MenuHost` carries the one open menu and the rectangle of the button it hangs
from. `menuSurface` and `DropdownMenu` are gone from the codebase.

- **The dismiss watcher is a modifier on the root, not a full-screen sibling.**
  Not consuming the event is not enough: Compose hit-tests siblings in reverse
  draw order and **stops at the first one hit**, so a node covering the screen
  takes the gesture from everything beneath it whether it consumes anything or
  not. Reported as the menu blocking the scroll, and measured — menu closed,
  feed motionless. An *ancestor* sees the Initial pass first and the list still
  receives it: one is a lid, the other a doorbell.
- **It grows from the button**, `TransformOrigin` computed from the anchor, so it
  is correct when the pane flips above rather than below.
- **Rows are 40dp with the pane carrying 10dp**, because a label centred in a row
  leaves half a row above and half below — so *between* two labels is a whole
  row's leftover and at an edge it is half of one. Measured before: 37dp against
  19dp, reported as uneven padding. And rows fill the pane's width: one that
  answers only where the letters are is one people press twice.

### The sheet's frame, measured off the platform

A screenshot of iOS's share sheet (591×1280, 1.5 px per point) puts its left edge
at x=14 and its right at 577 of 591 — 9.3pt each side — and its bottom edge
10.6pt above the screen. So `SHEET_MARGIN` is 10dp, **even on all three sides**;
the home indicator is cleared *inside* the pane instead. The corner follows: the
same screenshot's arc is ~35pt, which is 47.33 − 10 — the platform draws its
sheet concentric with the display, exactly the rule this app used for 32dp when
the margin was 16.

### Two bugs the phone found

- **A channel's uploads come from YouTube, so most have no catalogue row** — and
  pressing one navigated to a watch screen that answered *"gateway answered 404
  for /api/videos/bnNMULP-Ftc"*. `ChannelViewModel.openVideo` writes the row
  first and opens the id the server answers with, which is `SearchViewModel`'s
  path for the same reason. Measured on the reported video: `POST
  /api/videos/external` → `{"videoId":"bnNMULP-Ftc"}`, then 200 and an HLS
  stream.
- **The two "fetching this one" overlays were different spinners.** One
  composable now, `OpeningOverlay`: the search results and the channel page have
  the same state for the same reason and must not look like two apps.

### The app has a mark

The web app's own favicon — a house with a play in it, and its comment says why
it is not YouTube's rounded rectangle: that is a trademark rather than a layout.
iOS gets a 1024 asset catalogue entry, inset to 75% because a favicon read at
16px runs to its edges and an app icon is masked. Android gets an **adaptive icon
as a vector** rather than ten PNGs, with a monochrome layer for Android 13's
themed icons.

## Five from the narration, the lock screen and one changed tab (2026-09-02)

### A slot with no slack, and a client that starts late

Reported as every narration line being cut short. Neither half was wrong on its
own, and together they lose the end of every sentence.

The server fits each clip's audio to the gap before the next line —
`tempoFor` stretches it to fill the slot exactly — so **there is no slack in the
slot at all**. The client then started a clip up to a tick late and had it
replaced by the next one exactly on time, so the tail lost precisely what the
start had cost: the 250ms tick, plus fetching a WAV over the LAN.

- **Fixed on the client, because that is where the delay is.** Making the server
  fit into 92% of the slot would have worked by speaking faster to pay for
  something slow somewhere else, and would have meant regenerating every clip
  already cached.
- **`NarrationHost.prepare(url)`, and two players used in turn.** One player
  cannot buffer the next line while playing this one — loading a new item is
  what stops the current one, on both `ExoPlayer` and `AVPlayer`. So the clip
  after this is prepared on the idle player and starting it is a swap.
- **One ahead, not a queue.** A platform holding several is a platform deciding
  when they play, which is the one thing `Narrator` exists to keep in one place.
- **The tick is 100ms**, and `nextClipAfter` reads from the playhead rather than
  from the clip now speaking, so a seek lands on the right answer with no
  special case — the reasoning that makes the whole loop poll rather than
  schedule.

`NarrationClip.durationSeconds` is no longer what stops a clip. Its comment said
ducking must end when the line's time is up rather than when its file stops;
that is right about ducking and was wrong as the rule for the audio.

### The narration was read one sentence ahead, and it was the server's

Recorded here because it was diagnosed from this side and reported as an app
fault. `services/translate_server.py` asked the model for N numbered lines and
checked only that N came back; a model that merged two cues into line 1 repeated
a line to keep the count, and from there every cue carried the next cue's words.
The server's own changelog holds the measurement. **Nothing in this app was
wrong**, and the two clients differed only in that the web app had already been
fed the same cache.

The lesson worth keeping is the shape of the first fix considered: numbering the
lines by hash instead of by position. It would have changed nothing — the model
returned every number correctly and put the wrong text under it. **A check on
the envelope cannot catch a fault in the contents.**

### A screen that changes what it is used as loses the two-box trick

Pressing "+" on the playlists tab killed the app. The alert was already wrapped
in the two boxes this charter prescribes — a sampled backdrop drawn *outside*
the node that records the layer — and that arrangement is only load-bearing
while the screen records its own layer. As a **tab** it is composed inside
`AppShell`'s recording, where `glassSource()` is deliberately a no-op, so both
boxes were inside the layer the alert would sample and Skia's image-filter
bounds walk recursed until the stack went.

`PlaylistNameAlert` is drawn from `App.kt` now, beside `SavePlaylistSheet`, for
exactly the reason that sheet is. **The rule is not "wrap it in two boxes", it
is "draw it outside every recording"** — and the two are the same sentence only
for a route.

### `MPRemoteCommandCenter` is process-wide and its targets accumulate

Play video A, background it, play video B, then press Play on the lock screen:
**both were heard.** `NowPlaying` had a `registered` flag, which stops one
instance registering twice and says nothing about a second instance registering
beside it — and nothing ever removed a handler. Two players, eight targets, one
button, two answers.

`resign()` removes this instance's own handlers and is called from `release()`
as well as `stop()`. That is the one place the release/stop split does *not*
apply: releasing hands back this app's hold on the picture while the sound
carries on, but a player that has let go must not still answer the lock screen.

### The lock screen had no scrubber, and a comment claimed otherwise

`describe()` runs the moment the item is handed to the player, when the duration
is still zero — and a zero duration is written out as `MPNowPlayingInfoPropertyIsLiveStream`,
which is how iOS is told there is nothing to scrub. The comment beside it said
*"`progress` corrects it on the first tick"*. It did not: `progress` sent the
elapsed time and the rate and never the length. So every video was a live stream
with no length for its whole duration, and the lock screen offered no seek.

**A comment that describes what another function does is a claim about code
somebody else can change.** The length now travels on every tick beside the
position, where the two cannot disagree.

- **The artwork is fetched, reversing the decision recorded above.** *"A lock
  screen with a title and no picture is complete"* does not survive contact with
  the real thing — every other app has one, and its absence reads as an entry
  that failed to load. The objections it was refused on are answered rather than
  ignored: the fetch is asynchronous, a failure leaves the text untouched, and
  "unreachable off the house wifi" is equally true of the video.
- **A picture that lands late is only applied if it is still the right picture.**
  A viewer can press next while it is in flight, and without the check the lock
  screen shows the previous video under the new title.
