package com.example.terminalmodule.model

import android.graphics.Bitmap

enum class FontSize { SMALL, NORMAL, LARGE }
enum class Align { LEFT, CENTER, RIGHT }

sealed interface PrintLine {
    data class Text(
        val value: String,
        val bold: Boolean = false,
        val size: FontSize = FontSize.NORMAL,
        val align: Align = Align.LEFT,
    ) : PrintLine

    data class Image(val bitmap: Bitmap, val align: Align = Align.CENTER) : PrintLine
    data class Feed(val lines: Int = 1) : PrintLine
}
