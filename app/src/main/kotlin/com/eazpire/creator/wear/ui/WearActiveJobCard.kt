package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            val preview = previewUrl?.takeIf { it.isNotBlank() }
            if (preview != null) {
                WearPulsingAsyncImage(
                    imageUrl = preview,
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.eazpire_creator_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.caption2,
                color = EazColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            )
            WearTetrisAssemblyLoader(
                modifier = Modifier
                    .size(72.dp)
                    .padding(vertical = 2.dp),
            )
            WearJobProgressBar(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(top = 8.dp),
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
}
