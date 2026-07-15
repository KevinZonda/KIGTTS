package com.lhtstudio.kigtts.app.data

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object VoicePackFileCoordinator {
    private val directoryLocks = ConcurrentHashMap<String, Any>()

    fun <T> withDirectoryLock(directory: File, block: () -> T): T {
        val key = runCatching { directory.canonicalPath }
            .getOrElse { directory.absolutePath }
        val lock = directoryLocks.computeIfAbsent(key) { Any() }
        return synchronized(lock, block)
    }

    fun writeTextAtomically(file: File, content: String) {
        val parent = file.parentFile ?: throw IOException("元数据文件缺少父目录")
        withDirectoryLock(parent) {
            writeTextAtomicallyLocked(file, parent, content)
        }
    }

    private fun writeTextAtomicallyLocked(file: File, parent: File, content: String) {
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("无法创建语音包元数据目录")
        }

        val temporary = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }
}
