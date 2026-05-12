package com.instafact.app.utils

import android.content.Context
import com.instafact.app.R

fun String?.displayStatus(context: Context): String {
    return when (this?.lowercase()) {
        "pending" -> context.getString(R.string.pending)
        "processing" -> context.getString(R.string.processing)
        "completed" -> context.getString(R.string.completed)
        "failed" -> context.getString(R.string.failed)
        else -> context.getString(R.string.unknown)
    }
}

fun String?.displayVerdict(context: Context): String {
    return this ?: context.getString(R.string.verdict_pending)
}

fun Int?.displayConfidence(context: Context): String {
    return this?.let { context.getString(R.string.confidence_format, it) }
        ?: context.getString(R.string.unknown)
}
