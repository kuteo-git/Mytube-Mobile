# Playlists — from the database up to the bookmark button

## Context

The bookmark on a video card writes one bit: `POST /api/videos/{id}/pinned`,
which asks the library to keep that file when the disk runs out of room. It is
not a collection, and there is nowhere to say *which* collection — so the
household cannot keep music apart from news.

What was asked for: pressing the bookmark opens a bottom sheet listing the
member's playlists, several can be ticked at once, a Save button applies the
choice, and playing a video from inside a playlist keeps autoplay inside that
playlist. Playlists and their contents are the same on every device, so they
live on the server. Mobile first, web after.

**The database is already built for this and the API is not.** Migration
`0015_playlists.sql` created `playlists` and `playlist_items`, with a `position`
column whose comment says *"appended to at the end when somebody adds a video
here"*. Above the repository interface in `services/catalog/internal/domain/catalog.go:293`
sits a doc comment for a `SetPlaylistItem` method that **does not exist** —
declared nowhere, implemented nowhere. The gateway exposes only `GET
/api/playlists` and `GET /api/playlists/{id}`; nothing can create a playlist,
add to one, or remove from one over HTTP. The running server answers
`{"playlists":[]}`.

So the order of work is **server → mobile → web**, even though the request said
mobile first: neither client has anything to call.

Two repositories:

- `/Volumes/Data2/git/Youtube` — the server. **Its `CLAUDE.md` is the authority
  there**; read it before touching anything, especially §8.1 and the migration
  conventions.
- `/Volumes/Data2/mytube-app` — this app.

## Decisions already made (do not re-litigate)

| | |
|---|---|
| Settings row | The existing **"Đã lưu" row becomes "Playlist"** — one row, not two |
| Default playlist | **Is the existing `pinned` set**, presented as a playlist that cannot be deleted, renamed, or removed. It is not a row in `playlists`; that is *why* it cannot be deleted, rather than a rule invented for the UI |
| Sheet | Multi-select, scrollable, **Save applies the difference** — unticking removes. A tick that does not remove is a control that lies |
| Create | From the sheet ("Playlist mới", inline name field) **and** from the playlists screen |
| Rename | Yes, via `UpdatePlaylist` (title + description), from the playlist page's overflow |
| Delete | Yes, from the playlist page's overflow, with one confirmation. Absent on the default playlist rather than shown and refused |
| Remove one video | From the card's overflow on the playlist page |
| Reorder | **No**, this round. Order is the order things were added |
| Queue | Opening a video from a playlist page passes the playlist's ids as `WatchSession.queue`; autoplay and next already read it |
| YouTube results | **Can** be added: the card gains a three-dot menu with one item, "Lưu vào playlist". Saving ensures the catalogue row first (`POST /api/videos/external`), then adds |
| UI | Follows the existing design system — `GlassSheet`, `GlassComponents.kt`, `DetailScaffold`. **No Material widgets** |

---

# Part 1 — The server (`/Volumes/Data2/git/Youtube`)

Nothing in the app can start before this lands and the gateway is restarted.

## 1.1 Proto (`proto/catalog/v1/catalog.proto`)

Four RPCs on the `Catalog` service, beside the existing playlist block near
line 92:

```proto
// Somebody adding one video to one of their own lists, from a client.
// Distinct from ImportPlaylistItems, which is the account importer's bulk
// write and carries a `complete` flag this has no meaning for.
rpc AddPlaylistItem(AddPlaylistItemRequest) returns (AddPlaylistItemResponse);
rpc RemovePlaylistItem(RemovePlaylistItemRequest) returns (RemovePlaylistItemResponse);
rpc UpdatePlaylist(UpdatePlaylistRequest) returns (UpdatePlaylistResponse);
rpc DeletePlaylist(DeletePlaylistRequest) returns (DeletePlaylistResponse);
```

Messages, following the shape of the ones already there (`user_id` on every
request — a playlist belongs to a member, and the id alone must never be enough
to reach one):

- `AddPlaylistItemRequest { playlist_id, user_id, video_id }` → `AddPlaylistItemResponse {}`.
  **Idempotent**: adding a video the list already holds is success, not an error
  — the sheet may be saved twice and a duplicate key must not surface as a
  failure. The primary key `(playlist_id, video_id)` already enforces this;
  `ON CONFLICT DO NOTHING` is the whole implementation.
- `RemovePlaylistItemRequest { playlist_id, user_id, video_id }` → empty response.
  Removing something absent is also success.
- `UpdatePlaylistRequest { playlist_id, user_id, title, description }` →
  `UpdatePlaylistResponse { Playlist playlist }`. One RPC rather than
  `RenamePlaylist`, because `CreatePlaylist` already carries both fields and an
  RPC that changes half of what creation set is one that grows a sibling later.
- `DeletePlaylistRequest { playlist_id, user_id }` → empty response.
  `playlist_items` has `ON DELETE CASCADE`, so the items go with it.

Then `make proto` (runs `cd proto && buf generate`; generated Go and TS are
committed). `make proto-lint` and `make proto-breaking` before committing —
these are additions, so breaking should stay clean.

## 1.2 Catalog service

**`internal/domain/catalog.go`** — the repository interface, around line 293
where the orphaned `SetPlaylistItem` comment sits. Declare what the comment
describes, but as the two verbs the API has:

```go
AddPlaylistItem(ctx context.Context, playlistID, userID, videoID string) error
RemovePlaylistItem(ctx context.Context, playlistID, userID, videoID string) error
UpdatePlaylist(ctx context.Context, playlistID, userID, title, description string) (Playlist, error)
DeletePlaylist(ctx context.Context, playlistID, userID string) error
```

Delete the orphaned comment or fold it into `AddPlaylistItem` — a comment
describing a method nobody wrote is worse than none.

**`internal/adapter/postgres/repository.go`** — the SQL. Every statement is
keyed by `(id, user_id)` so one member cannot touch another's list:

- Add: `INSERT INTO playlist_items (playlist_id, video_id, position)
  SELECT $1, $2, COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlist_id = $1`
  guarded by an `EXISTS (SELECT 1 FROM playlists WHERE id = $1 AND user_id = $3)`,
  with `ON CONFLICT (playlist_id, video_id) DO NOTHING`. Also bump
  `playlists.updated_at`.
  **The position is computed in the same statement, not read then written** —
  two clients adding at once would otherwise collide on the same number.
- Remove: `DELETE FROM playlist_items USING playlists WHERE …`, bump `updated_at`.
- Update: `UPDATE playlists SET title = $3, description = $4, updated_at = now()
  WHERE id = $1 AND user_id = $2 RETURNING …`.
- Delete: `DELETE FROM playlists WHERE id = $1 AND user_id = $2`.

A playlist that is not this member's must come back as **not found**, never as
"nothing happened".

**`internal/usecase/catalog.go`** — four thin methods beside `CreatePlaylist`
(line ~389), doing the validation that file already does elsewhere: reject an
empty `userID`, reject an empty `playlistID`, and for `UpdatePlaylist` reject an
empty title (`CreatePlaylist` almost certainly already has that check — reuse
it rather than writing a second one).

**`internal/adapter/rpc/server.go`** — four handlers in the shape of
`CreatePlaylist` at line 708: unwrap the request, call the usecase, map the
error with `toConnectErr`.

**Tests** — `internal/usecase/` has `saved_test.go` and `evict_test.go` as
models. Cover: adding twice is one row; adding to somebody else's playlist
fails; removing something absent succeeds; deleting takes the items with it;
position increments.

## 1.3 Gateway

**`internal/api/router.go`**, beside the two existing playlist routes at line
185. Order matters — the `{id}` route must not swallow a literal:

```go
mux.HandleFunc("POST /api/playlists", g.handleCreatePlaylist)
mux.HandleFunc("PATCH /api/playlists/{id}", g.handleUpdatePlaylist)
mux.HandleFunc("DELETE /api/playlists/{id}", g.handleDeletePlaylist)
mux.HandleFunc("POST /api/playlists/{id}/items", g.handleAddPlaylistItem)
mux.HandleFunc("DELETE /api/playlists/{id}/items/{videoId}", g.handleRemovePlaylistItem)
```

Handlers follow `handleSetPinned` (line 695): decode the body, call catalog with
`g.userID(r)`, `204 No Content` on success — except create and update, which
answer `200` with the `playlistDTO`.

**The one new read, and the reason the sheet is one request** —
`handleListPlaylists` (line 1105) gains an optional `?videoId=`:

```
GET /api/playlists?videoId=abc123
{"playlists":[{ …playlistDTO…, "containsVideo": true }, …]}
```

`containsVideo` is added to `playlistDTO` in `internal/api/dto.go:232` with
`json:"containsVideo"`. Without the parameter it is absent/false and every
existing caller is unaffected. Implementing it needs one more repository read —
`ListPlaylistIDsContaining(ctx, userID, videoID)` — and the gateway merges;
**do not fetch each playlist's contents**.

The alternative shapes were considered and refused: `GET
/api/videos/{id}/playlists` is a second request for one screen that is useless
without the first, and a client-side merge means N requests.

**Tests** — the gateway has table tests for its handlers; add the membership
flag and the `videoId` parameter to them.

## 1.4 Verifying the server before touching the app

```sh
# from /Volumes/Data2/git/Youtube, with the stack running
curl -X POST localhost:8180/api/playlists -H 'X-User-Id: dev' \
     -H 'Content-Type: application/json' -d '{"title":"Nhạc"}'
curl -X POST localhost:8180/api/playlists/<id>/items -H 'X-User-Id: dev' \
     -H 'Content-Type: application/json' -d '{"videoId":"gEWF0LL4IPA"}'
curl 'localhost:8180/api/playlists?videoId=gEWF0LL4IPA' -H 'X-User-Id: dev'
curl localhost:8180/api/playlists/<id> -H 'X-User-Id: dev'
curl -X DELETE localhost:8180/api/playlists/<id>/items/gEWF0LL4IPA -H 'X-User-Id: dev'
curl -X DELETE localhost:8180/api/playlists/<id> -H 'X-User-Id: dev'
```

Copy the real response bodies into the app's mapping test — the rule
`StreamMappingTest` and `DiscoverMappingTest` were both written under.

---

# Part 2 — The app (`/Volumes/Data2/mytube-app`)

Layers, in the order they should be written. Every file below already has
siblings doing the same job; copy their shape rather than inventing one.

## 2.1 Domain — `domain/model/Playlist.kt` (new)

```kotlin
data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val itemCount: Int,
    /** Whether the video the sheet was opened for is already in this list. */
    val containsVideo: Boolean,
    /** First few thumbnails, for the card on the playlists screen. */
    val thumbnailPaths: List<String>,
)
```

No `@Serializable`. Nothing nullable — `description` empty rather than null,
the rule §3 of the charter states.

**The default playlist is not a `Playlist`.** It is the pinned set, and the sheet
draws it as a fixed first row from `saved()`. Modelling it as a `Playlist` with a
made-up id would put a row in the list that no server call can delete or rename,
and every call site would need to remember which id was magic.

## 2.2 Data

- `data/remote/dto/PlaylistDto.kt` (new, or append to `VideoDto.kt` beside
  `ExternalVideoDto`): `PlaylistDto`, `PlaylistsDto`, `PlaylistPageDto`,
  `CreatePlaylistRequest`, `UpdatePlaylistRequest`, `AddPlaylistItemRequest`,
  plus `fun PlaylistDto.toDomain()`. Every field defaulted.
- `data/remote/GatewayDataSource.kt`: `playlists(baseUrl, userId, videoId = "")`,
  `playlist(...)`, `createPlaylist`, `updatePlaylist`, `deletePlaylist`,
  `addPlaylistItem`, `removePlaylistItem`. Follow `discover` / `ensureExternal`
  for the parameter and body style; use `client.delete` for the two deletes
  (import `io.ktor.client.request.delete`) and `client.patch` for update.
- `domain/repository/VideoRepository.kt`: the same seven as suspend functions,
  documented — in particular that `playlists(videoId)` answers "which lists are
  there, and which hold this video" in one call.
- `data/repository/VideoRepositoryImpl.kt`: the implementations, one line each.

**Every existing fake `VideoRepository` in `commonTest` must gain the new
members** (`HomeViewModelTest.FakeVideos`, `SearchViewModelTest.FakeVideos`) or
the test source stops compiling. That is the guard working, not a problem.

## 2.3 The sheet — `ui/playlist/SavePlaylistSheet.kt` + `SavePlaylistViewModel.kt` (new)

The centrepiece. `BoxScope.GlassSheet(visible, backdrop, scrim, onDismiss)` is
the container (`ui/shell/GlassSheet.kt:87`) — it is an ordinary child of the
caller's full-screen `Box`, **written last**, and it must be *always composed and
told whether it shows*, or the exit animation has no node to play on.

State:

```kotlin
sealed interface SaveSheetState {
    data object Loading; data class Failed(val message: String)
    data class Ready(
        val savedTicked: Boolean,          // the default playlist (pinned)
        val playlists: List<Playlist>,
        val ticked: Set<String>,           // playlist ids ticked right now
        val creating: Boolean,             // the inline name field is open
        val newName: String,
        val saving: Boolean,
    )
}
```

- Opens on `videoId`; loads `playlists(videoId)` **and** the pinned state. The
  pinned state is already known by the card that opened the sheet (`Video.saved`)
  — pass it in rather than making a second request for a fact in hand.
- `ticked` starts as the server's answer; the diff on Save is
  `ticked - original` (add) and `original - ticked` (remove), plus the pinned bit
  if it changed. **Requests are one per change**, not one "final state" call: a
  single tap is usually one or two changes, and an endpoint that takes the whole
  state is an endpoint that can empty a playlist when a client is wrong.
- **For a YouTube result**, `ensureExternal(sourceUrl)` runs first, on Save, and
  its id is what the adds use. The button shows `loading` while it does — this is
  a real round trip, and the pattern is already written in
  `SearchViewModel.openExternal`.
- Rows: 56dp, tick on the right, and **selected is a change of *kind*** —
  `glassControl(selected = true)`, the inverted solid surface, with the content
  colour following it (the lesson the Save pill cost: white content on a white
  selected pane). No Material checkbox exists in this app; do not add one.
- The list scrolls inside the sheet **below the drag handle**, or the scroll
  container takes the dismiss gesture.
- "Playlist mới" is the first row under the default one; pressing it swaps that
  row for a `GlassTextField` (`ui/shell/GlassComponents.kt:189`) plus a confirm.
  Creating adds the new playlist to the list **already ticked** — somebody who
  just named a list for this video means to put it there.
- Bottom: `GlassButton(primary = true)` labelled Save, `loading` while the diff
  is being applied, disabled when nothing changed.

## 2.4 The playlists screen — `ui/playlist/PlaylistsScreen.kt` + ViewModel (new)

Reached from Settings. Shape copied from `saved/SavedScreen.kt`: a
`Box(Modifier.fillMaxSize().glassSource())`, a `LazyColumn` with
`detailContentPadding()`, `ScreenTitle` as the first item, `DetailBack` drawn
over every state.

- First row is the **default playlist** — the pinned shelf — opening
  `SavedScreen` as it does today.
- Then one row per playlist: thumbnail strip (first of `thumbnailPaths` via
  `imageModel`), title, `itemCount`.
- A "+" in the title row opens the same create field as the sheet.
- Empty state: `EmptyState(title, detail)` — two lines, because one reads as a
  fault.

## 2.5 The playlist page — `ui/playlist/PlaylistScreen.kt` + ViewModel (new)

Again `SavedScreen`'s shape. Header: title, count, and an overflow (⋮) carrying
**Đổi tên** and **Xoá playlist**; the overflow is *absent* on the default
playlist rather than present and refusing. Rename opens the same name field;
delete asks once (a small `GlassSheet` or a confirm row — no Material dialog).

Cards use `VideoCard` with `onSave = { remove from this playlist }` and
`saveLabel = strings.removeFromPlaylist` — the exact pattern
`SavedScreen` already uses for `removeFromSaved`.

`onOpenVideo` passes **the whole page's ids** as the queue:

```kotlin
onOpenVideo = { id -> watching = WatchSession(id, queue = ready.videos.map { it.id }) }
```

`WatchSession.queue` already exists and the channel page already does this; next
and autoplay read it, so **no player code changes**.

## 2.6 Wiring — `ui/App.kt`

- `Route.Playlists` and `Route.Playlist(id)`, both depth 1 — except
  `Route.Playlist`, which is opened *from* `Route.Playlists`, so it is **depth 2**
  for the same reason `Route.Channel` is (equal depths give the back transition
  no direction; that bug was fixed today and must not be reintroduced).
- Both added to `browsing` so the miniplayer follows.
- Settings' Saved row becomes the Playlist row → `Route.Playlists`.
- The sheet is hoisted to `App.kt` and drawn **last**, like `ProfileSheet` was:
  it is opened from cards on Home, Search, History, Saved, a channel and the
  watch screen, so one instance keyed by the video it was opened for. A
  `var savingVideo: SaveTarget?` (id + sourceUrl + current pinned bit) is the
  state; every `onSave` callback sets it instead of calling `setSaved`.

## 2.7 The card menus

- `VideoCardMenu` (`ui/home/VideoCard.kt:309`): the Save row's label becomes
  "Lưu vào playlist" and its action opens the sheet. `onNotInterested` and
  `onMarkWatched` unchanged.
- `ExternalVideoCard` (`ui/search/ExternalVideoCard.kt`) **gains a three-dot
  menu** with exactly one item, "Lưu vào playlist" — the layout in the chosen
  mock: the dot sits at the end of the title/meta block, as on the library card.
  Reuse `menuSurface`; a popup cannot carry a sampled backdrop (it renders in its
  own coordinate space, and inside the recorded layer it crashes Skia — both
  faults are already in the charter).
- The watch screen's Save button (`ui/watch/WatchActions.kt`) opens the sheet too.

## 2.8 Strings — `ui/i18n/Strings.kt`

New properties, both languages, or the build stops (that is the point):
`playlists`, `playlistsDetail`, `newPlaylist`, `playlistName`, `savePlaylist`,
`removeFromPlaylist`, `renamePlaylist`, `deletePlaylist`, `deletePlaylistConfirm`,
`noPlaylists`, `noPlaylistsDetail`, `emptyPlaylist`, `emptyPlaylistDetail`,
`saveToPlaylist`. Vietnamese: "Playlist", "Playlist mới", "Tên playlist",
"Lưu vào playlist", "Bỏ khỏi playlist", "Đổi tên", "Xoá playlist"…

`savedTitle` stays — the default playlist is still called Đã lưu.

## 2.9 Tests (`commonTest`)

- `data/PlaylistMappingTest.kt` — bodies **copied from the running gateway**,
  including one with `containsVideo` and one with fields missing.
- `ui/SavePlaylistViewModelTest.kt` — the diff is the thing to pin down:
  ticking one adds one; unticking one removes one; ticking then unticking sends
  nothing; the pinned row and the playlist rows are independent; creating a
  playlist leaves it ticked; an external video ensures first and adds with the
  **returned** id; a failed ensure adds nothing.
- `ui/PlaylistViewModelTest.kt` — removing a video takes it off the page, and a
  failed removal puts it back (unlike the feed's "not interested", this page is
  the list itself, so a row that failed to leave must return).

## Verification

```sh
source env.sh
./gradlew jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```

Then both simulators — Android `emulator-5554`, iOS `iPhone 16e`
(`C6825263-6297-40D0-A994-72E4AFC851E7`, bundle id **`com.xtube.com`**, and
`xcodebuild` needs `-destination 'id=…'` or it picks x86_64 and Kotlin/Native
refuses):

1. Settings → Playlist → tạo "Nhạc" và "Tin tức"; both appear, count 0.
2. Home → ⋮ on a card → sheet lists Đã lưu, Nhạc, Tin tức; tick Nhạc → Save.
3. Reopen the same card's sheet: Nhạc is ticked, from the server.
4. Untick Nhạc → Save → the video leaves the playlist page.
5. Playlist page → play the second video → press next → it plays the **third
   video of that playlist**, not a recommendation.
6. Search a YouTube result → ⋮ → Lưu vào playlist → it appears in the playlist
   with its metadata (this is the ensure path).
7. Rename and delete a playlist; the default one offers neither.
8. On the web (after Part 3) the same lists appear — or, before that, confirm
   with `curl /api/playlists`.

## Part 3 — The web, afterwards

Not planned in detail here. It has `catalogRepository.ts` with `getPlaylist`
already, so the work is the mutations plus the same sheet; do it once the app has
proved the API's shape.

## Deferred, recorded rather than forgotten

Reordering inside a playlist (the `position` column is where it would go);
sharing a playlist between members (a column on `playlists`, as its migration
comment says); Watch Later, which the proto deliberately keeps out of
`ListPlaylists` because it has no name and cannot be created or deleted.
