package com.subtracks

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** A message to resolve later against the current resources: a string, or a plural when [pluralsId] is set. */
data class UiMessage(
    @param:StringRes val resId: Int = 0,
    val args: List<Any> = emptyList(),
    @param:PluralsRes val pluralsId: Int = 0,
    val quantity: Int = 0,
) {
    fun resolve(resources: Resources): String =
        if (pluralsId != 0) {
            resources.getQuantityString(pluralsId, quantity, *args.toTypedArray())
        } else {
            resources.getString(resId, *args.toTypedArray())
        }

    fun resolve(context: Context): String = resolve(context.resources)
}

class UiException(
    val uiMessage: UiMessage,
    message: String = "",
) : Exception(message)
