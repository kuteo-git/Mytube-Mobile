# Mytube QA test cases

Format: ID | area | pri | preconditions | steps | expected. Platform: iOS Simulator iPhone 16e. "Lib" = household library on gateway.

## A. Launch / Setup
- A01 P0 | Launch | configured server | Cold launch, wait 6s | Lands on Home, feed loads with thumbnails; no tab switch on its own.
- A02 P1 | Launch | | Relaunch 3x | Always same tab (Home), no flicker to another tab.
- A03 P1 | Setup | Settings > Server | Open server screen, press back | Returns to Settings, address unchanged.
- A04 P1 | Setup | | Server screen shows current address, back arrow present | Arrow visible, Save/Check buttons are glass/red, not Material blue.
- A05 P2 | Setup | | Focus address field with keyboard | Form scrolls/lifts; Save not under keyboard.
- A06 P2 | Setup | | Enter unreachable address, press Check (do not Save) | Error message shown, no crash.

## B. Home
- B01 P0 | Feed | | Open Home | Chips row, Continue watching rail (if any), cards edge-to-edge with one meta line.
- B02 P0 | Feed | | Scroll down 10 pages | Loads more (infinite scroll), no crash, no duplicate rows.
- B03 P1 | Chips | | Tap each chip (All, Missed?, Live?, topics) | Selected chip inverted; list changes; chip row does not jump scroll.
- B04 P1 | Chips | | Scroll feed down/up | Chip row stays pinned; top bar chip row never hides.
- B05 P1 | Refresh | | Pull to refresh | Glass indicator under chips, list refreshes, indicator disappears.
- B06 P1 | Card | | Tap card | Opens watch screen playing that video.
- B07 P1 | Card | | Tap channel avatar | Opens channel page.
- B08 P1 | Card | | Tap overflow dot | Menu "Save to playlist" / "Not interested" (do not confirm Not interested).
- B09 P1 | Card | | Menu: tap outside | Menu dismisses; scroll not blocked.
- B10 P1 | Continue | | Continue watching rail cards | Red progress bar, overflow menu present, opens video resumed.
- B11 P2 | Bars | | Scroll down then up | Top bar hides on down, returns on up (48dp), tab bar never leaves.
- B12 P2 | Press | | Hold chip | Chip blooms outward, not clipped.
- B13 P2 | Missed | | Missed chip present only if there are items | If shown, list non-empty.

## C. Watch
- C01 P0 | Play | | Open video | Plays, picture 16:9, title/channel/actions below.
- C02 P0 | Controls | | Tap picture once | Controls show; paused video keeps controls.
- C03 P0 | Play/Pause | | Tap pause disc, tap again | Pauses/resumes, icon swaps.
- C04 P1 | Seek tap | | Tap seek bar at 50% | Playhead jumps ~50%.
- C05 P1 | Seek drag | | Drag bar | Playhead follows; scrub preview still appears (if storyboard) with time pill.
- C06 P1 | Scrub preview | storyboard video | Hold drag | Picture replaced by still, time pill at foot, bar visible.
- C07 P1 | Double tap | | Double tap right/left | +/-10s jump, badge accumulates, controls not toggled.
- C08 P1 | Prev/Next | rail non-empty | Tap next | Next video from rail starts from beginning; previous returns.
- C09 P1 | Autoplay | autoplay on | Seek near end, let finish | Next video starts.
- C10 P1 | Settings sheet | | Tap gear | Glass sheet rises: subtitles, narration, autoplay; page behind not dimmed.
- C11 P1 | Settings sheet | | Dismiss: tap outside, drag down | Sheet closes both ways.
- C12 P1 | Subtitles | video with tracks | Toggle CC / choose track | Captions drawn once (not doubled), turn off removes.
- C13 P1 | Autoplay toggle | | Toggle in sheet | Persists across next video.
- C14 P1 | Narration | short video | Toggle on | Progress "preparing x/y", then plays; toggle off stops.
- C15 P2 | Narration live | live on air | Toggle offered only if captions | -
- C16 P1 | Fullscreen | | Tap zoom button | Landscape/rotated, bars hidden; back arrow/chevron exits fullscreen.
- C17 P2 | Fullscreen | | Pinch out in fullscreen | Fills screen and stays; pinch in restores.
- C18 P1 | Fullscreen | | Controls in fullscreen | Title+channel top-left aligned; clock & zoom bottom.
- C19 P1 | Like | | Like then like again | Filled thumb then cleared; server reaction matches; restore original.
- C20 P1 | Dislike | | Dislike then undo | Mutually exclusive with like.
- C21 P1 | Share | | Tap Share | iOS share sheet with YouTube link.
- C22 P1 | Save | | Tap Save | Opens playlist sheet.
- C23 P1 | Save sheet | QA- playlist | Tick QA playlist, Save | Video appears in playlist (server verify).
- C24 P1 | Save sheet | | "+" create playlist | Alert to name; creates and ticks.
- C25 P1 | Description | | Tap description box | Expands; scroll away and back; stays expanded; "Show less" collapses.
- C26 P1 | Comments | | Open comments section | Read-only list, replies nested; no input field.
- C27 P1 | Up next | | Header + chips All / From channel | Rows horizontal 168dp thumb; chip filters list.
- C28 P1 | Up next | | Tap row | Plays that video.
- C29 P1 | Channel link | | Tap channel name/avatar on watch | Opens channel page.
- C30 P2 | Loading | cold video | Open uncached video | Skeleton matches page; no double picture.
- C31 P2 | Screen sleep | | N/A on simulator | -
- C32 P1 | Subscribe | | Subscribe button present (do not press) | Rendered.
- C33 P2 | Ended | | Let video end, autoplay off | Stays ended; no replay loop.

## D. Miniplayer
- D01 P0 | Collapse | playing | Drag picture down > 1/3 screen | Collapses to miniplayer bar, continues playing.
- D02 P0 | Spring back | | Drag down < 1/3 | Springs back, still playing.
- D03 P0 | Chevron | | Tap chevron | Collapses to miniplayer.
- D04 P1 | Mini | | Tap bar | Expands to watch screen.
- D05 P1 | Mini | | Play/pause button | Toggles playback.
- D06 P1 | Mini | | X | Closes, playback stops, bar gone.
- D07 P1 | Mini | | Switch tabs | Bar persists on all tabs.
- D08 P1 | Mini | | On Search, Channel, Playlist pages | Bar present.
- D09 P2 | Mini | | Drag landing | Picture lands in round window.
- D10 P2 | Mini | | Long title | Marquee scrolls only if too long.
- D11 P1 | Mini | | Back gesture with expanded video | Collapses; with collapsed video back is not swallowed.

## E. Tab bar
- E01 P0 | Tabs | | Tap each tab | Switches; selected pill/filled glyph.
- E02 P1 | Drag | | Drag finger across bar | Pill follows, screen switches on release only.
- E03 P1 | Collapse | miniplayer | Scroll down on feed | Tab bar narrows to circle + mini + search.
- E04 P1 | Collapse | | Tap collapsed bar | Opens bar only, no scroll-to-top.
- E05 P1 | Retap | bar whole | Tap current tab | Scrolls to top.
- E06 P2 | Scroll mem | | Scroll Home, go other tab, return | Position remembered.
- E07 P2 | Labels | | Labels not clipped (Playlists, Settings) | Descenders visible.

## F. Search
- F01 P0 | Open | | Tap search | Search screen, field at bottom with keyboard.
- F02 P0 | Query | | Type "phone" | Results after ~300ms; library half.
- F03 P1 | YouTube | | Same query | "From YouTube" results below/also present.
- F04 P1 | Open external | | Tap YouTube result | Spinner then watch plays (no 404/refusal).
- F05 P1 | Video URL | | Paste a youtube watch URL | One result for that video.
- F06 P1 | Channel URL | | Type youtube.com/@mkbhd | Leaves to channel page.
- F07 P1 | Keyboard | | Drag on empty Idle area and on results | Keyboard dismisses.
- F08 P1 | Close X | | Tap X in row | Leaves screen to previous.
- F09 P1 | Clear | | Tap clear mark in pill | Empties query.
- F10 P1 | Caret | | Type, close, reopen | Caret at end of text.
- F11 P1 | Tap pill | | Tap pill outside text | Field focuses.
- F12 P2 | Avatar | | Tap avatar on result | Opens channel.
- F13 P2 | Empty | | Nonsense query | Empty state, no crash.
- F14 P2 | Mini | | With miniplayer | Mini sits just above row, same gap as tab bar.
- F15 P2 | More | | Scroll to end of YouTube half | Loads more.

## G. Channel
- G01 P0 | Open | | From card avatar | Header with name/counts, uploads list; back arrow.
- G02 P1 | Sort | | Tap Popular/Oldest | Chip lit; list reloads in new order.
- G03 P1 | Upstream video | | Tap an upload | Plays (no 404).
- G04 P1 | Back | | Back from channel opened via history/search/watch | Returns to origin, not Home.
- G05 P2 | Scroll | | Scroll to end | Loads more, no dupes.
- G06 P2 | Subscribe | | Button shown | Rendered.

## H. Playlists
- H01 P0 | Tab | | Open Playlists tab | List with Saved shelf first.
- H02 P1 | Create | | "+" name QA-One | Appears first in list; server confirms.
- H03 P1 | Rename | | Rename QA-One -> QA-Two | Cursor at end of name; renamed.
- H04 P1 | Add | | Add video via save sheet | itemCount increases.
- H05 P1 | Open | | Open playlist | Videos listed, Play all / Shuffle pills.
- H06 P1 | Play all | | Tap Play all | Plays first; next stays in list.
- H07 P1 | Shuffle | | Tap Shuffle | Plays in shuffled order.
- H08 P1 | Remove | | Overflow remove item | Item gone; server updated.
- H09 P1 | Delete | | Delete QA playlist | Removed from list immediately.
- H10 P1 | Saved shelf | | Open Saved | Lists pinned videos; menu says Remove from saved.
- H11 P1 | Back | | Back from playlist | Returns to tab.
- H12 P2 | Empty | | Open empty playlist | Empty state text.

## I. Settings
- I01 P0 | Menu | | Open Settings | Rows: server, subscriptions, voice, language, history (as built).
- I02 P1 | Server | | Open, back | Works.
- I03 P1 | Profile | | Tap avatar | Picker sheet; current member highlighted; (do not switch).
- I04 P1 | History | | Open Watch history | List with progress bars; back.
- I05 P1 | Subscriptions | | Open | Alphabetical channels with counts; tap row opens channel.
- I06 P1 | Voice | | Open Vietnamese voice | Voice screen; back.
- I07 P1 | Language | | Switch to Vietnamese | All labels switch; tab bar too; persists after relaunch.
- I08 P1 | Language | | Switch back to English | Restored.
- I09 P1 | Feed mix | | Row/sliders present (do not change) | Renders.

## J. Back / navigation
- J01 P1 | Back | | Detail pages show arrow | Arrow returns to previous.
- J02 P1 | Depth | | Transitions | Forward slides from right, back reverses.
- J03 P2 | Tab back | | Non-first tab | N/A iOS no system back.

## K. Vietnamese
- K01 P1 | Layout | VI | Visit Home, Watch, Search, Playlists, Settings | No clipped/overlapping text; no English literals.
- K02 P1 | Units | VI | Counts | K/N/Tr/T suffix and relative dates in Vietnamese.
- K03 P1 | Sheet | VI | Settings sheet, save sheet, alerts | Fully translated, text fits.
- K04 P2 | Menu | VI | Overflow menu | "Lưu vào playlist", etc.

## L. Robustness
- L01 P0 | Crash | | Run all flows | No crash report.
- L02 P1 | Rotation | | N/A portrait only | -
- L03 P2 | Offline | | N/A | not run.
