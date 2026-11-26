package com.nmamit.attendance.ui.dashboard.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.model.SubjectAttendanceDto
import com.nmamit.attendance.ui.components.GlassInputField
import com.nmamit.attendance.ui.components.GlassPillBadge
import com.nmamit.attendance.ui.components.GlassSelectDropdown
import com.nmamit.attendance.ui.components.GlassSubCard
import com.nmamit.attendance.ui.components.LiquidGlassCard
import com.nmamit.attendance.ui.components.SectionTag
import com.nmamit.attendance.ui.components.bouncyClickable
import com.nmamit.attendance.ui.dashboard.DashboardUiState
import com.nmamit.attendance.ui.dashboard.SimDelta
import com.nmamit.attendance.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val ResultBoxGradient = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f))
)
private val ResultBoxShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)
private val AdviceBoxShape = RoundedCornerShape(10.dp)

@Composable
fun SubjectPlannerTab(
    uiState: DashboardUiState,
    onTargetChanged: (Int) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSimulateSubject: (String, Int, Int) -> Unit,
    onResetSubjectSim: (String) -> Unit,
    onSimScopeChanged: (String) -> Unit,
    onAdjustSimAttend: (Int) -> Unit,
    onAdjustSimBunk: (Int) -> Unit,
    onApplySimPreset: (Int, Int) -> Unit,
    onResetSimulator: () -> Unit,
    onOpenAbsentModal: (SubjectAttendanceDto) -> Unit,
    onRetryFetch: () -> Unit
) {
    if (uiState.isSummaryLoading && uiState.subjects.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Foreground, strokeWidth = 2.dp)
        }
        return
    }

    if (!uiState.summaryError.isNullOrBlank() && uiState.subjects.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            LiquidGlassCard(cornerRadius = 22.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(uiState.summaryError, color = Rose, fontSize = 13.5.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRetryFetch,
                        colors = ButtonDefaults.buttonColors(containerColor = Foreground, contentColor = BgCanvas)
                    ) {
                        Text("Retry Fetch", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val aggStats = remember(uiState.subjects, uiState.perSubjectSim, uiState.targetPercent) {
        var aggConducted = 0
        var aggAttended = 0
        for (i in uiState.subjects.indices) {
            val s = uiState.subjects[i]
            val delta = uiState.perSubjectSim[s.subCode]
            aggConducted += s.conductedInt + (delta?.extraConducted ?: 0)
            aggAttended += s.attendedInt + (delta?.extraAttended ?: 0)
        }
        val aggPct = if (aggConducted > 0) (aggAttended.toDouble() / aggConducted) * 100.0 else 100.0
        val targetPct = uiState.targetPercent.toDouble()
        val tRatio = targetPct / 100.0
        val aggCanBunk = if (aggConducted > 0 && aggPct >= targetPct) {
            maxOf(0, floor((aggAttended / tRatio) - aggConducted).toInt())
        } else 0
        val aggMustAttend = if (aggConducted > 0 && aggPct < targetPct) {
            maxOf(1, ceil(((tRatio * aggConducted) - aggAttended) / (1.0 - tRatio)).toInt())
        } else 0
        AggregatesSnapshot(aggPct, aggConducted, aggAttended, aggCanBunk, aggMustAttend)
    }

    val filteredSubjects = remember(uiState.subjects, uiState.searchQuery) {
        val q = uiState.searchQuery.trim().lowercase()
        if (q.isEmpty()) uiState.subjects
        else uiState.subjects.filter { s ->
            s.subCode.lowercase().contains(q) || s.subName.lowercase().contains(q)
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = navBottom + 92.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "hero", contentType = "hero") {
            AggregateHeroCard(
                aggPct = aggStats.aggPct,
                aggConducted = aggStats.aggConducted,
                aggAttended = aggStats.aggAttended,
                targetPct = uiState.targetPercent,
                canBunk = aggStats.canBunk,
                mustAttend = aggStats.mustAttend
            )
        }

        item(key = "sandbox", contentType = "sandbox") {
            WhatIfSandboxCard(
                subjects = uiState.subjects,
                selectedScope = uiState.simScope,
                extraAttend = uiState.simExtraAttend,
                extraBunk = uiState.simExtraBunk,
                onScopeChanged = onSimScopeChanged,
                onAdjustAttend = onAdjustSimAttend,
                onAdjustBunk = onAdjustSimBunk,
                onApplyPreset = onApplySimPreset,
                onReset = onResetSimulator
            )
        }

        item(key = "toolbar", contentType = "toolbar") {
            TargetAndSearchToolbar(
                selectedTarget = uiState.targetPercent,
                searchQuery = uiState.searchQuery,
                onTargetChanged = onTargetChanged,
                onSearchChanged = onSearchChanged
            )
        }

        item(key = "heading", contentType = "heading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Course Breakdown & Bunk Planner",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = Foreground
                )
                GlassPillBadge(text = "${filteredSubjects.size} Course${if (filteredSubjects.size != 1) "s" else ""}")
            }
        }

        if (filteredSubjects.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                LiquidGlassCard(cornerRadius = 18.dp) {
                    Text(
                        text = "No courses match \"${uiState.searchQuery}\".",
                        fontSize = 13.sp,
                        color = ForegroundMuted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            itemsIndexed(
                items = filteredSubjects,
                key = { idx, subject -> "${subject.subCode}_${subject.subName}_$idx" },
                contentType = { _, _ -> "subject_card" }
            ) { _, subject ->
                SubjectPlannerCard(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(220),
                        fadeOutSpec = tween(160),
                        placementSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                    ),
                    subject = subject,
                    simDelta = uiState.perSubjectSim[subject.subCode] ?: SimDelta(),
                    targetPct = uiState.targetPercent,
                    onSimulate = { att, cond -> onSimulateSubject(subject.subCode, att, cond) },
                    onReset = { onResetSubjectSim(subject.subCode) },
                    onViewAbsentDates = { onOpenAbsentModal(subject) }
                )
            }
        }
    }
}

private data class AggregatesSnapshot(
    val aggPct: Double,
    val aggConducted: Int,
    val aggAttended: Int,
    val canBunk: Int,
    val mustAttend: Int
)

@Composable
private fun AggregateHeroCard(
    aggPct: Double,
    aggConducted: Int,
    aggAttended: Int,
    targetPct: Int,
    canBunk: Int,
    mustAttend: Int
) {
    val animatedPct by animateFloatAsState(
        targetValue = aggPct.toFloat().coerceIn(0f, 100f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "aggGauge"
    )
    val animConducted by animateIntAsState(
        targetValue = aggConducted,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "aggConducted"
    )
    val animAttended by animateIntAsState(
        targetValue = aggAttended,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "aggAttended"
    )
    val targetBuf = when {
        canBunk > 0 -> canBunk
        mustAttend > 0 -> -mustAttend
        else -> 0
    }
    val animBuffer by animateIntAsState(
        targetValue = targetBuf,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "aggBuffer"
    )

    val gaugeColor by animateColorAsState(
        targetValue = when {
            aggPct >= targetPct -> Emerald
            aggPct >= targetPct - 10 -> Amber
            else -> Rose
        },
        animationSpec = tween(220),
        label = "gaugeColor"
    )
    val statusBg by animateColorAsState(
        targetValue = when {
            aggPct >= targetPct -> EmeraldBg
            aggPct >= targetPct - 10 -> AmberBg
            else -> RoseBg
        },
        animationSpec = tween(220),
        label = "statusBg"
    )
    val statusBorder by animateColorAsState(
        targetValue = when {
            aggPct >= targetPct -> EmeraldBorder
            aggPct >= targetPct - 10 -> AmberBorder
            else -> RoseBorder
        },
        animationSpec = tween(220),
        label = "statusBorder"
    )
    val statusText = when {
        aggPct >= targetPct -> "ABOVE $targetPct% TARGET"
        aggPct >= targetPct - 10 -> "CLOSE TO $targetPct% TARGET"
        else -> "BELOW $targetPct% TARGET"
    }
    val headline = when {
        canBunk > 0 -> "$canBunk Safe Skip Hours Available"
        mustAttend > 0 -> "Need $mustAttend Classes to Recover Target"
        else -> "Safe Margin Maintained"
    }

    LiquidGlassCard(
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 9.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        color = gaugeColor,
                        startAngle = -90f,
                        sweepAngle = (animatedPct / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "%.1f%%", animatedPct),
                        fontSize = 20.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.ExtraBold,
                        color = Foreground
                    )
                    Text(
                        text = "AGGREGATE",
                        fontSize = 9.5.sp,
                        letterSpacing = 0.6.sp,
                        color = ForegroundMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(statusBg)
                    .border(1.dp, statusBorder, PillShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(gaugeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = gaugeColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = headline,
                transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.97f)) togetherWith fadeOut(tween(120)) },
                label = "heroHeadline"
            ) { text ->
                Text(
                    text = text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = Foreground,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Analyzing your overall margin against the $targetPct% attendance target.",
                fontSize = 12.sp,
                color = ForegroundMuted,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeroMetricBox(
                modifier = Modifier.weight(1f),
                label = "TOTAL CONDUCTED",
                value = animConducted.toString(),
                sub = "Across all courses",
                valueColor = Foreground,
                subColor = ForegroundMuted
            )
            HeroMetricBox(
                modifier = Modifier.weight(1f),
                label = "TOTAL ATTENDED",
                value = animAttended.toString(),
                sub = "Classes verified",
                valueColor = Emerald,
                subColor = Emerald
            )
            val bufColor = when {
                canBunk > 0 -> Foreground
                mustAttend > 0 -> Rose
                else -> Foreground
            }
            HeroMetricBox(
                modifier = Modifier.weight(1f),
                label = "SAFE BUNK BUFFER",
                value = animBuffer.toString(),
                sub = "Skips at $targetPct%",
                valueColor = bufColor,
                subColor = ForegroundMuted
            )
        }
    }
}

@Composable
private fun HeroMetricBox(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    subColor: Color
) {
    GlassSubCard(
        modifier = modifier,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = ForegroundMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 19.sp,
                fontFamily = AppMonoFont,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                fontSize = 9.5.sp,
                color = subColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatIfSandboxCard(
    subjects: List<SubjectAttendanceDto>,
    selectedScope: String,
    extraAttend: Int,
    extraBunk: Int,
    onScopeChanged: (String) -> Unit,
    onAdjustAttend: (Int) -> Unit,
    onAdjustBunk: (Int) -> Unit,
    onApplyPreset: (Int, Int) -> Unit,
    onReset: () -> Unit
) {
    val scopeOptions = remember(subjects) {
        buildList {
            add("AGGREGATE" to "Overall College Aggregate")
            val seen = HashSet<String>(subjects.size)
            for (i in subjects.indices) {
                val sub = subjects[i]
                if (seen.add(sub.subCode)) {
                    add(sub.subCode to "${sub.subCode} - ${sub.subName}")
                }
            }
        }
    }

    val (baseCond, baseAtt, scopeLabel) = remember(subjects, selectedScope) {
        if (selectedScope == "AGGREGATE") {
            Triple(
                subjects.sumOf { it.conductedInt },
                subjects.sumOf { it.attendedInt },
                "Overall College Aggregate"
            )
        } else {
            val found = subjects.find { it.subCode == selectedScope }
            if (found != null) {
                Triple(found.conductedInt, found.attendedInt, "${found.subCode} - ${found.subName}")
            } else {
                Triple(0, 0, "Overall College Aggregate")
            }
        }
    }

    val currentPct = if (baseCond > 0) (baseAtt.toDouble() / baseCond) * 100.0 else 100.0
    val projCond = baseCond + extraAttend + extraBunk
    val projAtt = baseAtt + extraAttend
    val projPct = if (projCond > 0) (projAtt.toDouble() / projCond) * 100.0 else 100.0
    val diffPct = projPct - currentPct

    val animCurrentPct by animateFloatAsState(
        targetValue = currentPct.toFloat(),
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "simCurPct"
    )
    val animProjPct by animateFloatAsState(
        targetValue = projPct.toFloat(),
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "simProjPct"
    )
    val projColor by animateColorAsState(
        targetValue = when {
            projPct >= 85.0 -> Emerald
            projPct >= 75.0 -> Amber
            else -> Rose
        },
        animationSpec = tween(200),
        label = "simProjColor"
    )

    val safeBunksRemaining = maxOf(0, floor((projAtt / 0.85) - projCond).toInt())
    val needed85 = maxOf(0, ceil(((0.85 * projCond) - projAtt) / 0.15).toInt())
    val needed75 = maxOf(1, ceil(((0.75 * projCond) - projAtt) / 0.25).toInt())

    LiquidGlassCard(
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
    ) {
        SectionTag("// WHAT-IF SANDBOX")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Attendance & Bunk Simulator",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            color = Foreground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Simulate attending future classes or taking planned leaves to see their exact percentage impact before bunking.",
            fontSize = 12.sp,
            color = ForegroundMuted,
            lineHeight = 16.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "SELECT SCOPE:",
            fontSize = 10.5.sp,
            fontFamily = AppMonoFont,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = ForegroundMuted
        )
        Spacer(modifier = Modifier.height(6.dp))

        GlassSelectDropdown(
            selectedLabel = scopeLabel,
            options = scopeOptions,
            selectedKey = selectedScope,
            onSelect = onScopeChanged
        )

        Spacer(modifier = Modifier.height(14.dp))

        SimStepperBox(
            labelIcon = "↑",
            labelText = "CLASSES TO ATTEND",
            value = "+$extraAttend",
            hint = "Consecutive boost attendances",
            accentColor = Emerald,
            onMinus = { onAdjustAttend(-1) },
            onPlus = { onAdjustAttend(1) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SimStepperBox(
            labelIcon = "↓",
            labelText = "CLASSES TO MISS (BUNK)",
            value = "+$extraBunk",
            hint = "Planned skips or leaves",
            accentColor = Rose,
            onMinus = { onAdjustBunk(-1) },
            onPlus = { onAdjustBunk(1) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ResultBoxShape)
                .background(ResultBoxGradient, ResultBoxShape)
                .border(1.dp, Color.White.copy(alpha = 0.16f), ResultBoxShape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURRENT:",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForegroundMuted
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", animCurrentPct),
                        fontSize = 19.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.ExtraBold,
                        color = Foreground
                    )
                }
                Text("➔", fontSize = 18.sp, color = ForegroundMuted)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PROJECTED:",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForegroundMuted
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", animProjPct),
                        fontSize = 23.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.Black,
                        color = projColor
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val (deltaText, deltaBg, deltaColor, deltaBorder) = when {
                    abs(diffPct) < 0.05 -> Quad("No Change (0.0%)", Color.White.copy(alpha = 0.08f), ForegroundMuted, Color.White.copy(alpha = 0.12f))
                    diffPct > 0 -> Quad(String.format(Locale.US, "+%.1f%% 📈", diffPct), EmeraldBg, Emerald, EmeraldBorder)
                    else -> Quad(String.format(Locale.US, "%.1f%% 📉", diffPct), RoseBg, Rose, RoseBorder)
                }
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(deltaBg)
                        .border(1.dp, deltaBorder, PillShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = deltaText,
                        fontSize = 11.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.ExtraBold,
                        color = deltaColor
                    )
                }

                val (zoneText, zoneBg, zoneColor, zoneBorder) = when {
                    projPct >= 85.0 -> Quad("Safe • $safeBunksRemaining buffer", EmeraldBg, Emerald, EmeraldBorder)
                    projPct >= 75.0 -> Quad("Warning (75–85%)", AmberBg, Amber, AmberBorder)
                    else -> Quad("Critical (<75%)", RoseBg, Rose, RoseBorder)
                }
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(zoneBg)
                        .border(1.dp, zoneBorder, PillShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = zoneText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = zoneColor
                    )
                }
            }

            val advice = when {
                extraAttend == 0 && extraBunk == 0 ->
                    "Adjust steppers above or choose a scenario below to project outcomes."
                projPct >= 85.0 && safeBunksRemaining > 0 ->
                    "You remain safely above 85%. You could still miss $safeBunksRemaining more class${if (safeBunksRemaining > 1) "es" else ""}."
                projPct >= 85.0 ->
                    "Right at the 85% boundary. Further bunks will drop you into the warning zone."
                projPct >= 75.0 ->
                    "Eligible for exams, below 85% honor threshold. Attend next $needed85 consecutive classes to reach 85%."
                else ->
                    "Exams barred! Must attend next $needed75 consecutive class${if (needed75 > 1) "es" else ""} to cross 75%."
            }
            Text(
                text = advice,
                fontSize = 11.5.sp,
                color = ForegroundMuted,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = GlassBorderSubtle, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SCENARIOS:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = ForegroundMuted
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimPresetPill("Miss 1 Day (4h)") { onApplyPreset(0, 4) }
                SimPresetPill("Long Weekend (10h)") { onApplyPreset(0, 10) }
                SimPresetPill("Full Week Off (20h)") { onApplyPreset(0, 20) }
                SimPresetPill("1-Week Perfect (+15h)") { onApplyPreset(15, 0) }
                Box(
                    modifier = Modifier
                        .bouncyClickable(onClick = onReset)
                        .clip(PillShape)
                        .border(1.dp, Rose.copy(alpha = 0.3f), PillShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Rose
                    )
                }
            }
        }
    }
}

@Composable
private fun SimStepperBox(
    labelIcon: String,
    labelText: String,
    value: String,
    hint: String,
    accentColor: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    GlassSubCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = labelIcon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = labelText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = ForegroundMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .bouncyClickable(pressedScale = 0.90f, onClick = onMinus)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Foreground)
                }

                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        (slideInVertically(
                            spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)
                        ) { -it / 3 } + scaleIn(initialScale = 0.88f) + fadeIn(tween(140))) togetherWith fadeOut(tween(100))
                    },
                    label = "stepperVal"
                ) { v ->
                    Text(
                        text = v,
                        fontSize = 23.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 60.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .bouncyClickable(pressedScale = 0.90f, onClick = onPlus)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Foreground)
                }
            }

            Text(
                text = hint,
                fontSize = 10.5.sp,
                color = ForegroundSubtle
            )
        }
    }
}

@Composable
private fun SimPresetPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bouncyClickable(onClick = onClick)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), PillShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Foreground
        )
    }
}

@Composable
private fun TargetAndSearchToolbar(
    selectedTarget: Int,
    searchQuery: String,
    onTargetChanged: (Int) -> Unit,
    onSearchChanged: (String) -> Unit
) {
    val targetOptions = remember {
        listOf(
            75 to "75% (Min)",
            80 to "80%",
            85 to "85% (Optimal)",
            90 to "90% (Distinction)"
        )
    }

    LiquidGlassCard(cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
        Text(
            text = "Attendance Target:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Foreground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in targetOptions.indices) {
                val (pct, label) = targetOptions[i]
                val isSelected = selectedTarget == pct
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) Foreground else GlassSubCardBg,
                    animationSpec = tween(200),
                    label = "targetBg"
                )
                val pillBorder by animateColorAsState(
                    targetValue = if (isSelected) Foreground else GlassBorder,
                    animationSpec = tween(200),
                    label = "targetBorder"
                )
                val pillText by animateColorAsState(
                    targetValue = if (isSelected) BgCanvas else ForegroundMuted,
                    animationSpec = tween(200),
                    label = "targetText"
                )
                Box(
                    modifier = Modifier
                        .bouncyClickable { onTargetChanged(pct) }
                        .clip(PillShape)
                        .background(pillBg)
                        .border(1.dp, pillBorder, PillShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pillText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        GlassInputField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = "Search course code or name...",
            height = 38.dp,
            cornerRadius = 50.dp,
            textStyle = TextStyle(color = Foreground, fontSize = 13.5.sp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = ForegroundMuted,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = ForegroundMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .bouncyClickable { onSearchChanged("") }
                    )
                }
            } else null
        )
    }
}

@Composable
private fun SubjectPlannerCard(
    modifier: Modifier = Modifier,
    subject: SubjectAttendanceDto,
    simDelta: SimDelta,
    targetPct: Int,
    onSimulate: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onViewAbsentDates: () -> Unit
) {
    val conducted = subject.conductedInt + simDelta.extraConducted
    val attended = subject.attendedInt + simDelta.extraAttended
    val missed = maxOf(0, conducted - attended)
    val pct = if (conducted > 0) (attended.toDouble() / conducted) * 100.0 else 100.0

    val tRatio = targetPct / 100.0
    val canBunk = if (conducted > 0 && pct >= targetPct) maxOf(0, floor((attended / tRatio) - conducted).toInt()) else 0
    val mustAttend = if (conducted > 0 && pct < targetPct) maxOf(1, ceil(((tRatio * conducted) - attended) / (1.0 - tRatio)).toInt()) else 0

    val statusLabel = when {
        pct >= targetPct -> "ON TRACK"
        pct >= targetPct - 10 -> "WARNING"
        else -> "CRITICAL"
    }
    val statusColor = when {
        pct >= targetPct -> Emerald
        pct >= targetPct - 10 -> Amber
        else -> Rose
    }
    val animatedProgress by animateFloatAsState(
        targetValue = (pct / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "subBar"
    )

    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)
    ) {
        // Header: Subject Identity + Percentage Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.subCode,
                    fontSize = 11.sp,
                    fontFamily = AppMonoFont,
                    fontWeight = FontWeight.Bold,
                    color = ForegroundMuted
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subject.subName,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = Foreground,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            GlassSubCard(cornerRadius = 12.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f%%", pct),
                        fontSize = 18.5.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6dp Progress Bar with protruding Target Marker (deferred to Draw phase)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            val barH = 6.dp.toPx()
            val barTop = (size.height - barH) * 0.5f
            val corner = CornerRadius(50f, 50f)
            drawRoundRect(
                color = GlassSubCardBg,
                topLeft = Offset(0f, barTop),
                size = size.copy(height = barH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = statusColor,
                topLeft = Offset(0f, barTop),
                size = size.copy(width = size.width * animatedProgress, height = barH),
                cornerRadius = corner
            )
            val markerX = size.width * (targetPct / 100f)
            drawLine(
                color = Foreground,
                start = Offset(markerX, 0f),
                end = Offset(markerX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats Line: Attended vs Missed Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Attended: $attended / $conducted",
                fontSize = 11.5.sp,
                color = ForegroundMuted
            )

            val missedBg = if (missed > 0) RoseSoft.copy(alpha = 0.08f) else Emerald.copy(alpha = 0.08f)
            val missedBorder = if (missed > 0) RoseSoft.copy(alpha = 0.25f) else Emerald.copy(alpha = 0.25f)
            Row(
                modifier = Modifier
                    .bouncyClickable(onClick = onViewAbsentDates)
                    .clip(PillShape)
                    .background(missedBg)
                    .border(1.dp, missedBorder, PillShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Missed: $missed class${if (missed != 1) "es" else ""}",
                    fontSize = 11.sp,
                    color = Foreground
                )
                Text(
                    text = if (missed > 0) "LOG" else "100%",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (missed > 0) Rose else Emerald
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bunk Advice Box
        val advice = when {
            conducted == 0 -> BunkAdviceState(
                "No classes conducted yet",
                "Starts calculating after first class",
                ForegroundMuted,
                GlassSubCardBg,
                GlassBorder
            )
            pct >= targetPct && canBunk > 0 -> BunkAdviceState(
                "You can safely bunk $canBunk class${if (canBunk > 1) "es" else ""}",
                "Maintains attendance at or above $targetPct%",
                Emerald,
                EmeraldBg,
                EmeraldBorder
            )
            pct >= targetPct -> BunkAdviceState(
                String.format(Locale.US, "On the edge (%.1f%%)", pct),
                "Cannot miss any class without dropping below $targetPct%",
                Amber,
                AmberBg,
                AmberBorder
            )
            else -> BunkAdviceState(
                "Must attend next $mustAttend class${if (mustAttend > 1) "es" else ""}",
                "Required consecutive attendances to reach $targetPct%",
                Rose,
                RoseBg,
                RoseBorder
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AdviceBoxShape)
                .background(advice.bg)
                .border(1.dp, advice.border, AdviceBoxShape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (pct >= targetPct) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = advice.color,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = advice.main,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = advice.color
                )
                Text(
                    text = advice.sub,
                    fontSize = 10.5.sp,
                    color = advice.color.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = GlassBorderSubtle, thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

        val hasSim = simDelta.extraConducted > 0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasSim) "Sim: +${simDelta.extraAttended} / +${simDelta.extraConducted}" else "What-If Simulator",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = ForegroundMuted
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (missed > 0) {
                    SmallSimActionPill(
                        text = "Dates ($missed)",
                        bgColor = RoseSoft.copy(alpha = 0.08f),
                        borderColor = RoseSoft.copy(alpha = 0.28f),
                        textColor = RoseSoft,
                        onClick = onViewAbsentDates
                    )
                }
                SmallSimActionPill(
                    text = "+1 Attend",
                    bgColor = GlassSubCardBg,
                    borderColor = GlassBorder,
                    textColor = Foreground,
                    onClick = { onSimulate(1, 1) }
                )
                SmallSimActionPill(
                    text = "+1 Miss",
                    bgColor = GlassSubCardBg,
                    borderColor = GlassBorder,
                    textColor = Foreground,
                    onClick = { onSimulate(0, 1) }
                )
                if (hasSim) {
                    Box(
                        modifier = Modifier
                            .bouncyClickable(onClick = onReset)
                            .clip(PillShape)
                            .border(1.dp, GlassBorderSubtle, PillShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Reset",
                            fontSize = 10.5.sp,
                            color = ForegroundMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallSimActionPill(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .bouncyClickable(onClick = onClick)
            .clip(PillShape)
            .background(bgColor)
            .border(1.dp, borderColor, PillShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

private data class BunkAdviceState(
    val main: String,
    val sub: String,
    val color: Color,
    val bg: Color,
    val border: Color
)

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
