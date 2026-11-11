/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import java.io.File

/** Canonical containment applies to every listing and read, not just the initial grant. */
object SharedStorageBoundary {
    fun resolve(base: File, candidate: File): File {
        val root=base.canonicalFile
        val file=candidate.canonicalFile
        require(file==root || file.path.startsWith(root.path+File.separator))
        val relative=file.relativeTo(root).path.replace(File.separatorChar,'/')
        require(relative!="Android/data" && !relative.startsWith("Android/data/") && relative!="Android/obb" && !relative.startsWith("Android/obb/"))
        return file
    }
}
