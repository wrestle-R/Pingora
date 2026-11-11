/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

/** Main-thread ownership of verified imports; verification itself runs off-thread. */
class UpdateImportCoordinator<T>(private val discard: (T) -> Unit) {
    var candidate: T? = null
        private set
    var isVerifying = false
        private set
    private var generation = 0L

    fun begin(): Long? {
        if (isVerifying) return null
        candidate?.let(discard)
        candidate = null
        isVerifying = true
        return ++generation
    }

    /** False means the owning screen was destroyed while verification ran. */
    fun complete(ticket: Long, verified: T?): Boolean {
        if (ticket != generation || !isVerifying) {
            verified?.let(discard)
            return false
        }
        candidate = verified
        isVerifying = false
        return true
    }

    /** The OS now owns the read handoff; do not delete its file on screen teardown. */
    fun handedToInstaller() {
        candidate = null
    }

    fun dispose() {
        ++generation
        isVerifying = false
        candidate?.let(discard)
        candidate = null
    }
}
