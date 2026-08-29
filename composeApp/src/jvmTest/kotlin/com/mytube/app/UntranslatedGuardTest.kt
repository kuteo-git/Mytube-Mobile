package com.mytube.app

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Copy that never reached the dictionary.
 *
 * ## Why a source scan and not a type
 *
 * `Strings` being an interface already makes a *missing translation*
 * impossible: add a property and every language stops compiling until it is
 * supplied. What that cannot see is the string which was never put on the
 * interface at all — a literal sitting in a composable, rendering in English
 * while nothing anywhere reports it.
 *
 * The web side of this system learned exactly this, and wrote down what it cost:
 * its own guard once passed while "the tab bar, the chips, Subscribe, Share,
 * Sort by, and every settings heading were still English, and the person who
 * found out was the one using it." Its conclusion is the reason this file
 * exists — *"a guard that passes on the failure it exists to catch is worse than
 * none, because it is also believed."*
 *
 * ## What counts as copy
 *
 * Any quoted literal passed to `Text(...)` or a `contentDescription`. That is
 * narrower than the web app's rule and it fits Compose: there is no JSX text
 * here, and everything a person reads goes through one of those two.
 *
 * Exceptions are enumerated rather than guessed at, because guessing at the
 * shape of copy is what let the other guard through.
 */
class UntranslatedGuardTest {

    /**
     * Literals that are not copy, each with a reason.
     *
     * Kept short deliberately. A long list here means the rule is being worked
     * around rather than followed.
     */
    private val allowed = setOf(
        // An example address in the server field. It reads identically in both
        // languages, and translating it would mean inventing a fake IP per
        // language for no reader's benefit.
        "10.0.0.5:8180",
    )

    @Test
    fun noScreenShowsAStringThatIsNotInTheDictionary() {
        val ui = sourceRoot().resolve("commonMain/kotlin/com/mytube/app/ui")
        if (!ui.isDirectory) return

        val problems = buildList {
            ui.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                // The dictionary itself is where the strings live, and previews
                // are sample data rather than anything a person meets.
                .filterNot { it.path.contains("/i18n/") }
                .forEach { file ->
                    val lines = file.readLines()
                    var inPreviewBlock = false
                    lines.forEachIndexed { index, raw ->
                        val line = raw.trim()
                        if (line.startsWith("@Preview")) inPreviewBlock = true
                        if (inPreviewBlock && line == "}") inPreviewBlock = false
                        if (inPreviewBlock || line.startsWith("//") || line.startsWith("*")) {
                            return@forEachIndexed
                        }

                        COPY_SITE.findAll(line).forEach { match ->
                            val literal = match.groupValues[2]
                            if (literal.isBlank() || literal in allowed) return@forEach
                            add("${file.name}:${index + 1} — untranslated: \"$literal\"")
                        }
                    }
                }
        }

        if (problems.isNotEmpty()) {
            fail(
                "${problems.size} string(s) go straight to the screen without passing " +
                    "through Strings. Add a property to the interface — both languages " +
                    "will then refuse to compile until they have it.\n" +
                    problems.joinToString("\n"),
            )
        }
    }

    private companion object {
        /**
         * `Text("…")` and `contentDescription = "…"`.
         *
         * Only double-quoted literals: a string built from an expression —
         * `"${'$'}{channel.name} · ${'$'}{formatViews(…)}"` — is composed of values that
         * came from elsewhere, and the words inside it are already in the
         * dictionary or are data.
         */
        val COPY_SITE = Regex("""(Text\(|contentDescription\s*=\s*)"([^"$]*)"""")
    }

    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "composeApp/src")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        fail("could not find composeApp/src from ${File(".").absolutePath}")
    }
}
