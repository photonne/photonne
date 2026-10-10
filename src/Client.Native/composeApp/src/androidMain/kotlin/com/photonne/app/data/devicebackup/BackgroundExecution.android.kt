package com.photonne.app.data.devicebackup

/** Android keeps long passes alive with a foreground worker, not with a
 *  process-level grace period. */
actual suspend fun <T> withBackgroundExecution(block: suspend () -> T): T = block()
