package com.eazpire.creator.wear

import androidx.compose.ui.graphics.Color

/** Brand colors aligned with phone app [com.eazpire.creator.EazColors]. */
object EazColors {
    val Orange = Color(0xFFF97316)
    /** Wear OS quality guideline: app/tile root background is black. */
    val CreatorBg = Color(0xFF000000)
    val CreatorSurface = Color(0xFF1A1A1A)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.65f)
}
