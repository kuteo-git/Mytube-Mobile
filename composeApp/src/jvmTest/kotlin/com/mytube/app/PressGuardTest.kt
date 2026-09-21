package com.mytube.app

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every control answers a finger the same way, checked by a machine.
 *
 * # Why this exists
 *
 * The press animation lives in one file. That is the whole design: change the
 * bloom, the wobble or the lift in `GlassPress.kt` and every control in the app
 * changes with it. It only holds while every control actually goes through it —
 * and the way it stops holding is not a decision anybody makes, it is a
 * `clickable` written in a hurry on the next screen.
 *
 * That has already happened here. Like and dislike sat beside Share and Save
 * for months with no movement at all, because they are two halves of a joined
 * pill rather than a pill, so nobody thought to give them one. Seven controls
 * over the video were the same. The charter's own words for this shape of
 * fault: *"written apart, the next control written gets the material and no
 * press — which is exactly how fifteen of them ended up silent."*
 *
 * So the rule is a test rather than a habit, the same as
 * [ArchitectureGuardTest] and the untranslated-copy guard beside it. It is a
 * source scan and it is crude on purpose: it asks one question — does this file
 * call `clickable` without asking the design system for a press — and a human
 * reading the failure can see in one line whether the answer is fair.
 */
class PressGuardTest {

    /**
     * A file that calls `clickable` must also name the press.
     *
     * Either through [com.mytube.app.ui.shell.pressable], which folds the two
     * together, or by holding a `GlassPress` of its own — which a handful of
     * controls do because they need the same object for their material as well
     * as their movement.
     */
    @Test
    fun everyClickableAnswersWithTheDesignSystemsPress() {
        val offenders = mutableListOf<String>()

        uiSources().filter { it.name !in PRESS_OWNERS }.forEach { file ->
            val lines = file.readText().lines()
            lines.forEachIndexed { index, line ->
                if (!CLICKABLE.containsMatchIn(line)) return@forEachIndexed
                // The window is the modifier chain around it, not the file.
                //
                // A file is too coarse and this guard has already been caught by
                // that: `WatchActions.kt` holds Share and Save, which have the
                // press, beside one half of the like pill, which did not — and a
                // file-level check saw the first and passed the second. Fourteen
                // lines is the longest chain in this app plus room.
                val from = (index - WINDOW).coerceAtLeast(0)
                val to = (index + WINDOW).coerceAtMost(lines.lastIndex)
                val chain = lines.subList(from, to + 1).joinToString("\n")
                if (!PRESS.containsMatchIn(chain) && !EXEMPT.containsMatchIn(chain)) {
                    offenders += "${file.name}:${index + 1}  ${line.trim()}"
                }
            }
        }

        if (offenders.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("These draw a control that does not move when it is pressed:")
                    offenders.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Use `Modifier.pressable(onClick = …)`, or one of the")
                    appendLine("`pressableGlass*` modifiers when the control has a pane of its")
                    appendLine("own. If this really is a surface nobody presses — a scrim, the")
                    appendLine("floor of a bar — write `press-guard: <the reason>` beside it.")
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
        val CLICKABLE = Regex("""\bclickable\(""")

        /**
         * How far either side of a `clickable` its modifier chain can reach.
         *
         * Wide, because the chains in this app are mostly comments: the press on
         * `GlassButton` sits twenty-seven lines above the `clickable` it belongs
         * to, with the argument for each link written between them. A window
         * that fits the code and not the reasons reports the files that explain
         * themselves best.
         */
        const val WINDOW = 34
        val PRESS = Regex("""rememberGlassPress|\.pressable\(|pressableGlass|pressableLiquid""")

        /**
         * The way out, written where the exception is rather than in a list
         * somebody has to go and find.
         *
         * A file-wide allowance is too blunt and this guard has already proved
         * it: `WatchActions.kt` holds Share and Save, which have the press,
         * beside one half of the like pill, which did not, and a file-level
         * check saw the first and excused the second.
         */
        val EXEMPT = Regex("""press-guard: """)

        /** The file the press is defined in, which cannot ask itself for one. */
        val PRESS_OWNERS = setOf("GlassPress.kt")
    }
}
