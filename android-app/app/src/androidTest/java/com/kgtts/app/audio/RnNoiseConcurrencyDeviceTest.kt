package com.lhtstudio.kigtts.app.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RnNoiseConcurrencyDeviceTest {
    @Test
    fun separateProcessorsCanProcessConcurrentlyWithoutCorruptingSharedFftState() {
        val workerCount = 4
        val executor = Executors.newFixedThreadPool(workerCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workerCount)
        val failure = AtomicReference<Throwable?>(null)

        repeat(workerCount) { worker ->
            executor.execute {
                val processor = RnNoiseProcessor()
                try {
                    start.await()
                    repeat(240) { frame ->
                        val samples = FloatArray(320) { index ->
                            val phase = (frame * samplesPerFrame + index) * (0.006 + worker * 0.001)
                            (sin(phase) * 0.12).toFloat()
                        }
                        processor.processInPlace(samples)
                        check(samples.all(Float::isFinite))
                    }
                } catch (error: Throwable) {
                    failure.compareAndSet(null, error)
                } finally {
                    processor.release()
                    done.countDown()
                }
            }
        }

        start.countDown()
        assertTrue("RNNoise workers timed out", done.await(30, TimeUnit.SECONDS))
        executor.shutdownNow()
        failure.get()?.let { throw AssertionError("RNNoise concurrent processing failed", it) }
    }

    private companion object {
        const val samplesPerFrame = 320
    }
}
