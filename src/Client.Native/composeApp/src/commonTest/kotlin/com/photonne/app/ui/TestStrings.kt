package com.photonne.app.ui

/**
 * Makes [com.photonne.app.ui.util.getString] usable in this test run. On the
 * Android unit tests (plain JVM) Compose can't read its resources and the
 * texts become "<key>" or "<key>:<args>"; on the iOS simulator the real ones
 * stay. Call it before building a view model that formats messages.
 */
internal expect fun useTestStrings()
