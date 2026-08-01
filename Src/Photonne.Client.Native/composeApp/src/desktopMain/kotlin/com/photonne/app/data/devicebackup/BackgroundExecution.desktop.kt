package com.photonne.app.data.devicebackup

/** Desktop processes aren't suspended by the OS. */
actual suspend fun <T> withBackgroundExecution(block: suspend () -> T): T = block()
