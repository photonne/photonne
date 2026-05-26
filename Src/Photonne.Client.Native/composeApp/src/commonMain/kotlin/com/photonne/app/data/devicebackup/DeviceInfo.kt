package com.photonne.app.data.devicebackup

/**
 * Platform-specific name of the device the user is backing up FROM. Used as
 * a subfolder under `MobileBackup/` on the server so multi-device backups
 * stay visually grouped. The server further sanitizes whatever we send, so
 * raw platform strings (which may contain spaces or punctuation) are fine.
 */
expect fun currentDeviceName(): String
