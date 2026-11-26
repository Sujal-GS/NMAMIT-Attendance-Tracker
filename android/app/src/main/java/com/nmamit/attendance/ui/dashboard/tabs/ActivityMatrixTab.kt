package com.nmamit.attendance.ui.dashboard.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.model.DayAttendanceSummary
import com.nmamit.attendance.data.model.SubjectAttendanceDto
import com.nmamit.attendance.ui.components.GlassSelectDropdown
import com.nmamit.attendance.ui.components.GlassSubCard
import com.nmamit.attendance.ui.components.LiquidGlassCard
import com.nmamit.attendance.ui.components.SectionTag
import com.nmamit.attendance.ui.components.bouncyClickable
import com.nmamit.attendance.ui.dashboard.DashboardUiState
import com.nmamit.attendance.ui.dashboard.DayBarStat
import com.nmamit.attendance.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val HeatmapBoardShape = RoundedCornerShape(20.dp)
private val HeatmapBoardBorder = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.06f))
)
private val TooltipShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
fun ActivityMatrixTab(
    uiState: DashboardUiState,
    streaks: Triple<Int, Int, Int>,
    weeklyPattern: List<DayBarStat>,
    monthlyTrend: List<DayBarStat>,
    getFilteredDayStats: (String, String) -> Pair<Int, Int>,
    onSelectSubjectFilter: (String) -> Unit,
    onJumpToDate: (String) -> Unit
) {
    var selectedCellDate by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "heatmap", contentType = "heatmap") {
            HeatmapCard(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                uiState = uiState,
                selectedCellDate = selectedCellDate,
                getFilteredDayStats = getFilteredDayStats,
                onSelectSubjectFilter = {
                    selectedCellDate = null
                    onSelectSubjectFilter(it)
                },
                onSelectCell = { dateStr ->
                    selectedCellDate = if (selectedCellDate == dateStr) null else dateStr
                },
                onJumpToDate = onJumpToDate
            )
        }

        item(key = "insights", contentType = "insights") {
            SmartInsightsSection(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                uiState = uiState,
                streaks = streaks,
                weeklyPattern = weeklyPattern
            )
        }

        item(key = "weekly_pattern", contentType = "bar_chart") {
            WeeklyPatternCard(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                weeklyPattern = weeklyPattern
            )
        }

        item(key = "monthly_trend", contentType = "bar_chart") {
            MonthlyTrendCard(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                monthlyTrend = monthlyTrend
            )
        }

        item(key = "leaderboard", contentType = "leaderboard") {
            SubjectLeaderboardCard(
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(220),
                    placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ),
                subjects = uiState.subjects,
                semesterData = uiState.semesterData
            )
        }
    }
}

@Composable
private fun HeatmapCard(
    modifier: Modifier = Modifier,
    uiState: DashboardUiState,
    selectedCellDate: String?,
    getFilteredDayStats: (String, String) -> Pair<Int, Int>,
    onSelectSubjectFilter: (String) -> Unit,
    onSelectCell: (String) -> Unit,
    onJumpToDate: (String) -> Unit
) {
    val filterOptions = remember(uiState.subjects, uiState.semesterData) {
        val list = mutableListOf("ALL" to "All Subjects Combined")
        val seen = mutableSetOf<String>()
        uiState.subjects.forEach {
            if (seen.add(it.subCode)) list.add(it.subCode to "${it.subCode} - ${it.subName}")
        }
        uiState.semesterData.values.forEach { day ->
            day.classes?.forEach { cls ->
                val code = cls.subCode
                if (!code.isNullOrBlank() && seen.add(code)) {
                    list.add(code to "$code - ${cls.subName ?: code}")
                }
            }
        }
        list
    }

    val selectedLabel = remember(filterOptions, uiState.heatmapSubjectFilter) {
        filterOptions.firstOrNull { it.first == uiState.heatmapSubjectFilter }?.second
            ?: "All Subjects Combined"
    }

    val today = remember { LocalDate.now() }
    val todayStr = remember(today) { today.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val weeks = remember(today) {
        val startYear = if (today.monthValue < 8) today.year - 1 else today.year
        val aug1 = LocalDate.of(startYear, 8, 1)
        val startMonday = aug1.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        List(22) { w ->
            List(7) { d -> startMonday.plusDays((w * 7 + d).toLong()) }
        }
    }

    // Precompute flat date strings & attendance levels so the 22x7 Canvas renders in < 0.3ms
    val dateGrid = remember(weeks) {
        Array(22) { w ->
            Array(7) { d -> weeks[w][d].format(DateTimeFormatter.ISO_LOCAL_DATE) }
        }
    }

    val levelGrid = remember(dateGrid, uiState.semesterData, uiState.heatmapSubjectFilter, todayStr) {
        Array(22) { w ->
            IntArray(7) { d ->
                val dStr = dateGrid[w][d]
                if (dStr > todayStr) -1
                else {
                    val (att, cond) = getFilteredDayStats(dStr, uiState.heatmapSubjectFilter)
                    when {
                        cond == 0 -> 0
                        att == cond -> 4
                        att.toDouble() / cond >= 0.75 -> 3
                        att > 0 -> 2
                        else -> 1
                    }
                }
            }
        }
    }

    val monthLabels = remember(weeks) {
        val labels = mutableListOf<Pair<Int, String>>()
        val fmt = DateTimeFormatter.ofPattern("MMM", Locale.US)
        weeks.forEachIndexed { wIdx, week ->
            val firstOfMonth = week.firstOrNull { it.dayOfMonth == 1 }
            if (firstOfMonth != null) {
                if (labels.isEmpty() || (wIdx - labels.last().first) >= 3) {
                    labels.add(wIdx to firstOfMonth.format(fmt))
                }
            } else if (wIdx == 0) {
                labels.add(0 to week.first().format(fmt))
            }
        }
        labels
    }

    val scrollState = rememberScrollState()
    val consumeRemainingHorizontal = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                return if (source == NestedScrollSource.UserInput) Offset(available.x, 0f) else Offset.Zero
            }
        }
    }

    val cellSize = 16.dp
    val cellGap = 4.dp
    val colWidth = cellSize + cellGap
    val density = LocalDensity.current
    val cellSizePx = remember(density) { with(density) { cellSize.toPx() } }
    val cellGapPx = remember(density) { with(density) { cellGap.toPx() } }
    val stepPx = cellSizePx + cellGapPx

    val gridWidth = colWidth * 22 - cellGap
    val gridHeight = cellSize * 7 + cellGap * 6

    val waveProgress = remember { Animatable(0f) }
    LaunchedEffect(uiState.heatmapSubjectFilter) {
        waveProgress.snapTo(0f)
        waveProgress.animateTo(
            targetValue = 1.35f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }

    val selectedPulse = remember { Animatable(0f) }
    LaunchedEffect(selectedCellDate) {
        if (selectedCellDate != null) {
            selectedPulse.snapTo(0.6f)
            selectedPulse.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.50f, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTag(text = "// ACTIVITY MATRIX")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Attendance Heatmap",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    color = Foreground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "GitHub-style daily class attendance density. Tap any cell to inspect schedule and attendances.",
                    fontSize = 12.sp,
                    color = ForegroundMuted,
                    lineHeight = 16.5.sp
                )
            }
            if (uiState.isHeatmapLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                    color = Foreground
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Filter Subject:",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = ForegroundMuted
        )
        Spacer(modifier = Modifier.height(6.dp))

        GlassSelectDropdown(
            selectedLabel = selectedLabel,
            options = filterOptions,
            selectedKey = uiState.heatmapSubjectFilter,
            onSelect = onSelectSubjectFilter
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Nested .heatmap-board-wrapper
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HeatmapBoardShape)
                .background(Color.White.copy(alpha = 0.025f))
                .border(1.dp, HeatmapBoardBorder, HeatmapBoardShape)
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(top = 24.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(cellGap)
                ) {
                    val dayLabels = remember { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") }
                    for (i in dayLabels.indices) {
                        Box(
                            modifier = Modifier.height(cellSize),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = dayLabels[i],
                                fontSize = 10.sp,
                                fontFamily = AppMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = ForegroundMuted
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(consumeRemainingHorizontal)
                        .horizontalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .width(colWidth * weeks.size)
                            .height(18.dp)
                    ) {
                        monthLabels.forEach { (wIdx, name) ->
                            Text(
                                text = name,
                                fontSize = 10.5.sp,
                                fontFamily = AppMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = ForegroundMuted,
                                modifier = Modifier.offset(x = colWidth * wIdx)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val cr = remember(density) { with(density) { 3.5.dp.toPx() } }
                    val corner = remember(cr) { CornerRadius(cr, cr) }
                    val cellSz = remember(cellSizePx) { Size(cellSizePx, cellSizePx) }
                    val glowPad = remember(density) { with(density) { 1.5.dp.toPx() } }
                    val glowSz = remember(cellSizePx, glowPad) {
                        Size(cellSizePx + glowPad * 2, cellSizePx + glowPad * 2)
                    }
                    val glowCorner = remember(cr, glowPad) {
                        CornerRadius(cr + glowPad, cr + glowPad)
                    }
                    val subtleStroke = remember(density) { Stroke(width = with(density) { 0.75.dp.toPx() }) }
                    val highlightStroke = remember(density) { Stroke(width = with(density) { 1.5.dp.toPx() }) }
                    val futureColor = remember { HeatmapLevel0.copy(alpha = 0.25f) }
                    val emptyBorder = remember { Color.White.copy(alpha = 0.03f) }
                    val todayBorder = remember { Foreground.copy(alpha = 0.85f) }
                    val selectedRingColor = remember { Foreground.copy(alpha = 0.22f) }
                    val baseColors = remember {
                        arrayOf(HeatmapLevel0, HeatmapLevel1, HeatmapLevel2, HeatmapLevel3, HeatmapLevel4)
                    }
                    val emeraldGlowBase = remember { Emerald }
                    val amberGlowBase = remember { Amber }
                    val roseGlowBase = remember { Rose }
                    val ringPadBase = remember(density) { with(density) { 2.dp.toPx() } }

                    // Single high-speed Canvas replacing 154 Box composables + 462 animations
                    Canvas(
                        modifier = Modifier
                            .width(gridWidth)
                            .height(gridHeight)
                            .pointerInput(dateGrid, todayStr) {
                                detectTapGestures { offset ->
                                    val w = (offset.x / stepPx).toInt()
                                    val d = (offset.y / stepPx).toInt()
                                    if (w in 0 until 22 && d in 0 until 7) {
                                        val inCellX = (offset.x - w * stepPx) <= cellSizePx
                                        val inCellY = (offset.y - d * stepPx) <= cellSizePx
                                        if (inCellX && inCellY) {
                                            val dStr = dateGrid[w][d]
                                            if (dStr <= todayStr) {
                                                onSelectCell(dStr)
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val wave = waveProgress.value
                        val pulseScale = selectedPulse.value
                        val ringPad = ringPadBase * pulseScale
                        val ringSz = Size(cellSizePx + ringPad * 2, cellSizePx + ringPad * 2)
                        val ringCorner = CornerRadius(cr + ringPad, cr + ringPad)

                        for (w in 0 until 22) {
                            val colReveal = ((wave - w * 0.016f) * 2.8f).coerceIn(0f, 1f)
                            if (colReveal <= 0.02f) continue
                            val x = w * stepPx
                            for (d in 0 until 7) {
                                val y = d * stepPx
                                val level = levelGrid[w][d]
                                val dStr = dateGrid[w][d]
                                val isFuture = level < 0
                                val isToday = dStr == todayStr
                                val isSelected = dStr == selectedCellDate

                                val baseColor = if (level in 0..4) baseColors[level] else futureColor
                                val cellColor = if (colReveal < 0.99f) baseColor.copy(alpha = baseColor.alpha * colReveal) else baseColor

                                // Soft ambient glow halo on active cells (zero RenderNode shadow overhead)
                                if (!isFuture && (level == 1 || level == 2 || level == 4)) {
                                    val glowBase = when (level) {
                                        4 -> emeraldGlowBase
                                        2 -> amberGlowBase
                                        else -> roseGlowBase
                                    }
                                    drawRoundRect(
                                        color = glowBase.copy(alpha = 0.24f * colReveal),
                                        topLeft = Offset(x - glowPad, y - glowPad),
                                        size = glowSz,
                                        cornerRadius = glowCorner
                                    )
                                }

                                drawRoundRect(
                                    color = cellColor,
                                    topLeft = Offset(x, y),
                                    size = cellSz,
                                    cornerRadius = corner
                                )

                                when {
                                    isSelected -> {
                                        drawRoundRect(
                                            color = selectedRingColor,
                                            topLeft = Offset(x - ringPad, y - ringPad),
                                            size = ringSz,
                                            cornerRadius = ringCorner
                                        )
                                        drawRoundRect(
                                            color = Foreground,
                                            topLeft = Offset(x, y),
                                            size = cellSz,
                                            cornerRadius = corner,
                                            style = highlightStroke
                                        )
                                    }
                                    isToday -> drawRoundRect(
                                        color = todayBorder,
                                        topLeft = Offset(x, y),
                                        size = cellSz,
                                        cornerRadius = corner,
                                        style = highlightStroke
                                    )
                                    level == 0 -> drawRoundRect(
                                        color = emptyBorder,
                                        topLeft = Offset(x, y),
                                        size = cellSz,
                                        cornerRadius = corner,
                                        style = subtleStroke
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tapped Cell Inspection Tooltip
        AnimatedVisibility(
            visible = selectedCellDate != null,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            selectedCellDate?.let { dateStr ->
                val parsed = remember(dateStr) { runCatching { LocalDate.parse(dateStr) }.getOrNull() }
                val formatted = parsed?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)) ?: dateStr
                val (att, cond) = getFilteredDayStats(dateStr, uiState.heatmapSubjectFilter)
                val dayClasses = remember(uiState.semesterData, dateStr, uiState.heatmapSubjectFilter) {
                    val list = uiState.semesterData[dateStr]?.classes.orEmpty()
                    if (uiState.heatmapSubjectFilter == "ALL") list
                    else list.filter { (it.subCode ?: "") == uiState.heatmapSubjectFilter }
                }

                val (badgeText, badgeColor, badgeBg) = when {
                    cond == 0 -> Triple("Off / Holiday", ForegroundMuted, Color.White.copy(alpha = 0.1f))
                    att == cond -> Triple("100% Flawless", Emerald, Emerald.copy(alpha = 0.2f))
                    att.toDouble() / cond >= 0.75 -> Triple("${((att * 100.0) / cond).roundToInt()}% Attended", Emerald, Emerald.copy(alpha = 0.15f))
                    att > 0 -> Triple("${((att * 100.0) / cond).roundToInt()}% Partial", Amber, Amber.copy(alpha = 0.2f))
                    else -> Triple("0% Missed All", Rose, Rose.copy(alpha = 0.2f))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(TooltipShape)
                        .background(Color(0xF50C0C10))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), TooltipShape)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Foreground
                        )
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = badgeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = if (cond > 0) "$att of $cond class${if (cond > 1) "es" else ""} attended" else "No scheduled classes",
                        fontSize = 12.sp,
                        color = ForegroundMuted
                    )

                    if (dayClasses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val displayCount = minOf(4, dayClasses.size)
                        for (i in 0 until displayCount) {
                            val c = dayClasses[i]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (c.isPresent) Emerald else Rose)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = c.subName ?: c.subCode ?: "Class",
                                    fontSize = 11.5.sp,
                                    color = Foreground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (dayClasses.size > 4) {
                            Text(
                                text = "+${dayClasses.size - 4} more",
                                fontSize = 11.sp,
                                color = ForegroundMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .bouncyClickable { onJumpToDate(dateStr) }
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyanAccent.copy(alpha = 0.08f))
                                .border(1.dp, CyanAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "View in Calendar →",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Legend Row
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "💡 Tap any cell to inspect schedule or jump to Calendar",
                fontSize = 11.5.sp,
                color = ForegroundMuted
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(text = "Legend:", fontSize = 11.sp, color = ForegroundMuted)
                Text(text = "Less", fontSize = 10.sp, color = ForegroundMuted)
                val legendColors = remember {
                    listOf(HeatmapLevel0, HeatmapLevel1, HeatmapLevel2, HeatmapLevel3, HeatmapLevel4)
                }
                for (i in legendColors.indices) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(legendColors[i])
                            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(3.dp))
                    )
                }
                Text(text = "More", fontSize = 10.sp, color = ForegroundMuted)
            }
        }
    }
}

@Composable
private fun SmartInsightsSection(
    modifier: Modifier = Modifier,
    uiState: DashboardUiState,
    streaks: Triple<Int, Int, Int>,
    weeklyPattern: List<DayBarStat>
) {
    val fullDayName = remember {
        mapOf(
            "Mon" to "Monday",
            "Tue" to "Tuesday",
            "Wed" to "Wednesday",
            "Thu" to "Thursday",
            "Fri" to "Friday",
            "Sat" to "Saturday"
        )
    }

    val weakestDay = remember(weeklyPattern) {
        weeklyPattern.filter { it.conducted > 0 && it.percentage != null }.minByOrNull { it.percentage ?: 101 }
    }
    val bestSub = remember(uiState.subjects) {
        uiState.subjects
            .filter { it.conductedInt > 0 }
            .maxWithOrNull(compareBy<SubjectAttendanceDto> { it.percentage }.thenBy { it.conductedInt })
    }
    val (totalMissed, missedPct) = remember(uiState.semesterData) {
        var totalCond = 0
        var totalAtt = 0
        uiState.semesterData.values.forEach {
            totalCond += it.conducted
            totalAtt += it.attended
        }
        val missed = maxOf(0, totalCond - totalAtt)
        val pct = if (totalCond > 0) (missed.toDouble() / totalCond) * 100.0 else 0.0
        missed to pct
    }

    val (activeStreak, maxStreak, _) = streaks

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTag(text = "// SMART INSIGHTS", modifier = Modifier.padding(horizontal = 4.dp))

        InsightRowCard(
            emoji = "📅",
            label = "WEAKEST DAY",
            value = weakestDay?.let { fullDayName[it.label] ?: it.label } ?: "—",
            sub = weakestDay?.percentage?.let { "$it% avg attendance" } ?: "No data recorded",
            accentColor = Rose
        )
        InsightRowCard(
            emoji = "🏆",
            label = "BEST SUBJECT",
            value = bestSub?.subName ?: "—",
            sub = bestSub?.let {
                if (it.percentage >= 99.95) "100% perfect attendance"
                else String.format(Locale.US, "%.1f%% attendance", it.percentage)
            } ?: "—",
            accentColor = Emerald
        )
        InsightRowCard(
            emoji = "⚠️",
            label = "CLASSES MISSED",
            value = "$totalMissed class${if (totalMissed == 1) "" else "es"}",
            sub = String.format(Locale.US, "%.1f%% of all classes skipped", missedPct),
            accentColor = Amber
        )
        InsightRowCard(
            emoji = "🔥",
            label = "LONGEST STREAK",
            value = "$maxStreak day${if (maxStreak == 1) "" else "s"}",
            sub = "Active streak: $activeStreak day${if (activeStreak == 1) "" else "s"}",
            accentColor = FireOrange
        )
    }
}

@Composable
private fun InsightRowCard(
    emoji: String,
    label: String,
    value: String,
    sub: String,
    accentColor: Color
) {
    val accentOverlay = remember(accentColor) {
        Brush.linearGradient(
            colors = listOf(accentColor.copy(alpha = 0.07f), Color.Transparent),
            start = Offset.Zero,
            end = Offset(500f, 300f)
        )
    }
    val accentBorder = remember(accentColor) {
        Brush.verticalGradient(
            colors = listOf(accentColor.copy(alpha = 0.34f), accentColor.copy(alpha = 0.16f))
        )
    }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(13.dp),
        tintColor = Color(0xFF0E0E12),
        glassAlpha = 0.76f,
        accentOverlayBrush = accentOverlay,
        borderBrush = accentBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 17.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontFamily = AppMonoFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = ForegroundMuted
                )
                Text(
                    text = value,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = ForegroundMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WeeklyPatternCard(
    modifier: Modifier = Modifier,
    weeklyPattern: List<DayBarStat>
) {
    LiquidGlassCard(modifier = modifier, cornerRadius = 20.dp, contentPadding = PaddingValues(16.dp)) {
        SectionTag(text = "// WEEKLY DNA")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Day-of-Week Pattern",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Foreground
        )
        Text(
            text = "Which day you attend best & worst",
            fontSize = 12.sp,
            color = ForegroundMuted
        )
        Spacer(modifier = Modifier.height(16.dp))
        VerticalBarsChart(stats = weeklyPattern)
    }
}

@Composable
private fun MonthlyTrendCard(
    modifier: Modifier = Modifier,
    monthlyTrend: List<DayBarStat>
) {
    LiquidGlassCard(modifier = modifier, cornerRadius = 20.dp, contentPadding = PaddingValues(16.dp)) {
        SectionTag(text = "// TREND")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Month-over-Month Trend",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Foreground
        )
        Text(
            text = "Attendance % by calendar month",
            fontSize = 12.sp,
            color = ForegroundMuted
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (monthlyTrend.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No monthly attendance history recorded yet",
                    fontSize = 12.sp,
                    color = ForegroundMuted
                )
            }
        } else {
            VerticalBarsChart(stats = monthlyTrend)
        }
    }
}

@Composable
private fun VerticalBarsChart(stats: List<DayBarStat>) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val animFraction by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "chartReveal"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stats.forEach { stat ->
            val pct = stat.percentage
            val barColor = when {
                pct == null -> Color.White.copy(alpha = 0.1f)
                pct >= 85 -> Emerald
                pct >= 75 -> Amber
                else -> Rose
            }
            val targetFraction = if (pct != null) (pct / 100f).coerceIn(0.08f, 1f) else 0f

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = pct?.let { "$it%" } ?: "—",
                    fontSize = 10.5.sp,
                    fontFamily = AppMonoFont,
                    fontWeight = FontWeight.Bold,
                    color = ForegroundMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .widthIn(max = 44.dp)
                        .fillMaxWidth()
                        .height(85.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.035f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                        .drawBehind {
                            val fillFrac = (targetFraction * animFraction).coerceIn(0f, 1f)
                            if (fillFrac > 0f) {
                                val h = size.height * fillFrac
                                val y = size.height - h
                                val cr = 5.dp.toPx()
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(barColor, barColor.copy(alpha = 0.45f)),
                                        startY = y,
                                        endY = size.height
                                    ),
                                    topLeft = Offset(0f, y),
                                    size = Size(size.width, h),
                                    cornerRadius = CornerRadius(cr, cr)
                                )
                            }
                        }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stat.label,
                    fontSize = 11.sp,
                    fontFamily = AppMonoFont,
                    fontWeight = FontWeight.Bold,
                    color = ForegroundMuted
                )
            }
        }
    }
}

@Composable
private fun SubjectLeaderboardCard(
    modifier: Modifier = Modifier,
    subjects: List<SubjectAttendanceDto>,
    semesterData: Map<String, DayAttendanceSummary>
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val animProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "lbReveal"
    )

    val ranked = remember(subjects, semesterData) {
        val list = if (subjects.isNotEmpty()) {
            subjects.map {
                LeaderboardEntry(
                    code = it.subCode,
                    name = it.subName,
                    attended = it.attendedInt,
                    conducted = it.conductedInt,
                    pct = it.percentage
                )
            }
        } else {
            val map = linkedMapOf<String, LeaderboardEntry>()
            semesterData.values.forEach { day ->
                day.classes?.forEach { cls ->
                    val code = cls.subCode ?: return@forEach
                    val name = cls.subName ?: code
                    val prev = map[code] ?: LeaderboardEntry(code, name, 0, 0, 0.0)
                    val h = cls.hoursCount
                    val att = if (cls.isPresent) h else 0
                    val nextAtt = prev.attended + att
                    val nextCond = prev.conducted + h
                    map[code] = prev.copy(
                        attended = nextAtt,
                        conducted = nextCond,
                        pct = if (nextCond > 0) (nextAtt.toDouble() / nextCond) * 100.0 else 0.0
                    )
                }
            }
            map.values.toList()
        }
        list.sortedWith(compareByDescending<LeaderboardEntry> { it.pct }.thenByDescending { it.conducted })
    }

    LiquidGlassCard(modifier = modifier, cornerRadius = 20.dp, contentPadding = PaddingValues(16.dp)) {
        SectionTag(text = "// SUBJECT LEADERBOARD")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Subjects Ranked by Attendance",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Foreground
        )
        Text(
            text = "From highest to lowest across your courses",
            fontSize = 12.sp,
            color = ForegroundMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (ranked.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No subject data found", fontSize = 13.sp, color = ForegroundMuted)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ranked.forEachIndexed { idx, entry ->
                    val color = when {
                        entry.pct >= 85.0 -> Emerald
                        entry.pct >= 75.0 -> Amber
                        else -> Rose
                    }
                    val targetWidth = (entry.pct / 100.0).toFloat().coerceIn(0.02f, 1f)

                    GlassSubCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.width(34.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        when (idx) {
                                            0 -> Text("🥇", fontSize = 17.sp)
                                            1 -> Text("🥈", fontSize = 17.sp)
                                            2 -> Text("🥉", fontSize = 17.sp)
                                            else -> Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White.copy(alpha = 0.06f))
                                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "#${idx + 1}",
                                                    fontSize = 10.5.sp,
                                                    fontFamily = AppMonoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ForegroundMuted
                                                )
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Foreground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${entry.code} • ${entry.attended}/${entry.conducted} classes attended",
                                            fontSize = 10.5.sp,
                                            fontFamily = AppMonoFont,
                                            color = ForegroundMuted
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", entry.pct),
                                    fontSize = 14.sp,
                                    fontFamily = AppMonoFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = color
                                )
                            }

                            // Draw-phase horizontal progress bar (zero recomposition during reveal)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(PillShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .drawBehind {
                                        val w = size.width * (targetWidth * animProgress).coerceIn(0f, 1f)
                                        if (w > 0f) {
                                            drawRoundRect(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(color, color.copy(alpha = 0.85f)),
                                                    startX = 0f,
                                                    endX = w
                                                ),
                                                size = Size(w, size.height),
                                                cornerRadius = CornerRadius(50f, 50f)
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class LeaderboardEntry(
    val code: String,
    val name: String,
    val attended: Int,
    val conducted: Int,
    val pct: Double
)
