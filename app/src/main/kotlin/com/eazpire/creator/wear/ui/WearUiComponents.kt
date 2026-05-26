package com.eazpire.creator.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
fun WearBackChevronButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(EazColors.CreatorSurface.copy(alpha = 0.92f))
            .border(1.dp, EazColors.Orange.copy(alpha = 0.65f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "‹",
            fontSize = 22.sp,
            color = EazColors.Orange,
        )
    }
}

@Composable
fun rememberWearPulseAlpha(
    minAlpha: Float = 0.72f,
    maxAlpha: Float = 1f,
    durationMs: Int = 900,
): Float {
    val pulse = rememberInfiniteTransition(label = "wearPulse")
    return pulse.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    ).value
}

@Composable
fun rememberWearPulseScale(
    minScale: Float = 0.96f,
    maxScale: Float = 1.04f,
    durationMs: Int = 900,
): Float {
    val pulse = rememberInfiniteTransition(label = "wearPulseScale")
    return pulse.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    ).value
}

/** Design preview with a soft pulse while processing (upload / save in progress). */
@Composable
fun WearPulsingAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val alpha = rememberWearPulseAlpha()
    val scale = rememberWearPulseScale()
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier
            .scale(scale)
            .alpha(alpha),
        contentScale = contentScale,
    )
}

@Composable
fun WearPulsingGenerateButton(
    onClick: () -> Unit,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    economy: WearEconomySnapshot? = null,
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
        val showEazCost = economy?.walletActive == true && economy.isGenerateFree != true
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showEazCost) {
                WearEazCoinIcon(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.button,
                color = EazColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            if (showEazCost) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = formatWearEazAmount(economy.eazGenerateCost),
                    style = MaterialTheme.typography.button,
                    color = EazColors.TextPrimary,
                    fontSize = 12.sp,
                )
            } else if (economy?.walletActive == true && economy.isGenerateFree) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Free",
                    style = MaterialTheme.typography.caption2,
                    color = EazColors.TextPrimary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

internal fun formatWearEazAmount(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", rounded)
    }
}

/**
 * Wide upload arrow + tray (same motif as Eazy QR center logo / creator phone upload QR).
 */
@Composable
fun WearUploadArrowIcon(
    modifier: Modifier = Modifier.size(18.dp),
) {
    val gradient = Brush.verticalGradient(
        0f to Color(0xFFFFE082),
        0.35f to Color(0xFFFBBF24),
        0.7f to EazColors.Orange,
        1f to Color(0xFFEA580C),
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun nx(x: Float) = x / 24f * w
        fun ny(y: Float) = y / 24f * h

        val icon = Path().apply {
            moveTo(nx(12f), ny(2.6f))
            lineTo(nx(20.4f), ny(11.2f))
            lineTo(nx(16.6f), ny(11.2f))
            lineTo(nx(16.6f), ny(14f))
            lineTo(nx(20f), ny(14f))
            lineTo(nx(20f), ny(18.8f))
            lineTo(nx(4f), ny(18.8f))
            lineTo(nx(4f), ny(14f))
            lineTo(nx(7.4f), ny(14f))
            lineTo(nx(7.4f), ny(11.2f))
            lineTo(nx(3.6f), ny(11.2f))
            close()
        }
        drawPath(icon, gradient)
    }
}

/** Upload control outside the search pill — distinct from orange mic button. */
@Composable
fun WearUploadIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(32.dp),
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(EazColors.CreatorSurface)
            .border(1.5.dp, EazColors.Orange.copy(alpha = 0.85f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WearUploadArrowIcon(modifier = Modifier.size(16.dp))
    }
}

/** Circular icon-only action (triangle menu layout). */
@Composable
fun WearIconCircleButton(
    onClick: () -> Unit,
    icon: String,
    contentDescription: String,
    modifier: Modifier = Modifier.size(44.dp),
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (enabled) EazColors.Orange else EazColors.Orange.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = 18.sp, color = EazColors.TextPrimary)
    }
}

@Composable
fun WearTriangleIconActions(
    topStart: @Composable () -> Unit,
    topEnd: @Composable () -> Unit,
    bottomCenter: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topStart()
            topEnd()
        }
        bottomCenter()
    }
}

@Composable
fun WearSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit = {},
    onVoiceClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchSubmit()
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
            ),
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
        WearRoundIconButton(
            onClick = onVoiceClick,
            contentDescription = "Voice search",
            modifier = Modifier.size(micSize),
            icon = { Text("🎤", fontSize = if (compact) 11.sp else 14.sp) },
        )
    }
}
