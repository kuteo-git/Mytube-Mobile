package com.mytube.app

import kotlin.test.Test
import kotlin.test.fail

/**
 * Every gesture a finger can make has a key a remote can press, or says why not.
 *
 * # Why this exists
 *
 * The television reaches every control through focus, and for anything written
 * with `clickable` that comes free: `clickable` is focusable and answers OK as a
 * click, and [PressGuardTest] already holds every `clickable` to the design
 * system's press. What it cannot see is a control that never used `clickable` —
 * a tap read off `pointerInput`, a value dragged across a track. Those answer a
 * finger and nothing else, and on a television they are a control nobody can
 * reach. The level sliders in Settings were exactly that until this guard was
 * written: a stop focus could not land on, on a page a remote has to use.
 *
 * A source scan, crude on purpose, the same as the guards beside it. It asks
 * one question — does this pointer gesture have a key handler or a focus target
 * in its chain — and the way out is a reason written where the exception is:
 * `focus-guard: <reason>`. Most of the exceptions are true and short: the
 * phone's player is not the television's, a pinch has no key, the drag to the
 * miniplayer exists only where there is a miniplayer.
 */
class FocusGuardTest {

    @Test
    fun everyPointerGestureHasAKeyOrAReason() {
        val offenders = mutableListOf<String>()

        uiSources().forEach { file ->
            val lines = file.readText().lines()
            lines.forEachIndexed { index, line ->
                if (isComment(line)) return@forEachIndexed
                if (!GESTURE.containsMatchIn(line)) return@forEachIndexed
                // Lines of code for the answer, raw lines for the reason — the
                // two halves [PressGuardTest] learned to read apart: a paragraph
                // explaining a gesture must not push its key handler out of the
                // window, and the exemption lives in prose by design.
                val code = lines.withIndex().filterNot { (_, l) -> isComment(l) }
                val here = code.indexOfFirst { it.index == index }
                val chain = code
                    .subList((here - WINDOW).coerceAtLeast(0), (here + WINDOW).coerceAtMost(code.lastIndex) + 1)
                    .joinToString("\n") { it.value }
                val prose = lines
                    .subList((index - WINDOW * 2).coerceAtLeast(0), (index + WINDOW * 2).coerceAtMost(lines.lastIndex) + 1)
                    .joinToString("\n")
                if (!KEYED.containsMatchIn(chain) && !EXEMPT.containsMatchIn(prose)) {
                    offenders += "${file.name}:${index + 1}  ${line.trim()}"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("These answer a finger and nothing a remote can press:")
                    offenders.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Give the node a key handler (`onKeyEvent`) and a focus")
                    appendLine("target (`focusable`), or write `focus-guard: <the reason>`")
                    appendLine("beside it when the gesture has no place on a television.")
                },
            )
        }
    }

    private companion object {
        /**
         * The pointer gestures that act: a tap, a drag, a pinch.
         *
         * Calls only — followed by a bracket — so an import, or a
         * `return@detectHorizontalDragGestures` inside one, is not a second
         * gesture.
         */
        val GESTURE = Regex(
            """\b(detectTapGestures|detectDragGestures|detectHorizontalDragGestures|""" +
                """detectVerticalDragGestures|detectTransformGestures)\s*[({]""",
        )

        /** What counts as a remote being able to do the same thing. */
        val KEYED = Regex("""onKeyEvent|onPreviewKeyEvent""")

        val EXEMPT = Regex("""focus-guard: """)

        /** Lines of code either side, as wide as the press guard's for its reason. */
        const val WINDOW = 34
    }
}
