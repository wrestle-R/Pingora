package org.kde.kdeconnect.pingora

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SharedStorageBoundaryTest {
    @Test fun sharedStorageContainsReadsAndRejectsPrivateDirectoriesAndSymlinkEscapes() {
        val temp=Files.createTempDirectory("pingora-storage").toFile()
        try {
            val root=File(temp,"phone").apply {mkdir()}
            val picture=File(root,"DCIM/photo.jpg").apply {parentFile.mkdirs();writeText("synthetic")}
            assertEquals(picture.canonicalFile, SharedStorageBoundary.resolve(root,picture))
            for(path in listOf(File(temp,"phone-other/file"),File(root,"../outside"),File(root,"Android/data/app/file"),File(root,"Android/obb/app/file"))) {
                assertThrows(IllegalArgumentException::class.java) {SharedStorageBoundary.resolve(root,path)}
            }
            val outside=File(temp,"outside").apply {mkdir()}
            Files.createSymbolicLink(File(root,"escape").toPath(),outside.toPath())
            assertThrows(IllegalArgumentException::class.java) {SharedStorageBoundary.resolve(root,File(root,"escape/file"))}
        } finally {temp.deleteRecursively()}
    }
}
