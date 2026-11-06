package com.mytube.app

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The skeleton's pulse runs only while a skeleton is on screen.
 *
 * `skeletonShade()` is an infinite transition read in composition, so whatever
 * calls it recomposes on every frame for as long as it stays composed. The up
 * next rail called it at its top for the one bar it draws while loading, and
 * the pulse then ran under every video for the life of the watch page: the
 * rail and all its rows recomposed and redrew sixty times a second, measured
 * on the iPhone as most of the app's frame work with the picture playing.
 *
 * So the call belongs inside the skeleton it colours: a function whose name
 * says it is one, composed only while the thing it stands in for is missing.
 */
class SkeletonGuardTest {

    @Test
    fun theSkeletonPulseIsOnlyAskedForInsideASkeleton() {
        val offenders = uiSources().flatMap { file ->
            val lines = file.readLines()
            lines.withIndex()
                .filter { (_, line) -> !isComment(line) && CALL.containsMatchIn(line) && !DECLARATION.containsMatchIn(line) }
                .mapNotNull { (index, _) ->
                    val owner = (index downTo 0).firstNotNullOfOrNull { FUNCTION.find(lines[it])?.groupValues?.get(1) }
                    if (owner != null && "Skeleton" in owner) null else "${file.name}:${index + 1} in ${owner ?: "?"}"
                }
        }
        assertTrue(
            offenders.isEmpty(),
            "skeletonShade() outside a *Skeleton composable, so its pulse outlives the loading state:\n" +
                offenders.joinToString("\n"),
        )
    }

    private companion object {
        val CALL = Regex("""\bskeletonShade\(\)""")
        val DECLARATION = Regex("""fun skeletonShade\(""")
        val FUNCTION = Regex("""\bfun\s+(\w+)\s*\(""")
    }
}
