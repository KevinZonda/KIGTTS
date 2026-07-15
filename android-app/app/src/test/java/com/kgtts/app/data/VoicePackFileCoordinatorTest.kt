package com.lhtstudio.kigtts.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VoicePackFileCoordinatorTest {
    @Test
    fun concurrentAtomicWritesKeepACompleteTargetFile() {
        val directory = Files.createTempDirectory("voice-meta-write-test").toFile()
        val target = File(directory, "voicepack.json")
        val contents = (0 until 32).map { index ->
            """{"name":"voice-$index","order":$index}"""
        }
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)

        try {
            val writes = contents.map { content ->
                executor.submit {
                    start.await()
                    VoicePackFileCoordinator.writeTextAtomically(target, content)
                }
            }
            start.countDown()
            writes.forEach { it.get(10, TimeUnit.SECONDS) }

            assertTrue(target.isFile)
            assertTrue(target.readText(Charsets.UTF_8) in contents)
            assertFalse(
                directory.listFiles().orEmpty().any { file ->
                    file.name.startsWith(".voicepack.json.") && file.name.endsWith(".tmp")
                }
            )
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun canonicalDirectoryLockSerializesEquivalentPaths() {
        val directory = Files.createTempDirectory("voice-meta-lock-test").toFile()
        val equivalentDirectory = File(directory, ".")
        val executor = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val activeCounts = mutableListOf<Int>()
        var active = 0

        try {
            val tasks = (0 until 24).map { index ->
                executor.submit {
                    start.await()
                    val lockPath = if (index % 2 == 0) directory else equivalentDirectory
                    VoicePackFileCoordinator.withDirectoryLock(lockPath) {
                        active += 1
                        activeCounts += active
                        Thread.sleep(2)
                        active -= 1
                    }
                }
            }
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }

            assertTrue(activeCounts.isNotEmpty())
            assertTrue(activeCounts.all { it == 1 })
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }
}
