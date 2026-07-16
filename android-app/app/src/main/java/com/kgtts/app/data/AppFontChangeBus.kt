package com.lhtstudio.kigtts.app.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal object AppFontChangeBus {
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = mutableChanges.asSharedFlow()

    fun notifyChanged() {
        mutableChanges.tryEmit(Unit)
    }
}
