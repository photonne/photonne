package com.photonne.app.data.models

/**
 * What a streamed download says before its first byte: the platform needs the
 * MIME type and suggested filename to open the file it writes into, and
 * [contentLength] (null when the server doesn't send `Content-Length`) drives
 * the progress pill.
 */
data class DownloadHead(
    val mimeType: String,
    val suggestedFileName: String,
    val contentLength: Long?
)
