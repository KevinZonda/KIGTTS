package com.lhtstudio.kigtts.app.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallbackJobTrackerTest {
    @Test
    fun awaitIdleWaitsForAnAlreadyRegisteredCallback(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tracker = CallbackJobTracker(scope)
        val release = CompletableDeferred<Unit>()
        var delivered = false
        try {
            tracker.launch {
                release.await()
                delivered = true
            }
            val waiting = async { tracker.awaitIdle() }
            yield()

            assertFalse(waiting.isCompleted)
            release.complete(Unit)
            waiting.await()
            assertTrue(delivered)
        } finally {
            scope.cancel()
        }
    }
}
