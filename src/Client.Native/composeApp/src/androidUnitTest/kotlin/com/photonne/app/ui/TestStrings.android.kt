package com.photonne.app.ui

import com.photonne.app.ui.util.UiStrings
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

private object KeyStrings : UiStrings.Resolver {
    override suspend fun string(resource: StringResource, args: Array<out Any>) =
        format(resource.key, args)

    override suspend fun plural(resource: PluralStringResource, quantity: Int, args: Array<out Any>) =
        format(resource.key, args)

    private fun format(key: String, args: Array<out Any>) =
        if (args.isEmpty()) key else "$key:${args.joinToString(",")}"
}

internal actual fun useTestStrings() {
    UiStrings.resolver = KeyStrings
}
