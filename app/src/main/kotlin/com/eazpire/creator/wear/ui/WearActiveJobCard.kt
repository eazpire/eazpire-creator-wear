package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import com.eazpire.creator.core.brand.BrandAssetSlots
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.eazpire.creator.wear.EazColors
import com.eazpire.creator.wear.R

@Composable
fun WearActiveJobCard(
    title: String,
    progress: Int,
    statusHint: String? = null,
    isError: Boolean = false,
    previewUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val logoScale = rememberWearPulseScale(minScale = 0.92f, maxScale = 1.08f)
    val logoAlpha = rememberWearPulseAlpha(minAlpha = 0.78f, maxAlpha = 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val preview = previewUrl?.takeIf { it.isNotBlank() }
        if (preview != null) {
            WearPulsingAsyncImage(
                imageUrl = preview,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .padding(bottom = 6.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            WatchBrandSlotImage(
                slot = BrandAssetSlots.CREATOR_WATCH_LOGO,
                fallbackResId = R.drawable.eazpire_creator_logo,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
                    .padding(bottom = 6.dp),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.caption2,
            color = EazColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        WearTetrisAssemblyLoader(
            modifier = Modifier
                .size(88.dp)
                .padding(vertical = 4.dp),
        )
        WearJobProgressBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(top = 12.dp),
        )
        if (!statusHint.isNullOrBlank()) {
            Text(
                text = statusHint,
                style = MaterialTheme.typography.caption2,
                color = if (isError) EazColors.Orange else EazColors.TextPrimary.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}
