package com.nativelens.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HistogramView(bins: IntArray, modifier: Modifier = Modifier) {
    val maxBin = (bins.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(
        modifier = modifier
            .height(80.dp)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(4.dp)
    ) {
        val barWidth = size.width / bins.size
        for (i in bins.indices) {
            val ratio = bins[i].toFloat() / maxBin
            val barHeight = size.height * ratio
            drawRect(
                color = Color(0xFF7CFF6B),
                topLeft = Offset(i * barWidth, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}
