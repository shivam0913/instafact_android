package com.instafact.app.utils

import android.view.View
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun AppCompatActivity.configureSystemBars(
    @ColorRes statusBarColorRes: Int,
    lightStatusBar: Boolean,
    @ColorRes navigationBarColorRes: Int = statusBarColorRes,
) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = ContextCompat.getColor(this, statusBarColorRes)
    window.navigationBarColor = ContextCompat.getColor(this, navigationBarColorRes)
    WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = lightStatusBar
    WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightNavigationBars = lightStatusBar
}

fun View.applySystemBarInsets(
    applyTop: Boolean = false,
    applyBottom: Boolean = false,
    applyLeft: Boolean = false,
    applyRight: Boolean = false,
) {
    val initialPaddingLeft = paddingLeft
    val initialPaddingTop = paddingTop
    val initialPaddingRight = paddingRight
    val initialPaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemInsets: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = initialPaddingLeft + if (applyLeft) systemInsets.left else 0,
            top = initialPaddingTop + if (applyTop) systemInsets.top else 0,
            right = initialPaddingRight + if (applyRight) systemInsets.right else 0,
            bottom = initialPaddingBottom + if (applyBottom) systemInsets.bottom else 0,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
