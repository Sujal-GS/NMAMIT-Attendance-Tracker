package com.nmamit.attendance.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.model.SubjectAttendanceDto
import com.nmamit.attendance.ui.components.GlassSubCard
import com.nmamit.attendance.ui.components.LiquidGlassCard
import com.nmamit.attendance.ui.components.SectionTag
import com.nmamit.attendance.ui.components.bouncyClickable
import com.nmamit.attendance.ui.dashboard.AbsentRecordItem
import com.nmamit.attendance.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AbsentDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

@Composable
fun AbsentDatesModal(
    subject: SubjectAttendanceDto,
    records: List<AbsentRecordItem>,
    onJumpToDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 14.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .heightIn(max = 560.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume backdrop clicks inside modal */ },
            cornerRadius = 24.dp,
            tintColor = AuthCardTint,
            glassAlpha = 0.76f,
            blurRadius = 28.dp,
            enableBlur = true,
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionTag(text = "// ${subject.subCode}")
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Absent Classes Log",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Foreground
                    )
                    Text(
                        text = "${subject.subName} • ${subject.missedInt} missed class${if (subject.missedInt != 1) "es" else ""}",
                        fontSize = 12.sp,
                        color = ForegroundMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .bouncyClickable(onClick = onDismiss)
                        .clip(CircleShape)
                        .background(GlassSubCardBg)
                        .border(1.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ForegroundMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                subject.missedInt == 0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏆", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Zero Absences!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "100% perfect attendance record for this course.",
                            fontSize = 12.5.sp,
                            color = ForegroundMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                records.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${subject.missedInt} class${if (subject.missedInt != 1) "es" else ""} missed overall",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Foreground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detailed date logs are still syncing from monthly records.",
                            fontSize = 12.sp,
                            color = ForegroundMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${records.size} absent session${if (records.size != 1) "s" else ""} found",
                            fontSize = 11.5.sp,
                            color = ForegroundMuted
                        )
                        Text(
                            text = "Tap View Day to inspect",
                            fontSize = 11.sp,
                            color = ForegroundMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = records,
                            key = { idx, rec -> "${rec.dateStr}_${rec.period}_$idx" },
                            contentType = { _, _ -> "absent_record" }
                        ) { _, rec ->
                            AbsentRecordRow(
                                rec = rec,
                                onJumpToDate = { onJumpToDate(rec.dateStr) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AbsentRecordRow(
    rec: AbsentRecordItem,
    onJumpToDate: () -> Unit
) {
    val dateDisplay = remember(rec.dateStr) {
        val parsed = runCatching { LocalDate.parse(rec.dateStr) }.getOrNull()
        parsed?.format(AbsentDateFormatter) ?: rec.dateStr
    }

    GlassSubCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RoseSoft.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Rose,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateDisplay,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("Period ${rec.period} • ${rec.hours} hr${if (rec.hours > 1) "s" else ""}")
                            if (rec.teacher.isNotBlank()) append(" • ${rec.teacher}")
                        },
                        fontSize = 11.sp,
                        color = ForegroundMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(RoseSoft.copy(alpha = 0.14f))
                        .border(1.dp, RoseSoft.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ABSENT",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Rose
                    )
                }

                Box(
                    modifier = Modifier
                        .bouncyClickable(onClick = onJumpToDate)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanAccent.copy(alpha = 0.06f))
                        .border(1.dp, CyanAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "View Day >",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }
            }
        }
    }
}
