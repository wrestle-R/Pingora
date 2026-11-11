/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class UpdateImportCoordinatorTest {
    @Test fun failedReplacementCannotInstallPreviousCandidate() {
        val directory = Files.createTempDirectory("pingora-update-test").toFile()
        try {
            val old = directory.resolve("previous.apk").apply { writeText("verified fixture") }
            val coordinator = UpdateImportCoordinator<java.io.File> { it.delete() }
            assertTrue(coordinator.complete(coordinator.begin()!!, old))
            val next = coordinator.begin()!!
            assertFalse(old.exists())
            assertNull(coordinator.candidate)
            assertTrue(coordinator.complete(next, null))
            assertNull(coordinator.candidate)
            assertFalse(coordinator.isVerifying)
        } finally { directory.deleteRecursively() }
    }

    @Test fun overlappingImportsAreRefusedAndLateResultsAreDiscarded() {
        val discarded = mutableListOf<String>()
        val coordinator = UpdateImportCoordinator<String> { discarded.add(it) }
        val first = coordinator.begin()!!
        assertNull(coordinator.begin())
        coordinator.dispose()
        val current = coordinator.begin()!!
        assertFalse(coordinator.complete(first, "stale APK"))
        assertTrue(coordinator.isVerifying)
        assertEquals(listOf("stale APK"), discarded)
        assertTrue(coordinator.complete(current, "current APK"))
        assertEquals("current APK", coordinator.candidate)
    }

    @Test fun screenTeardownPreservesOnlyFilesHandedToTheInstaller() {
        val directory = Files.createTempDirectory("pingora-update-test").toFile()
        try {
            val handedOff = directory.resolve("installer.apk").apply { writeText("verified fixture") }
            val pending = directory.resolve("pending.apk").apply { writeText("verified fixture") }
            val coordinator = UpdateImportCoordinator<java.io.File> { it.delete() }
            coordinator.complete(coordinator.begin()!!, handedOff)
            coordinator.handedToInstaller()
            coordinator.dispose()
            assertTrue(handedOff.exists())
            coordinator.complete(coordinator.begin()!!, pending)
            coordinator.dispose()
            assertFalse(pending.exists())
            assertTrue(handedOff.exists())
        } finally { directory.deleteRecursively() }
    }
}
