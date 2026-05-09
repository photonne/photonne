package com.photonne.app.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.core.context.GlobalContext

actual fun openExternalUrl(url: String) {
    val context: Context? = runCatching { GlobalContext.get().get<Context>() }.getOrNull()
    val target = context ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { target.startActivity(intent) }
}
