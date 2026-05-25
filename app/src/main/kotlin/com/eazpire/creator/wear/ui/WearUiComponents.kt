package com.eazpire.creator.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.eazpire.creator.wear.EazColors

@Composable
fun WearRoundIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier.size(52.dp),
    selected: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) EazColors.Orange.copy(alpha = 0.35f) else EazColors.Orange,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
fun WearPulsingGenerateButton(
    onClick: () -> Unit,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "genPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val alpha by pulse.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (enabled) alpha else 0.5f)
            .clip(CircleShape)
            .background(EazColors.Orange)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button,
            color = EazColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun WearSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onUploadClick: (() -> Unit)? = null,
) {
    val padH = if (compact) 6.dp else 10.dp
    val padV = if (compact) 2.dp else 6.dp
    val micSize = if (compact) 28.dp else 40.dp
    val iconSp = if (compact) 11.sp else 14.sp
    val fieldSp = if (compact) 9.sp else 11.sp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        Text("⌕", color = EazColors.Orange, fontSize = iconSp)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.caption2.copy(
                color = EazColors.TextPrimary,
                fontSize = fieldSp,
            ),
            singleLine = true,
            cursorBrush = SolidColor(EazColors.Orange),
            decorationBox = { inner ->
                Box {
                    if (query.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.caption2,
                            color = EazColors.TextPrimary.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        if (onUploadClick != null) {
            WearRoundIconButton(
                onClick = onUploadClick,
                contentDescription = "Upload design",
                modifier = Modifier.size(micSize),
                icon = { Text("📤", fontSize = if (compact) 11.sp else 14.sp) },
            )
        }
        WearRoundIconButton(
            onClick = onVoiceClick,
            contentDescription = "Voice search",
            modifier = Modifier.size(micSize),
            icon = { Text("🎤", fontSize = if (compact) 11.sp else 14.sp) },
        )
    }
}
