package com.mytube.app

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * A row that scrolls must leave the press somewhere to go.
 *
 * # Why this is a guard and not a comment
 *
 * `pressSquish` blooms a control **past its own bounds** — that is the whole
 * point of it, and it is what makes a 40dp circle feel springy while a
 * full-width row barely moves. A scroll container clips at its viewport, so a
 * control sitting flush against that edge comes back with a slice taken off it.
 *
 * It has been reported three times, on two different axes, and each time it was
 * the same fault in a row nobody had thought about:
 *
 * | | |
 * |---|---|
 * | `ChipRow` | a `LazyRow` exactly one chip tall — cut top and bottom |
 * | the up-next rail's filters | content from x=0 — cut left and right |
 * | the player's subtitle chips | content from x=0 — *"2 chips Off \| EN (auto) ở Player settings đang bị crop"* |
 *
 * Three is enough. The rule is now a build failure: a horizontally scrolling
 * row in `ui/` carries `padding` or `contentPadding`, whatever is inside it.
 * Deliberately blunter than "…if it holds something pressable" — a chip is
 * usually its own composable defined elsewhere in the file, so no window around
 * the row can see the press, and a guard that cannot see the thing it is about
 * is a guard that passes for the wrong reason. Padding on a skeleton row costs
 * nothing; a missing slice off a control costs a bug report.
 */
class ScrollRoomGuardTest {

    @Test
    fun everyScrollingRowLeavesRoomForABloom() {
        val offenders = mutableListOf<String>()

        uiSources().forEach { file ->
            val lines = file.readText().lines()
            lines.forEachIndexed { index, line ->
                if (!SCROLLER.containsMatchIn(line)) return@forEachIndexed
                // The modifier chain and the arguments that follow it, with
                // **comments dropped first**. A row's padding is written within
                // a line or two of the scroll and a `LazyRow`'s
                // `contentPadding` is an argument a little further down — but a
                // paragraph explaining why the padding is there pushes it out of
                // any fixed window. That is not hypothetical: the first version
                // of this guard failed on `ChipRow`, whose `contentPadding` sat
                // eleven lines below the `LazyRow(` under a comment saying
                // exactly what it was for.
                val chain = lines.drop(index)
                    .filter { !it.trim().startsWith("//") && !it.trim().startsWith("*") }
                    .take(WINDOW)
                    .joinToString("\n")
                if (!ROOM.containsMatchIn(chain) && !EXEMPT.containsMatchIn(chain)) {
                    offenders += "${file.name}:${index + 1}  ${line.trim()}"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("These rows scroll, so they clip, and a pressed")
                    appendLine("control against the edge loses a slice of itself:")
                    offenders.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Add `padding` inside the scroll — `PRESS_INSET` is")
                    appendLine("the distance the bloom travels — or say why not")
                    appendLine("with a `scroll-room: <reason>` comment beside it.")
                },
            )
        }
    }

    private fun uiSources(): List<File> =
        File("src/commonMain/kotlin/com/mytube/app/ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private companion object {
        /** A row that scrolls sideways, however it was asked for. */
        val SCROLLER = Regex("""\.horizontalScroll\(|^\s*LazyRow\(""")

        /**
         * What counts as room. `contentPadding` is a `LazyRow`'s way of saying
         * it; `padding` written *inside* the scroll is a plain `Row`'s.
         */
        val ROOM = Regex("""\.padding\(|contentPadding""")

        /** The way out, written where the exception is. */
        val EXEMPT = Regex("""scroll-room:""")

        /**
         * Lines of *code* after the scroll, comments already dropped. Long
         * enough to reach a `LazyRow`'s `contentPadding` argument and short
         * enough not to reach into whatever the row contains.
         */
        const val WINDOW = 6
    }
}
