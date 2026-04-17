package com.qoffee.core.common

import javax.inject.Inject

interface TimeProvider {
    fun nowMillis(): Long
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
