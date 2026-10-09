package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarPulseView(
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    isScanning: Boolean = true,
    accentColor: Color = CyberCyan,
    secondaryColor: Color = NeonPurple
) {
    val transition = rememberInfiniteTransition(label = "RadarTransition")

    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweep"
    )

    val pulse1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )

    val pulse2 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )

    val pulse3 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse3"
    )

    Box(
        modifier = modifier
            .testTag("radar_pulse_view")
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = (this.size.width / 2f) * 0.95f

            // Static background rings
            for (step in 1..4) {
                val ringRadius = maxRadius * (step / 4f)
                drawCircle(
                    color = accentColor.copy(alpha = 0.12f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Crosshair lines
            drawLine(
                color = accentColor.copy(alpha = 0.15f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = accentColor.copy(alpha = 0.15f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            if (isScanning) {
                // Expanding wave pulses
                listOf(pulse1, pulse2, pulse3).forEach { p ->
                    val r = maxRadius * p
                    val alpha = ((1f - p) * 0.45f).coerceIn(0f, 1f)
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Rotating radar sweep needle
                val rad = Math.toRadians(sweepAngle.toDouble())
                val needleEnd = Offset(
                    x = (center.x + cos(rad) * maxRadius).toFloat(),
                    y = (center.y + sin(rad) * maxRadius).toFloat()
                )

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(secondaryColor.copy(alpha = 0.8f), accentColor.copy(alpha = 0.9f), Color.Transparent),
                        start = center,
                        end = needleEnd
                    ),
                    start = center,
                    end = needleEnd,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Glowing center core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor, secondaryColor, Color.Transparent),
                    center = center,
                    radius = 24.dp.toPx()
                ),
                radius = 16.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = center
            )
        }
    }
}
