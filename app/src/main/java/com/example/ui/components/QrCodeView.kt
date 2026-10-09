package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.util.QrEncoder

@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    darkColor: Color = Color(0xFF090D16),
    lightColor: Color = Color(0xFFFFFFFF),
    accentColor: Color = Color(0xFF00E5FF)
) {
    val qrMatrix = remember(content) {
        QrEncoder.encode(content)
    }

    Box(
        modifier = modifier
            .testTag("qr_code_view")
            .size(size)
            .background(lightColor, RoundedCornerShape(16.dp))
            .border(2.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 24.dp)) {
            val moduleCount = qrMatrix.width
            val moduleSize = this.size.width / moduleCount.toFloat()

            for (y in 0 until moduleCount) {
                for (x in 0 until moduleCount) {
                    if (qrMatrix[x, y]) {
                        // Check if part of corner finder pattern for sleek rounded accents
                        val isCorner = (x < 7 && y < 7) ||
                                (x >= moduleCount - 7 && y < 7) ||
                                (x < 7 && y >= moduleCount - 7)

                        val moduleColor = if (isCorner) darkColor else darkColor

                        drawRoundRect(
                            color = moduleColor,
                            topLeft = Offset(x * moduleSize, y * moduleSize),
                            size = Size(moduleSize * 1.02f, moduleSize * 1.02f),
                            cornerRadius = CornerRadius(moduleSize * 0.2f, moduleSize * 0.2f)
                        )
                    }
                }
            }
        }
    }
}
