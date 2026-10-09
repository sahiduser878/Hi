package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuroraBaseLight
import com.example.ui.theme.AuroraBlueMid
import com.example.ui.theme.AuroraBlueTop
import com.example.ui.theme.AuroraCyanGlow
import com.example.ui.theme.AuroraLilac
import com.example.ui.theme.AuroraSky
import com.example.ui.theme.GlassBorderBottom
import com.example.ui.theme.GlassBorderTop
import com.example.ui.theme.GlassCardBg

@Composable
fun LiquidAuroraBackground(
    modifier: Modifier = Modifier,
    isHeroDeepBlue: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundBrush = if (isHeroDeepBlue) {
        Brush.verticalGradient(
            colors = listOf(
                AuroraBlueTop,
                AuroraBlueMid,
                AuroraSky,
                AuroraBaseLight
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFD6E6FF),
                Color(0xFFE8F1FF),
                AuroraBaseLight,
                Color(0xFFF3F7FF)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Floating 3D liquid orbs / water bubbles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top-right large iridescent liquid sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        AuroraCyanGlow.copy(alpha = 0.35f),
                        AuroraSky.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.88f, h * 0.15f),
                    radius = w * 0.32f
                ),
                radius = w * 0.32f,
                center = Offset(w * 0.88f, h * 0.15f)
            )

            // Mid-right subtle bubble
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        AuroraLilac.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.92f, h * 0.38f),
                    radius = w * 0.16f
                ),
                radius = w * 0.16f,
                center = Offset(w * 0.92f, h * 0.38f)
            )

            // Bottom-left soft liquid sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.40f),
                        AuroraCyanGlow.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.10f, h * 0.82f),
                    radius = w * 0.28f
                ),
                radius = w * 0.28f,
                center = Offset(w * 0.10f, h * 0.82f)
            )
        }

        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    elevation: Dp = 2.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = Brush.verticalGradient(
        colors = listOf(GlassBorderTop, GlassBorderBottom)
    )

    Box(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = Color(0x1A1E40AF), spotColor = Color(0x1A1E40AF))
            .clip(shape)
            .background(GlassCardBg)
            .border(1.2.dp, borderBrush, shape),
        content = content
    )
}
