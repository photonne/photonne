package com.photonne.app.data.devicebackup

/**
 * Runs [block] while asking the OS not to suspend the process yet.
 *
 * Only iOS has something to do here: an in-process upload started from the
 * screen gets frozen the moment the user switches apps, so a
 * `beginBackgroundTask` buys the batch time to finish (or at least to stop
 * cleanly). Android routes these passes through a foreground worker instead,
 * and desktop is never suspended, so both just run the block.
 */
expect suspend fun <T> withBackgroundExecution(block: suspend () -> T): T
