package com.example.nexgointegration.ui.payment

import android.view.View


object PaymentTheme {

    // Fonds : un par ambiance
    val BG_WAITING = 0xFF0B1F3A.toInt()
    val BG_PROCESSING = 0xFF1E2A78.toInt()
    val BG_SUCCESS = 0xFF0E5A3A.toInt()
    val BG_FAILURE = 0xFF6B1D2A.toInt()

    // Éléments
    val ACCENT = 0xFF2DD4FF.toInt()
    val CARD = 0xFFF8FAFC.toInt()
    val CHIP = 0xFFF5B942.toInt()
    val SUCCESS = 0xFF22C55E.toInt()
    val FAILURE = 0xFFEF4444.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val TEXT_SOFT = 0xB3FFFFFF.toInt()

    const val ANIMATION_BG_MS = 450L
}

internal fun View.dp(value: Float): Float = value * resources.displayMetrics.density
internal fun View.dpInt(value: Float): Int = dp(value).toInt()