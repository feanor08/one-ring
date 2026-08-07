package com.feanor08.onering.geometry

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The geometry engine must stay pure Kotlin: every angle/wedge/seek-track
 * computation is unit-testable on a plain JVM with no Compose or Android
 * runtime involved. This scans the actual source files so the rule can't
 * silently rot -- see docs/PLAN.md ("Layer 1" testing strategy).
 */
class ArchitectureBoundaryTest {

    @Test fun `geometry sources import neither Compose nor Android`() {
        val geometryDir = findGeometrySourceDir()
        val offenders = geometryDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter {
                        it.trimStart().startsWith("import androidx.compose") ||
                            it.trimStart().startsWith("import android.")
                    }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()

        assertTrue("Found forbidden imports in geometry/:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    private fun findGeometrySourceDir(): File {
        // Resolve relative to this test's own source location so the check works
        // regardless of the working directory the test runner uses.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "one-ring/src/main/kotlin/com/feanor08/onering/geometry")
            if (candidate.isDirectory) return candidate
            val altCandidate = File(dir, "src/main/kotlin/com/feanor08/onering/geometry")
            if (altCandidate.isDirectory) return altCandidate
            dir = dir.parentFile
        }
        throw IllegalStateException("Could not locate the geometry/ source directory from ${File(".").absolutePath}")
    }
}
