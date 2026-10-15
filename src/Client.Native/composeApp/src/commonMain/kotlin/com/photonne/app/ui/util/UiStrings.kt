package com.photonne.app.ui.util

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as composeGetPluralString
import org.jetbrains.compose.resources.getString as composeGetString

/**
 * Texts built outside composition (view models, snackbar messages). Same
 * signatures as Compose's getString/getPluralString, behind a seam: in the
 * Android unit tests (plain JVM, no device) Compose can't resolve its
 * resources, so those tests swap [resolver] for a stub.
 */
internal object UiStrings {
    interface Resolver {
        suspend fun string(resource: StringResource, args: Array<out Any>): String
        suspend fun plural(resource: PluralStringResource, quantity: Int, args: Array<out Any>): String
    }

    object ComposeResources : Resolver {
        override suspend fun string(resource: StringResource, args: Array<out Any>) =
            composeGetString(resource, *args)

        override suspend fun plural(resource: PluralStringResource, quantity: Int, args: Array<out Any>) =
            composeGetPluralString(resource, quantity, *args)
    }

    var resolver: Resolver = ComposeResources
}

internal suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
    UiStrings.resolver.string(resource, formatArgs)

internal suspend fun getPluralString(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String =
    UiStrings.resolver.plural(resource, quantity, formatArgs)
