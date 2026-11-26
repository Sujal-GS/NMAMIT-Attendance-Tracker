package com.nmamit.attendance.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.ui.theme.*
import java.util.Locale

@Composable
fun OverallMetricRing(
    percentage: Double,
    attended: Int,
    conducted: Int,
    modifier: Modifier = Modifier
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percentage.toFloat().coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 1000),
        label = "overallPercent"
    )

    val ringColor = when {
        percentage < 75.0 -> RedDanger
        percentage < 85.0 -> AmberWarning
        else -> CyanPrimary
    }

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            // Track background
            drawCircle(
                color = DarkSurfaceBorder,
                style = Stroke(width = strokeWidth)
            )

            // Progress Arc
            val sweepAngle = (animatedPercent / 100f) * 360f
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%.1f%%", percentage),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = "$attended / $conducted hrs",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}
