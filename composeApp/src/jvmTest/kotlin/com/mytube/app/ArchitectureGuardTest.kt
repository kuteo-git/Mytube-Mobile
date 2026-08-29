package com.mytube.app

import kotlin.test.Test
import kotlin.test.fail
import java.io.File

/**
 * The dependency arrows, checked by a machine.
 *
 * The charter this app serves states the rule as a property of direction rather
 * than of folder count: *"Clean architecture is about the direction of
 * dependencies… `domain` imports no DB, HTTP or framework."* A rule written down
 * is a rule that gets forgotten once, which is why the web side of this project
 * grew `untranslated.guard.test.ts` and `player-seek.guard.test.ts` — crude
 * source scans that cannot forget.
 *
 * This is the same idea, and it is deliberately crude for the same reason. It
 * reads the source as text. It cannot be fooled by a clever indirection because
 * it is not trying to be clever; it asks one question — does this file name a
 * forbidden package — and a human reading the failure will see immediately
 * whether the arrow really points the wrong way.
 *
 * ## Why a source scan and not a Gradle module boundary
 *
 * Splitting `domain` into its own Gradle module would let the build system
 * enforce this outright, and that is the stronger answer. It is not the first
 * answer because module boundaries are expensive to move while a design is still
 * settling, and this app has three `expect/actual` seams whose shape is not yet
 * proven on both platforms. A test that fails loudly costs nothing to change;
 * a module graph does. When the seams stop moving, this test is the specification
 * for the modules that replace it.
 *
 * Lives in jvmTest, not commonTest: it reads files with java.io.File, which
 * common code cannot see. That is not a workaround — a guard about source
 * layout has no business being compiled for a phone.
 */
class ArchitectureGuardTest {

    /**
     * `domain` holds entities and plain types. Nothing that knows about a wire,
     * a screen or an operating system belongs in it.
     */
    @Test
    fun domainKnowsNothingAboutFrameworks() {
        assertNoImports(
            layer = "domain",
            forbidden = listOf(
                "io.ktor" to "HTTP",
                "kotlinx.serialization" to "a wire format",
                "androidx" to "Android",
                "android." to "Android",
                "platform." to "an Apple framework",
                "androidx.compose" to "Compose",
                "org.jetbrains.compose" to "Compose",
            ),
        )
    }

    /**
     * `application` holds use cases. It talks to repositories through interfaces
     * it declares itself, so it must not know which library fulfils them.
     *
     * Serialization is allowed nowhere but `infrastructure`: the moment a use
     * case carries `@Serializable`, the shape of the wire has reached into the
     * shape of the logic, and changing one means changing the other.
     */
    @Test
    fun applicationKnowsNothingAboutTransportOrScreens() {
        assertNoImports(
            layer = "application",
            forbidden = listOf(
                "io.ktor" to "HTTP",
                "kotlinx.serialization" to "a wire format",
                "androidx" to "Android",
                "android." to "Android",
                "platform." to "an Apple framework",
                "androidx.compose" to "Compose",
                "org.jetbrains.compose" to "Compose",
            ),
        )
    }

    /**
     * `ui` draws. It never fetches.
     *
     * The web side of this project states the same rule — *"`ui/` never calls
     * `fetch` directly"* — and the reason given there is what makes a second UI
     * possible at all: a screen that talks to a repository through the
     * application layer can be replaced wholesale without touching anything
     * behind it.
     */
    @Test
    fun uiNeverSpeaksHttp() {
        assertNoImports(
            layer = "ui",
            forbidden = listOf(
                "io.ktor" to "HTTP",
            ),
        )
    }

    private fun assertNoImports(layer: String, forbidden: List<Pair<String, String>>) {
        val root = sourceRoot().resolve("commonMain/kotlin/com/mytube/app/$layer")
        if (!root.exists()) return

        val problems = buildList {
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val import = line.trim().removePrefix("import ").takeIf {
                            line.trim().startsWith("import ")
                        } ?: return@forEachIndexed
                        forbidden.firstOrNull { (prefix, _) -> import.startsWith(prefix) }
                            ?.let { (_, what) ->
                                add("${file.name}:${index + 1} — $layer imports $what: ${import.trim()}")
                            }
                    }
                }
        }

        if (problems.isNotEmpty()) {
            fail(
                "The dependency arrows point outward in ${problems.size} place(s). " +
                    "Move the type behind an interface this layer owns.\n" +
                    problems.joinToString("\n"),
            )
        }
    }

    /**
     * Found by walking up from the working directory rather than hard-coded.
     *
     * Gradle does not promise which directory a test runs in, and a path written
     * as a literal is a test that passes on one machine and fails on another for
     * a reason that has nothing to do with the code.
     */
    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "composeApp/src")
            if (candidate.isDirectory) return candidate
            val here = File(dir, "src/commonMain")
            if (here.isDirectory) return File(dir, "src")
            dir = dir.parentFile
        }
        fail("could not find composeApp/src from ${File(".").absolutePath}")
    }
}
