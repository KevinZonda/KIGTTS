package com.lhtstudio.kigtts.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class CallbackJobTracker(
    private val scope: CoroutineScope
) {
    private val lock = Any()
    private val jobs = mutableSetOf<Job>()

    fun launch(block: suspend () -> Unit) {
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                synchronized(lock) { jobs.remove(job) }
            }
        }
        synchronized(lock) { jobs.add(job) }
        job.start()
    }

    suspend fun awaitIdle() {
        while (true) {
            val active = synchronized(lock) { jobs.filter(Job::isActive) }
            if (active.isEmpty()) return
            active.forEach { job ->
                runCatching { job.join() }
            }
        }
    }
}
