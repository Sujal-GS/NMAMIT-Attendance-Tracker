package com.nmamit.attendance.ui.dashboard.components

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.nmamit.attendance.ui.dashboard.DashboardUiState
import com.nmamit.attendance.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WrappedDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

@Composable
fun AttendanceWrappedModal(
    uiState: DashboardUiState,
    streaks: Triple<Int, Int, Int>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val summary = remember(uiState.subjects) {
        val totalCond = uiState.subjects.sumOf { it.conductedInt }
        val totalAtt = uiState.subjects.sumOf { it.attendedInt }
        val overallPct = if (totalCond > 0) (totalAtt.toDouble() / totalCond) * 100.0 else 100.0
        val bestSubject = uiState.subjects
            .filter { it.conductedInt > 0 }
            .maxByOrNull { it.percentage }
        Triple(totalAtt, totalCond, overallPct to bestSubject)
    }
    val totalAtt = summary.first
    val totalCond = summary.second
    val overallPct = summary.third.first
    val bestSubject = summary.third.second
    val isGood = overallPct >= 85.0
    val (_, maxStreak, _) = streaks

    val name = uiState.studentInfo?.name?.takeIf { it.isNotBlank() } ?: "Student User"
    val usn = uiState.studentInfo?.regno?.takeIf { it.isNotBlank() } ?: "NNM24CS1234"
    val pctFormatted = remember(overallPct) { String.format(Locale.US, "%.1f", overallPct) }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animPct by animateFloatAsState(
        targetValue = if (appeared) overallPct.toFloat() else 0f,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "wrappedPct"
    )
    val animAtt by animateIntAsState(
        targetValue = if (appeared) totalAtt else 0,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "wrappedAtt"
    )
    val animStreak by animateIntAsState(
        targetValue = if (appeared) maxStreak else 0,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "wrappedStreak"
    )

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
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume backdrop clicks inside modal */ },
            cornerRadius = 24.dp,
            tintColor = AuthCardTint,
            glassAlpha = 0.76f,
            blurRadius = 28.dp,
            enableBlur = true,
            contentPadding = PaddingValues(16.dp)
        ) {
            // Modal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionTag(text = "// SEMESTER RECAP")
                    Text(
                        text = "Attendance Wrapped ✨",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        color = Foreground
                    )
                }
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

            // 9:16 Story Card Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF050508))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                        .drawWithCache {
                            val step = 28.dp.toPx()
                            val gridColor = Color.White.copy(alpha = 0.045f)
                            val c1 = Offset(size.width * 0.2f, size.height * 0.25f)
                            val r1 = size.width * 0.65f
                            val b1 = Brush.radialGradient(
                                colors = listOf(Emerald.copy(alpha = 0.18f), Color.Transparent),
                                center = c1,
                                radius = r1
                            )
                            val c2 = Offset(size.width * 0.85f, size.height * 0.72f)
                            val r2 = size.width * 0.7f
                            val b2 = Brush.radialGradient(
                                colors = listOf(IndigoAccent.copy(alpha = 0.16f), Color.Transparent),
                                center = c2,
                                radius = r2
                            )
                            onDrawBehind {
                                var x = 0f
                                while (x < size.width) {
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 1f
                                    )
                                    x += step
                                }
                                var y = 0f
                                while (y < size.height) {
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f
                                    )
                                    y += step
                                }
                                drawCircle(brush = b1, radius = r1, center = c1)
                                drawCircle(brush = b2, radius = r2, center = c2)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, GlassBorderSpecular, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ 2026 ATTENDANCE RECAP",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald
                            )
                            Text(
                                text = "NMAMIT PORTAL",
                                fontSize = 10.sp,
                                fontFamily = AppMonoFont,
                                color = ForegroundMuted
                            )
                        }

                        Column {
                            Text(
                                text = name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Foreground
                            )
                            Text(
                                text = "USN: $usn • NITTE University",
                                fontSize = 12.sp,
                                fontFamily = AppMonoFont,
                                color = ForegroundMuted
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, GlassBorderSpecular, RoundedCornerShape(18.dp))
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "OVERALL SEMESTER SCORE",
                                fontSize = 10.sp,
                                fontFamily = AppMonoFont,
                                fontWeight = FontWeight.SemiBold,
                                color = ForegroundMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f%%", animPct),
                                fontSize = 52.sp,
                                fontFamily = AppMonoFont,
                                fontWeight = FontWeight.Black,
                                color = if (isGood) Emerald else Amber
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isGood) EmeraldBg else AmberBg)
                                    .border(
                                        1.dp,
                                        (if (isGood) Emerald else Amber).copy(alpha = 0.4f),
                                        RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isGood) "✓ 85% HONOR ROLL" else "⚠ BUFFER WATCH",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGood) Emerald else Amber
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassSubCard(modifier = Modifier.weight(1f), cornerRadius = 14.dp) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "CLASSES ATTENDED",
                                        fontSize = 9.5.sp,
                                        fontFamily = AppMonoFont,
                                        color = ForegroundMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$animAtt",
                                        fontSize = 24.sp,
                                        fontFamily = AppMonoFont,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Foreground
                                    )
                                    Text(
                                        text = "out of $totalCond held",
                                        fontSize = 10.5.sp,
                                        color = ForegroundMuted
                                    )
                                }
                            }

                            GlassSubCard(modifier = Modifier.weight(1f), cornerRadius = 14.dp) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "LONGEST STREAK",
                                        fontSize = 9.5.sp,
                                        fontFamily = AppMonoFont,
                                        color = ForegroundMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🔥 ${animStreak}d",
                                        fontSize = 24.sp,
                                        fontFamily = AppMonoFont,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Emerald
                                    )
                                    Text(
                                        text = "without bunking",
                                        fontSize = 10.5.sp,
                                        color = ForegroundMuted
                                    )
                                }
                            }
                        }

                        GlassSubCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "TOP SCORING SUBJECT",
                                    fontSize = 9.5.sp,
                                    fontFamily = AppMonoFont,
                                    color = ForegroundMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = bestSubject?.subName ?: "N/A",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Foreground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = bestSubject?.let {
                                        String.format(Locale.US, "%.1f%% Attendance Record", it.percentage)
                                    } ?: "No classes recorded",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald
                                )
                            }
                        }

                        GlassSubCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "ACTIVITY MATRIX PREVIEW",
                                    fontSize = 9.5.sp,
                                    fontFamily = AppMonoFont,
                                    color = ForegroundMuted
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                // Single Canvas replaces 54 Box composables
                                Canvas(
                                    modifier = Modifier
                                        .width((18 * 10 + 17 * 4).dp)
                                        .height((3 * 10 + 2 * 4).dp)
                                ) {
                                    val cell = 10.dp.toPx()
                                    val gap = 4.dp.toPx()
                                    val step = cell + gap
                                    val cr = 2.5.dp.toPx()
                                    val corner = CornerRadius(cr, cr)
                                    val sz = Size(cell, cell)
                                    val dimColor = Color.White.copy(alpha = 0.1f)
                                    for (r in 0 until 3) {
                                        val y = r * step
                                        for (c in 0 until 18) {
                                            val hash = (c * 7 + r * 13) % 10
                                            val dotColor = when {
                                                hash == 0 -> Rose
                                                hash <= 2 -> Amber
                                                hash <= 6 -> Emerald
                                                else -> dimColor
                                            }
                                            drawRoundRect(
                                                color = dotColor,
                                                topLeft = Offset(c * step, y),
                                                size = sz,
                                                cornerRadius = corner
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Crafted with 🖤 for NMAMIT Engineering Students",
                                fontSize = 11.sp,
                                color = ForegroundMuted,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Sujal G S",
                                fontSize = 10.5.sp,
                                color = ForegroundMuted.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable {
                            val topName = bestSubject?.subName ?: "N/A"
                            val topPct = bestSubject?.let { String.format(Locale.US, "%.1f", it.percentage) } ?: "0.0"
                            val shareText = """
                                📊 My NMAMIT Attendance Recap:
                                Overall: $pctFormatted% ${if (isGood) "✓ (Safe)" else "⚠️"}
                                Classes Attended: $totalAtt / $totalCond
                                🔥 Longest Streak: $maxStreak days without bunking!
                                Top Subject: $topName ($topPct%)

                                Tracked with NMAMIT Attendance Tracker 🚀
                            """.trimIndent()
                            clipboard.setText(AnnotatedString(shareText))
                            Toast.makeText(context, "Stats copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(Foreground)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = BgCanvas,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy Stats Text",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BgCanvas
                    )
                }

                Box(
                    modifier = Modifier
                        .bouncyClickable(onClick = onDismiss)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassSubCardBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Close",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Foreground
                    )
                }
            }
        }
    }
}

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
        parsed?.format(WrappedDateFormatter) ?: rec.dateStr
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
