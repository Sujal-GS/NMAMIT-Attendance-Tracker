package com.nmamit.attendance.ui.dashboard.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.model.IAMarkItem
import com.nmamit.attendance.ui.components.GlassPillBadge
import com.nmamit.attendance.ui.components.GlassSubCard
import com.nmamit.attendance.ui.components.LiquidGlassCard
import com.nmamit.attendance.ui.components.bouncyClickable
import com.nmamit.attendance.ui.dashboard.DashboardUiState
import com.nmamit.attendance.ui.theme.*
import java.util.Locale

private val HeroIconShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(50)
private val AbBadgeShape = RoundedCornerShape(4.dp)

@Composable
fun IaMarksTab(
    uiState: DashboardUiState,
    onRefreshIa: () -> Unit
) {
    val marks = uiState.iaMarks

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "ia_hero", contentType = "ia_hero") {
            IaHeroCard(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                marks = marks
            )
        }

        item(key = "ia_header", contentType = "ia_header") {
            IaSectionHeader(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                subjectCount = marks.size,
                isLoading = uiState.isIaLoading,
                onRefreshIa = onRefreshIa
            )
        }

        item(key = "ia_table", contentType = "ia_table") {
            IaTableCard(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                marks = marks,
                isLoading = uiState.isIaLoading,
                iaError = uiState.iaError
            )
        }
    }
}

@Composable
private fun IaHeroCard(modifier: Modifier = Modifier, marks: List<IAMarkItem>) {
    val metrics = remember(marks) {
        val finals = marks.mapNotNull { it.displayFinalMarks.toDoubleOrNull() }.filter { it > 0.0 }
        val avg = if (finals.isNotEmpty()) finals.average() else null
        val highest = finals.maxOrNull()
        Triple(avg, marks.size, highest)
    }

    val animAvg by animateFloatAsState(
        targetValue = (metrics.first ?: 0.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "iaAvg"
    )
    val animCourses by animateIntAsState(
        targetValue = metrics.second,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "iaCourses"
    )
    val animHighest by animateFloatAsState(
        targetValue = (metrics.third ?: 0.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "iaHighest"
    )

    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(HeroIconShape)
                    .background(CyanAccent.copy(alpha = 0.10f))
                    .border(1.dp, CyanAccent.copy(alpha = 0.28f), HeroIconShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Continuous Internal Evaluation (CIE)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                color = Foreground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mid-Semester Exams, Lab Assessments, and Course Work evaluations published by the university.",
                fontSize = 12.sp,
                color = ForegroundMuted,
                lineHeight = 16.5.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IaMetricBox(
                modifier = Modifier.weight(1f),
                label = "AVG CIE",
                value = if (metrics.first != null) String.format(Locale.US, "%.1f", animAvg) else "—",
                denom = if (metrics.first != null) "/50" else "",
                sub = if (metrics.first != null) String.format(Locale.US, "%.1f%% Overall", (animAvg / 50.0) * 100.0) else "Overall Average",
                valueColor = Emerald
            )
            IaMetricBox(
                modifier = Modifier.weight(1f),
                label = "COURSES",
                value = "$animCourses",
                denom = "",
                sub = "Evaluated",
                valueColor = Foreground
            )
            IaMetricBox(
                modifier = Modifier.weight(1f),
                label = "HIGHEST",
                value = if (metrics.third != null) String.format(Locale.US, "%.1f", animHighest) else "—",
                denom = if (metrics.third != null) "/50" else "",
                sub = "Top Mark",
                valueColor = Foreground
            )
        }
    }
}

@Composable
private fun IaSectionHeader(
    modifier: Modifier = Modifier,
    subjectCount: Int,
    isLoading: Boolean,
    onRefreshIa: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CIE Marks Sheet",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Foreground
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassPillBadge(text = "$subjectCount Subject${if (subjectCount != 1) "s" else ""}")
        }

        Row(
            modifier = Modifier
                .bouncyClickable(enabled = !isLoading, onClick = onRefreshIa)
                .clip(PillShape)
                .background(GlassSubCardBg)
                .border(1.dp, GlassBorder, PillShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                val spinTransition = rememberInfiniteTransition(label = "iaSpin")
                val refreshAngle by spinTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(850, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "iaRefreshAngle"
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Marks",
                    tint = Foreground,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = refreshAngle }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Marks",
                    tint = Foreground,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isLoading) "Syncing..." else "Refresh Marks",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Foreground
            )
        }
    }
}

@Composable
private fun IaTableCard(
    modifier: Modifier = Modifier,
    marks: List<IAMarkItem>,
    isLoading: Boolean,
    iaError: String?
) {
    val consumeRemainingHorizontal = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return if (source == NestedScrollSource.UserInput) Offset(available.x, 0f) else Offset.Zero
            }
        }
    }

    LiquidGlassCard(modifier = modifier, cornerRadius = 20.dp, contentPadding = PaddingValues(0.dp)) {
        val tableMode = when {
            isLoading && marks.isEmpty() -> 0
            marks.isEmpty() -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = tableMode,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
            label = "iaTableState"
        ) { mode ->
            when (mode) {
                0 -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), color = Foreground)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Fetching official university CIE table from student portal...",
                        fontSize = 13.sp,
                        color = ForegroundMuted,
                        textAlign = TextAlign.Center
                    )
                }
                1 -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = iaError ?: "No CIE marks published on university portal yet.",
                        fontSize = 13.sp,
                        color = if (iaError != null) Rose else ForegroundMuted,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    val hScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nestedScroll(consumeRemainingHorizontal)
                            .horizontalScroll(hScroll)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.03f))
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeaderCell("#", 38.dp, TextAlign.Center)
                            HeaderCell("Sub. Code", 96.dp, TextAlign.Start)
                            HeaderCell("Sub. Name", 170.dp, TextAlign.Start)
                            HeaderCell("MSE-1", 60.dp, TextAlign.Center)
                            HeaderCell("MSE-2", 60.dp, TextAlign.Center)
                            HeaderCell("LA-1", 54.dp, TextAlign.Center)
                            HeaderCell("LA-2", 54.dp, TextAlign.Center)
                            HeaderCell("LA-3", 54.dp, TextAlign.Center)
                            HeaderCell("RECORD", 68.dp, TextAlign.Center)
                            HeaderCell("CONDUCT", 74.dp, TextAlign.Center)
                            HeaderCell("MSE(LAB)", 72.dp, TextAlign.Center)
                            HeaderCell("Final", 86.dp, TextAlign.Center)
                        }

                        HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                        marks.forEachIndexed { idx, item ->
                            IaMarkTableRow(index = idx + 1, item = item)
                            if (idx < marks.lastIndex) {
                                HorizontalDivider(color = GlassBorderSubtle, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IaMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    denom: String,
    sub: String,
    valueColor: Color
) {
    GlassSubCard(
        modifier = modifier,
        cornerRadius = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = ForegroundMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontFamily = AppMonoFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor
                )
                if (denom.isNotEmpty()) {
                    Text(
                        text = denom,
                        fontSize = 11.sp,
                        fontFamily = AppMonoFont,
                        color = ForegroundMuted.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                fontSize = 9.5.sp,
                color = if (valueColor == Emerald) Emerald else ForegroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign) {
    Text(
        text = text.uppercase(),
        fontSize = 10.5.sp,
        fontFamily = AppMonoFont,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = ForegroundMuted,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun IaMarkTableRow(index: Int, item: IAMarkItem) {
    Row(
        modifier = Modifier.padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            fontSize = 11.5.sp,
            fontFamily = AppMonoFont,
            color = ForegroundMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(38.dp)
        )

        Box(
            modifier = Modifier
                .width(96.dp)
                .padding(horizontal = 4.dp)
        ) {
            GlassPillBadge(text = item.displaySubCode.ifBlank { "SUB" })
        }

        Text(
            text = item.displaySubName.ifBlank { "Course" },
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Foreground,
            lineHeight = 16.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(170.dp)
                .padding(horizontal = 4.dp)
        )

        ScoreCell(item.mse1, 60.dp)
        ScoreCell(item.mse2, 60.dp)
        ScoreCell(item.la1, 54.dp)
        ScoreCell(item.la2, 54.dp)
        ScoreCell(item.la3, 54.dp)
        ScoreCell(item.record, 68.dp)
        ScoreCell(item.conduction, 74.dp)
        ScoreCell(item.mseLab, 72.dp)

        val finalRaw = item.displayFinalMarks
        val finalNum = finalRaw.toDoubleOrNull()
        val finalFormatted = remember(finalRaw) {
            finalNum?.let { String.format(Locale.US, "%.2f", it) } ?: finalRaw.ifBlank { "—" }
        }
        val finalColor = when {
            finalNum == null -> ForegroundMuted
            finalNum >= 25 -> EmeraldBright
            else -> Rose
        }

        Row(
            modifier = Modifier.width(86.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = finalFormatted,
                fontSize = 14.sp,
                fontFamily = AppMonoFont,
                fontWeight = FontWeight.ExtraBold,
                color = finalColor
            )
            if (finalNum != null) {
                Text(
                    text = "/50",
                    fontSize = 10.5.sp,
                    fontFamily = AppMonoFont,
                    color = ForegroundMuted.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun ScoreCell(raw: String?, width: Dp) {
    val clean = raw?.trim().orEmpty()
    val isAb = clean == "-2" || clean == "-2.00"
    val isBlank = clean.isEmpty() || clean == "-"

    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isAb -> Box(
                modifier = Modifier
                    .clip(AbBadgeShape)
                    .background(RoseSoft.copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "AB",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Rose
                )
            }
            isBlank -> Text(
                text = "—",
                fontSize = 12.5.sp,
                fontFamily = AppMonoFont,
                color = ForegroundMuted.copy(alpha = 0.35f),
                textAlign = TextAlign.Center
            )
            else -> Text(
                text = clean,
                fontSize = 12.5.sp,
                fontFamily = AppMonoFont,
                fontWeight = FontWeight.SemiBold,
                color = Foreground,
                textAlign = TextAlign.Center
            )
        }
    }
}
