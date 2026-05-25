package com.eazpire.creator.wear.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TetrisEase = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val BlockLight = Color(0xFFF6F6F6)
private val BlockAccent = Color(0xFFFDE047)

private data class TetrisBlock(
    val x: Float,
    val y: Float,
    val color: Color,
    val delayMs: Int,
)

private val TetrisBlocks = listOf(
    TetrisBlock(36f, 60f, BlockLight, 0),
    TetrisBlock(55f, 60f, BlockAccent, 150),
    TetrisBlock(74f, 60f, BlockLight, 300),
    TetrisBlock(55f, 41f, BlockLight, 450),
    TetrisBlock(74f, 41f, BlockLight, 600),
)

private const val CycleMs = 2000
private const val ViewW = 120f
private const val ViewH = 110f
private const val BlockSize = 18f
private const val BlockRadius = 3f

/** Falling Tetris-piece assembly loader (matches web prototype timing). */
@Composable
fun WearTetrisAssemblyLoader(
    modifier: Modifier = Modifier.size(60.dp),
) {
    val transition = rememberInfiniteTransition(label = "tetris")
    val global by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CycleMs, easing = TetrisEase),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tetrisPhase",
    )
    Canvas(modifier) {
        val sx = size.width / ViewW
        val sy = size.height / ViewH
        val globalMs = global * CycleMs
        TetrisBlocks.forEach { block ->
            val localMs = (globalMs + block.delayMs) % CycleMs
            val t = localMs / CycleMs.toFloat()
            val (dy, alpha) = tetrisBlockKeyframe(t)
            val px = block.x * sx
            val py = (block.y + dy) * sy
            val w = BlockSize * sx
            val h = BlockSize * sy
            drawRoundRect(
                color = block.color.copy(alpha = alpha),
                topLeft = Offset(px, py),
                size = Size(w, h),
                cornerRadius = CornerRadius(BlockRadius * sx, BlockRadius * sy),
            )
        }
    }
}

/** @return vertical offset (viewBox px) and alpha for one block cycle */
private fun tetrisBlockKeyframe(t: Float): Pair<Float, Float> {
    return when {
        t < 0.45f -> {
            val p = t / 0.45f
            val eased = TetrisEase.transform(p)
            Pair(lerp(-42f, 0f, eased), eased)
        }
        t < 0.75f -> Pair(0f, 1f)
        else -> {
            val p = (t - 0.75f) / 0.25f
            val eased = TetrisEase.transform(p)
            Pair(lerp(0f, 28f, eased), 1f - eased)
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
