package com.mytube.app

import java.io.File
import kotlin.test.fail

/**
 * What the source-scanning guards read, and how they tell code from prose.
 *
 * One file because five guards asked the same two questions with their own
 * copies, and the copies had drifted: two dropped the line that opens a block
 * comment and two did not. The charter's rule is that guards count **lines of
 * code**, and a rule is only one rule while it has one definition.
 */

/**
 * `composeApp/src`, found by walking up from the working directory rather than
 * hard-coded.
 *
 * Gradle does not promise which directory a test runs in, and a path written
 * as a literal is a test that passes on one machine and fails on another for
 * a reason that has nothing to do with the code.
 */
internal fun sourceRoot(): File {
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

/** Every Kotlin file under `ui`. */
internal fun uiSources(): List<File> =
    sourceRoot().resolve("commonMain/kotlin/com/mytube/app/ui")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

/** A line of prose — a comment or a KDoc line — rather than one of code. */
internal fun isComment(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
}
