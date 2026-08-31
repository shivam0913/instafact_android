package com.instafact.app.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.R

/**
 * Shown when someone shares a link we cannot fact-check yet.
 *
 * A dialog rather than the old toast: this is a dead end for the thing the user just
 * tried to do, and a toast that vanishes in two seconds reads as the app ignoring them.
 * The copy declines, explains the current limit, and promises more - without apologising
 * at length.
 */
object UnsupportedPlatformDialog {

    fun show(context: Context, onDismiss: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Instafact_Dialog)
            .setTitle(R.string.unsupported_platform_title)
            .setMessage(R.string.unsupported_platform_body)
            .setPositiveButton(R.string.unsupported_platform_action) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
            .apply {
                window?.setBackgroundDrawable(
                    ContextCompat.getDrawable(context, R.drawable.bg_dialog_surface),
                )
            }
    }
}
