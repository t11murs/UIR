package com.example.uir_android.core.common

import java.util.concurrent.atomic.AtomicBoolean

/** Allows only one in-flight operation until the current owner releases the gate. */
class SingleFlightGate {
    private val isAcquired = AtomicBoolean(false)

    fun tryAcquire(): Boolean = isAcquired.compareAndSet(false, true)

    fun release() {
        isAcquired.set(false)
    }
}
