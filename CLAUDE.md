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
- **There is no "On YouTube" half.** The web app splits its results, but the
  gateway has one search route and it answers from the catalogue only. A heading
  over a single list would promise a second one that never arrives.
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
