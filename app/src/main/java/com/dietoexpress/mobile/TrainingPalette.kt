package com.dietoexpress.mobile

import androidx.compose.ui.graphics.Color

/**
 * Training area palette: graphite surfaces with a lime accent, kept separate from the green brand
 * colours of the rest of the app. First proposal; change the values here to retune every training
 * screen at once. Text on the accent must use [OnAccent] (dark) to keep contrast.
 */
internal object TrainingPalette {
    val Graphite = Color(0xFF1B2226)
    val GraphiteSurface = Color(0xFF242D32)
    val GraphiteInput = Color(0xFF2F3A40)
    val Border = Color(0xFF45535A)
    val TextOnDark = Color(0xFFEEF3F5)
    val MutedOnDark = Color(0xFFA3B1B8)
    val Accent = Color(0xFFC6F135)
    val OnAccent = Color(0xFF1B2226)
}
