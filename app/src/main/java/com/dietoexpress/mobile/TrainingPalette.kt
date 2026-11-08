package com.dietoexpress.mobile

import androidx.compose.ui.graphics.Color

/**
 * Training screens use the SAME colours as the rest of the app (Forest, Canvas, Ink, Muted, Line in
 * MainActivity) so they do not look like an add-on. Kept in one place: if the main palette changes,
 * update these values with it.
 */
internal object TrainingPalette {
    val Surface = Color(0xFFFFFFFF)
    val Input = Color(0xFFF5F8F6)      // Canvas
    val Border = Color(0xFFE2EAE5)     // Line
    val Text = Color(0xFF172923)       // Ink
    val Muted = Color(0xFF687970)
    val Accent = Color(0xFF176B52)     // Forest
    val OnAccent = Color(0xFFFFFFFF)
}
