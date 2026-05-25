package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Insets for round Wear displays. Full-width rows at the top/bottom of a circle get clipped;
 * keep content in the vertical center band and use [wearRoundContentPadding].
 *
 * See: https://developer.android.com/training/wearables/compose/lists
 */
object WearRoundInsets {
    /** ~12% horizontal margin — Google's recommended minimum on round screens. */
    val horizontal: Dp = 26.dp
    val vertical: Dp = 14.dp

    val contentPadding: PaddingValues = PaddingValues(
        horizontal = horizontal,
        vertical = vertical,
    )
}

fun Modifier.wearRoundSafePadding(): Modifier = composed {
    padding(horizontal = WearRoundInsets.horizontal, vertical = WearRoundInsets.vertical)
}
