package com.nmamit.attendance.ui.dashboard.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.model.DailyClassRecord
import com.nmamit.attendance.data.model.DayAttendanceSummary
import com.nmamit.attendance.ui.components.GlassSubCard
import com.nmamit.attendance.ui.components.LiquidGlassCard
import com.nmamit.attendance.ui.components.SectionTag
import com.nmamit.attendance.ui.components.bouncyClickable
import com.nmamit.attendance.ui.components.fluidEnter
import com.nmamit.attendance.ui.dashboard.DashboardUiState
import com.nmamit.attendance.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val PillShape = RoundedCornerShape(50)
private val DayCellShape = RoundedCornerShape(9.dp)
private val NavButtonBorder = Brush.verticalGradient(
    listOf(GlassBorderSpecular.copy(alpha = 0.25f), GlassBorder)
)
private val CellStatusPresentBg = Color(0x3330D158)
private val CellStatusPartialBg = Color(0x33FFD60A)
private val CellStatusAbsentBg = Color(0x33FF453A)
private val CellStatusPresentBorder = Emerald.copy(alpha = 0.22f)
private val CellStatusPartialBorder = Amber.copy(alpha = 0.24f)
private val CellStatusAbsentBorder = Rose.copy(alpha = 0.24f)

@Composable
fun CalendarTab(
    uiState: DashboardUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToday: () -> Unit,
    onSelectDate: (String) -> Unit
) {
    val year = uiState.calYear
    val month = uiState.calMonth
    val yearMonth = remember(year, month) { YearMonth.of(year, month) }
    val cacheKey = remember(year, month) { "%04d-%02d".format(year, month) }
    val monthData = uiState.monthDataMap[cacheKey].orEmpty()

    val monthTotals = remember(monthData) {
        var conducted = 0
        var attended = 0
        monthData.values.forEach { day ->
            conducted += day.conducted
            attended += day.attended
        }
        val missed = maxOf(0, conducted - attended)
        val rate = if (conducted > 0) (attended.toDouble() / conducted) * 100.0 else 0.0
        MonthTotals(conducted, attended, missed, rate)
    }

    val animConducted by animateIntAsState(
        targetValue = monthTotals.conducted,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "calCond"
    )
    val animAttended by animateIntAsState(
        targetValue = monthTotals.attended,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "calAtt"
    )
    val animMissed by animateIntAsState(
        targetValue = monthTotals.missed,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "calMiss"
    )
    val animRate by animateFloatAsState(
        targetValue = monthTotals.rate.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "calRate"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Month Calendar Liquid Card
        item(key = "calendar_card", contentType = "calendar_card") {
            LiquidGlassCard(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = tween(220),
                        fadeOutSpec = tween(160),
                        placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                    )
                    .fluidEnter(delayMs = 0, offsetYDp = 12f),
                cornerRadius = 18.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTag("// CALENDAR LOG")
                        Spacer(modifier = Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = yearMonth,
                            transitionSpec = {
                                val forward = targetState.isAfter(initialState)
                                val enter = slideInHorizontally(
                                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                ) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(200))
                                val exit = slideOutHorizontally(tween(150)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(130))
                                enter togetherWith exit
                            },
                            label = "monthTitleAnim"
                        ) { ym ->
                            Text(
                                text = "${ym.month.getDisplayName(TextStyle.FULL, Locale.US)} ${ym.year}",
                                fontSize = 18.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp,
                                color = Foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CalNavIconButton(onClick = onPrevMonth) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Month",
                                tint = Foreground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .bouncyClickable(pressedScale = 0.92f, onClick = onGoToday)
                                .clip(PillShape)
                                .background(GlassSubCardBg)
                                .border(1.dp, NavButtonBorder, PillShape)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Today",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Foreground
                            )
                        }
                        CalNavIconButton(onClick = onNextMonth) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month",
                                tint = Foreground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GlassBorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MonthStatCard(
                            modifier = Modifier.weight(1f),
                            label = "CLASSES HELD",
                            value = animConducted.toString(),
                            valueColor = Foreground
                        )
                        MonthStatCard(
                            modifier = Modifier.weight(1f),
                            label = "ATTENDED",
                            value = animAttended.toString(),
                            valueColor = Emerald
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MonthStatCard(
                            modifier = Modifier.weight(1f),
                            label = "MISSED",
                            value = animMissed.toString(),
                            valueColor = Rose
                        )
                        val rateColor by animateColorAsState(
                            targetValue = when {
                                monthTotals.conducted == 0 -> Foreground
                                monthTotals.rate >= 85.0 -> Emerald
                                monthTotals.rate >= 75.0 -> Foreground
                                else -> Rose
                            },
                            animationSpec = tween(260),
                            label = "rateColor"
                        )
                        MonthStatCard(
                            modifier = Modifier.weight(1f),
                            label = "MONTHLY RATE",
                            value = String.format(Locale.US, "%.1f%%", animRate),
                            valueColor = rateColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val weekdays = remember { listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (i in weekdays.indices) {
                        Text(
                            text = weekdays[i],
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForegroundMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (uiState.isCalendarLoading && monthData.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Foreground, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Fetching attendance records...", fontSize = 12.sp, color = ForegroundMuted)
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = yearMonth,
                        transitionSpec = {
                            val forward = targetState.isAfter(initialState)
                            val enter = slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                            ) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(200))
                            val exit = slideOutHorizontally(tween(150)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(130))
                            enter togetherWith exit
                        },
                        label = "calendarGridSlide"
                    ) { targetYm ->
                        val targetKey = "%04d-%02d".format(targetYm.year, targetYm.monthValue)
                        val targetData = uiState.monthDataMap[targetKey].orEmpty()
                        CalendarMonthGrid(
                            year = targetYm.year,
                            month = targetYm.monthValue,
                            selectedDate = uiState.selectedDate,
                            monthData = targetData,
                            onSelectDate = onSelectDate
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💡 Tap any date cell above to inspect the period-by-period class timetable.",
                    fontSize = 11.sp,
                    color = ForegroundMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 2. Daily Timetable Card
        item(key = "daily_timetable", contentType = "daily_timetable") {
            DailyTimetableCard(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = tween(220),
                        fadeOutSpec = tween(160),
                        placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                    )
                    .fluidEnter(delayMs = 55, offsetYDp = 14f),
                selectedDate = uiState.selectedDate,
                classes = uiState.selectedDayClasses,
                isLoading = uiState.isDailyLoading
            )
        }
    }
}

private data class MonthTotals(
    val conducted: Int,
    val attended: Int,
    val missed: Int,
    val rate: Double
)

@Composable
private fun CalNavIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick)
            .clip(CircleShape)
            .background(GlassSubCardBg)
            .border(1.dp, NavButtonBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun MonthStatCard(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color
) {
    GlassSubCard(
        modifier = modifier,
        cornerRadius = 12.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = ForegroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 19.sp,
                fontFamily = AppMonoFont,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = valueColor
            )
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    year: Int,
    month: Int,
    selectedDate: String,
    monthData: Map<String, DayAttendanceSummary>,
    onSelectDate: (String) -> Unit
) {
    val calendarMeta = remember(year, month) {
        val ym = YearMonth.of(year, month)
        val firstDayOffset = LocalDate.of(year, month, 1).dayOfWeek.value % 7
        val daysInMonth = ym.lengthOfMonth()
        val prevMonthDays = ym.minusMonths(1).lengthOfMonth()
        val totalCells = ((firstDayOffset + daysInMonth + 6) / 7) * 7
        Triple(firstDayOffset, daysInMonth, prevMonthDays to totalCells / 7)
    }
    val firstDayOffset = calendarMeta.first
    val daysInMonth = calendarMeta.second
    val prevMonthDays = calendarMeta.third.first
    val rows = calendarMeta.third.second
    val todayStr = remember { LocalDate.now().toString() }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - firstDayOffset + 1

                    if (dayNum < 1) {
                        OtherMonthCell(dayNum = prevMonthDays + dayNum, modifier = Modifier.weight(1f))
                    } else if (dayNum > daysInMonth) {
                        OtherMonthCell(dayNum = dayNum - daysInMonth, modifier = Modifier.weight(1f))
                    } else {
                        val dateStr = remember(year, month, dayNum) {
                            "%04d-%02d-%02d".format(year, month, dayNum)
                        }
                        val info = monthData[dateStr]
                        val conducted = info?.conducted ?: 0
                        val attended = info?.attended ?: 0
                        val isSelected = dateStr == selectedDate
                        val isToday = dateStr == todayStr

                        CalendarDayCell(
                            modifier = Modifier.weight(1f),
                            dayNum = dayNum,
                            conducted = conducted,
                            attended = attended,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onSelectDate(dateStr) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    dayNum: Int,
    conducted: Int,
    attended: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val statusBg = when {
        conducted <= 0 -> GlassSubCardBg
        attended == conducted -> CellStatusPresentBg
        attended > 0 -> CellStatusPartialBg
        else -> CellStatusAbsentBg
    }

    val statusBorder = when {
        conducted <= 0 -> GlassBorderSubtle
        attended == conducted -> CellStatusPresentBorder
        attended > 0 -> CellStatusPartialBorder
        else -> CellStatusAbsentBorder
    }

    val indicatorColor = when {
        conducted <= 0 -> null
        attended == conducted -> Emerald
        attended > 0 -> Color.White
        else -> Rose
    }

    val cellBg = if (isSelected) Color.White.copy(alpha = 0.14f) else statusBg
    val borderColor = when {
        isSelected -> Foreground
        isToday -> ForegroundMuted
        else -> statusBorder
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                if (isSelected) {
                    val pad = 2.dp.toPx()
                    val cr = 11.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(-pad, -pad),
                        size = Size(size.width + pad * 2, size.height + pad * 2),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                }
            }
            .clip(DayCellShape)
            .background(cellBg)
            .border(
                width = when {
                    isSelected -> 2.dp
                    isToday -> 1.5.dp
                    else -> 1.dp
                },
                color = borderColor,
                shape = DayCellShape
            )
            .padding(top = 4.dp, bottom = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayNum.toString(),
            fontSize = 13.sp,
            fontFamily = AppMonoFont,
            fontWeight = FontWeight.Bold,
            color = Foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = (-2).dp)
        )

        if (indicatorColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .drawBehind {
                        val pad = 2.dp.toPx()
                        drawRoundRect(
                            color = indicatorColor.copy(alpha = 0.36f),
                            topLeft = Offset(-pad, -pad * 0.5f),
                            size = Size(size.width + pad * 2, size.height + pad),
                            cornerRadius = CornerRadius(50f, 50f)
                        )
                    }
                    .width(12.dp)
                    .height(3.dp)
                    .clip(PillShape)
                    .background(indicatorColor)
            )
        }
    }
}

@Composable
private fun OtherMonthCell(dayNum: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .alpha(0.2f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayNum.toString(),
            fontSize = 13.sp,
            fontFamily = AppMonoFont,
            fontWeight = FontWeight.Bold,
            color = ForegroundMuted,
            modifier = Modifier.offset(y = (-2).dp)
        )
    }
}

@Composable
private fun DailyTimetableCard(
    modifier: Modifier = Modifier,
    selectedDate: String,
    classes: List<DailyClassRecord>,
    isLoading: Boolean
) {
    val parsedDate = remember(selectedDate) { runCatching { LocalDate.parse(selectedDate) }.getOrNull() }
    val headingText = if (parsedDate != null) {
        "${parsedDate.dayOfMonth} ${parsedDate.month.getDisplayName(TextStyle.FULL, Locale.US)} ${parsedDate.year}"
    } else selectedDate
    val dayNameText = parsedDate?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.US) ?: "Daily period breakdown"

    val conductedCount = remember(classes) { classes.sumOf { it.hoursCount } }
    val attendedCount = remember(classes) { classes.sumOf { if (it.isPresent) it.hoursCount else 0 } }
    val missedCount = maxOf(0, conductedCount - attendedCount)

    val animConducted by animateIntAsState(
        conductedCount,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "dayCond"
    )
    val animAttended by animateIntAsState(
        attendedCount,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "dayAtt"
    )
    val animMissed by animateIntAsState(
        missedCount,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "dayMiss"
    )

    val (pillText, pillColor) = when {
        conductedCount == 0 -> "No Classes" to ForegroundMuted
        attendedCount == conductedCount -> "100% Attended" to Emerald
        attendedCount > 0 -> "$attendedCount/$conductedCount Attended" to Foreground
        else -> "0% Attended" to Rose
    }

    LiquidGlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTag("// TIMETABLE LOG")
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedContent(
                    targetState = headingText to dayNameText,
                    transitionSpec = {
                        (slideInVertically(
                            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        ) { it / 3 } + fadeIn(tween(180))) togetherWith fadeOut(tween(110))
                    },
                    label = "dayTitleAnim"
                ) { (heading, dayName) ->
                    Column {
                        Text(
                            text = heading,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dayName,
                            fontSize = 12.sp,
                            color = ForegroundMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(GlassSubCardBg)
                    .border(1.dp, GlassBorder, PillShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = pillText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pillColor,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = GlassBorderSubtle)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DayMetricBox(Modifier.weight(1f), "CONDUCTED", animConducted.toString(), Foreground)
            DayMetricBox(Modifier.weight(1f), "ATTENDED", animAttended.toString(), Emerald)
            DayMetricBox(Modifier.weight(1f), "MISSED", animMissed.toString(), Rose)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "PERIOD BREAKDOWN",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = ForegroundMuted
        )
        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = Triple(selectedDate, isLoading, classes),
            transitionSpec = {
                (slideInVertically(
                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ) { it / 12 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120))
            },
            label = "dayPeriodsAnim"
        ) { (_, loading, dayClasses) ->
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Foreground, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                dayClasses.isEmpty() -> {
                    GlassSubCard(cornerRadius = 14.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = ForegroundMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No class records scheduled or logged for this date.",
                                fontSize = 12.5.sp,
                                color = ForegroundMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        dayClasses.forEachIndexed { idx, item ->
                            val isPres = item.isPresent
                            val periodNum = item.period?.ifBlank { "${idx + 1}" } ?: "${idx + 1}"
                            val hours = item.hoursCount
                            val accentBarColor = if (isPres) Emerald else Rose

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GlassSubCardBg)
                                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                                    .drawBehind {
                                        val barW = 3.5.dp.toPx()
                                        val insetV = 8.dp.toPx()
                                        drawRoundRect(
                                            color = accentBarColor,
                                            topLeft = Offset(0f, insetV),
                                            size = Size(barW, (size.height - insetV * 2).coerceAtLeast(4f)),
                                            cornerRadius = CornerRadius(barW, barW)
                                        )
                                    }
                                    .padding(start = 13.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(GlassSubCardBg)
                                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = periodNum,
                                            fontSize = 11.sp,
                                            fontFamily = AppMonoFont,
                                            fontWeight = FontWeight.Bold,
                                            color = Foreground
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.subCode ?: "SUB",
                                            fontSize = 10.5.sp,
                                            fontFamily = AppMonoFont,
                                            fontWeight = FontWeight.Bold,
                                            color = Foreground
                                        )
                                        Text(
                                            text = item.subName ?: "Academic Class",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Foreground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Period $periodNum • $hours Hour${if (hours > 1) "s" else ""}",
                                            fontSize = 10.5.sp,
                                            color = ForegroundMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .clip(PillShape)
                                            .background(if (isPres) EmeraldBg else RoseBg)
                                            .border(1.dp, if (isPres) EmeraldBorder else RoseBorder, PillShape)
                                            .padding(horizontal = 8.dp, vertical = 3.5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPres) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (isPres) Emerald else Rose,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = if (isPres) "PRESENT" else "ABSENT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPres) Emerald else Rose
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayMetricBox(modifier: Modifier, label: String, value: String, color: Color) {
    GlassSubCard(
        modifier = modifier,
        cornerRadius = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = ForegroundMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 19.sp,
                fontFamily = AppMonoFont,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
