package com.nmamit.attendance.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.model.SubjectAttendanceDto
import com.nmamit.attendance.ui.theme.*
import java.util.Locale

@Composable
fun SubjectCard(
    subject: SubjectAttendanceDto,
    modifier: Modifier = Modifier
) {
    val percent = subject.percentage
    val isDanger = percent < 75.0
    val isWarning = percent >= 75.0 && percent < 85.0
    val statusColor = when {
        isDanger -> RedDanger
        isWarning -> AmberWarning
        else -> GreenSuccess
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (percent / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.subCode,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subject.subName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f%%", percent),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor,
                trackColor = DarkSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attended: ${subject.attendedInt} / ${subject.conductedInt}",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                if (isDanger) {
                    val needed = subject.classesToRecover(75.0)
                    Text(
                        text = "Must attend $needed to hit 75%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RedDanger
                    )
                } else {
                    val safeBunks = subject.bunkBudget(75.0)
                    Text(
                        text = "Can bunk $safeBunks class${if (safeBunks != 1) "es" else ""}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GreenSuccess
                    )
                }
            }
        }
    }
}
