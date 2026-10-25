# Television QA — 2026-10-25

Run by hand on the TV emulator (`mytube_tv`, Google TV, 1920×1080), against the
household's gateway, with a screenshot at every check. The real Chromecast was
off for the night; the signed release was installed on it afterwards.

**Result: 45 cases — 38 passed first time, 7 failed. 6 of the 7 are fixed and
re-checked on the emulator; 1 is open (F-Back, below). One further fix (D4) is
in code but could not be re-checked: the only captioned video at hand would not
play on the emulator.**

Data-changing cases were net zero: a test video was added to Saved and to the
playlist "random" through the save sheet, then removed from both through the
long-press menu.

## A — Launch

| | Case | Result |
|---|---|---|
| A1 | Launch → "Who's watching", last profile focused | Pass (since 2026-10-25 asked only until a profile is chosen) |
| A2 | OK → Home, first card of the first shelf focused | Pass |

## B — Home

| | Case | Result |
|---|---|---|
| B1 | Right ×2 along shelf 1; the focused card pivots, rim visible | Pass |
| B2 | Down from shelf-1 card #3 → shelf-2 card #1 | Pass |
| B3 | Up → back to shelf-1 card #3 | Pass |
| B4 | Down again → the shelf-2 card left on | Pass |
| B5 | Down through several shelves: focused shelf held about a third down; topic shelves load | Pass |
| B6 | Left from the first card opens the rail on Home; Right returns to the card | Pass |
| B7 | Home picked again on the rail → top, first card focused, rail closes | Pass |

## C — Long press

| | Case | Result |
|---|---|---|
| C1 | Hold OK on a card → menu, focused, solid pane | Pass |
| C2 | Back closes the menu, focus back on the card | Pass |
| C3 | Menu → Save to playlist → sheet focused; Back → focus back on the card | Pass, but the sheet was see-through — **fixed** (solid modal fallback) |
| C4 | A short OK still opens the video | Pass |

## D — Player

| | Case | Result |
|---|---|---|
| D1 | OK opens the video full screen, nothing over the picture | Pass |
| D2 | Right/Left seek: storyboard preview travels with the target | Pass |
| D3 | OK pauses and shows the controls | Pass |
| D4 | Down → controls with focus on up next #1 | Pass; captions showed through the controls — **fixed in code, not re-checked** |
| D5 | From Like in the control row, Down → up next #1 | Pass |
| D6 | Up past the seek bar hides the controls | Pass |
| D7 | Back hides the controls, Back again closes; focus back on the Home card | Pass |
| D8 | Continue watching has the video just watched | **Failed → fixed**: the card moved to #1 and the row did not follow, cutting it off at the left edge |
| D9 | Hold OK in up next → menu → sheet → Back: controls still up, focus on that card | Pass |
| D10 | Controls hide by themselves after about 5 s while playing | Pass |
| D11 | Settings opens the side panel; Back closes the panel only | Pass; focus went to Play — **fixed**: back to the button that opened it |
| D12 | Narration button on a captioned video toggles | **Failed → fixed**: the button lost its focus rim when switched |

## E — Grids

| | Case | Result |
|---|---|---|
| E1 | Playlists tab: three across, title, + button | Pass |
| E2 | Playlist: header whole; Down into the grid, rows held about a third down | Pass |
| E3 | Up to the header: header whole | Pass |
| E4 | Hold OK → Remove from playlist: card goes, focus to its neighbour | Pass |
| E5 | Back to Playlists: focus on the tile opened | Pass |
| E6 | Saved grid: hold OK → Remove | Pass |
| E7 | Remove from Saved: card goes, focus to its neighbour, not the rail | Pass |
| E8 | Watch history: grid, pivot, hold OK → save; Back → focus on its Settings row | Pass |
| E9 | Channel: subscribe and sort chips show the avatar; grid pivots | Pass |
| E10 | Channel sort change → skeleton, then cards | **Failed → fixed**: stuck on the skeleton for good — a next page asked for during the sort wrote back a stale "sorting" state. Red test first (`ChannelViewModelTest`) |

## F — Search

| | Case | Result |
|---|---|---|
| F1 | Search opens with focus on the first key | **Failed → fixed**: focus landed on the microphone |
| F2 | Typing shows results on the right | Pass |
| F3 | Vietnamese layout and a tone key | Pass |
| F4 | Hold OK on a library result → save menu | Pass |
| F5 | Open a result, Back → search, focus on the result | Pass |

## G — Settings

| | Case | Result |
|---|---|---|
| G1 | A page from Settings and Back → focus on its row | Pass |
| G2 | Settings picked again on the rail → top, first row focused | Pass |

## H — Lifecycle

| | Case | Result |
|---|---|---|
| H1 | Home key while playing → paused; reopen → one `MainActivity` | Pass |
| H2 | Back on Home with nothing open → the app closes | Pass |

## Open

- **F-Back — a page reached from a page starts from its first control when it is
  come back to.** Back from a channel to Subscriptions focuses the first row,
  not the channel. Only the tabs stay composed under what they open; a page
  under a page is disposed and composed again, so the control focus was on no
  longer exists. The fix is to keep each page's saved state (a
  `SaveableStateHolder` around the routes, and `focusRestorer` on their lists) —
  larger than this pass, and it would touch the phone's navigation.
- Back from Search to Home lands on the rail's Home item, not the card left on —
  the rail redirects entry to the current tab. Debatable; left.
- Down from the channel's centred Subscribed button skips the sort chips; Right
  past the last chip drops to a card. Compose's geometric search; minor.
