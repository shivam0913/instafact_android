package com.instafact.app.utils

import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.load
import com.instafact.app.R

fun ImageView.loadThumbnail(thumbnailUrl: String?) {
    val fallback = ContextCompat.getDrawable(context, R.drawable.bg_media_placeholder)
    load(thumbnailUrl) {
        crossfade(true)
        placeholder(fallback)
        error(fallback)
        fallback(fallback)
    }
}
