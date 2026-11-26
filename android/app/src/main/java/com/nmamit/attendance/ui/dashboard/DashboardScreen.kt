package com.nmamit.attendance.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmamit.attendance.data.model.StudentInfo
import com.nmamit.attendance.ui.components.GlassSubCard
import com.nmamit.attendance.ui.components.LiquidGlassCard
import com.nmamit.attendance.ui.components.TechGridBackground
import com.nmamit.attendance.ui.components.bouncyClickable
import com.nmamit.attendance.ui.dashboard.components.AbsentDatesModal
import com.nmamit.attendance.ui.dashboard.components.AttendanceWrappedModal
import com.nmamit.attendance.ui.dashboard.tabs.ActivityMatrixTab
import com.nmamit.attendance.ui.dashboard.tabs.CalendarTab
import com.nmamit.attendance.ui.dashboard.tabs.IaMarksTab
import com.nmamit.attendance.ui.dashboard.tabs.SubjectPlannerTab
import com.nmamit.attendance.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val PillShape = RoundedCornerShape(50)
private val ProfileBorderBrush = Brush.verticalGradient(
    listOf(GlassBorderSpecular.copy(alpha = 0.25f), GlassBorder)
)
private val HeaderActionBorderBrush = Brush.verticalGradient(
    listOf(GlassBorderSpecular.copy(alpha = 0.28f), GlassBorder)
)
private val GoldSpotlightOverlay = Brush.linearGradient(
    colors = listOf(
        GoldAccent.copy(alpha = 0.09f),
        Color(0xFF0E0E14).copy(alpha = 0.45f)
    )
)
private val GoldSpotlightTopBar = Brush.horizontalGradient(
    colors = listOf(GoldAccent, Color(0xFFFF9F0A), Rose)
)
private val GoldSpotlightBorder = Brush.linearGradient(
    colors = listOf(
        GoldAccent.copy(alpha = 0.36f),
        Color.White.copy(alpha = 0.14f),
        GoldAccent.copy(alpha = 0.20f)
    )
)
private val ActiveTabBgBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.22f),
        TabActiveBg,
        Color.White.copy(alpha = 0.13f)
    )
)
private val ActiveTabBorderBrush = Brush.verticalGradient(
    listOf(GlassBorderSpecular, GlassBorder)
)

private class CollapsibleHeaderState {
    var heightPx: Int = 0
    var offsetPx by mutableFloatStateOf(0f)
    var settleJob: Job? = null
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hazeState = remember { HazeState() }
    val hasModalOpen = uiState.showWrappedModal || uiState.absentModalSubject != null
    val isSyncing = uiState.isCalendarLoading || uiState.isSummaryLoading || uiState.isIaLoading || uiState.isHeatmapLoading

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            onLogout()
        }
    }

    BackHandler(enabled = hasModalOpen) {
        when {
            uiState.showWrappedModal -> viewModel.setWrappedModalOpen(false)
            uiState.absentModalSubject != null -> viewModel.closeAbsentModal()
        }
    }

    val streaks = uiState.streaks
    val weeklyPattern = uiState.weeklyPattern
    val monthlyTrend = uiState.monthlyTrend

    val tabs = DashboardTab.entries
    val pagerState = rememberPagerState(initialPage = uiState.activeTab.ordinal) { tabs.size }
    val latestActiveTab by rememberUpdatedState(uiState.activeTab)

    // Swipe in HorizontalPager -> sync ViewModel activeTab once settled
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
            .filter { !it.first }
            .map { it.second }
            .distinctUntilChanged()
            .collect { page ->
                val tab = tabs.getOrNull(page) ?: return@collect
                if (tab != latestActiveTab) {
                    viewModel.selectTab(tab)
                }
            }
    }

    // Programmatic tab selection (tab tap or jumpToDateInCalendar) -> smoothly glide pager
    LaunchedEffect(uiState.activeTab) {
        val target = uiState.activeTab.ordinal
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(
                page = target,
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Collapsible header driven purely in Layout phase (0 recompositions during scroll)
    val headerState = remember { CollapsibleHeaderState() }
    val coroutineScope = rememberCoroutineScope()
    val headerScrollConnection = remember(headerState, coroutineScope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy == 0f || headerState.heightPx <= 0) return Offset.Zero
                headerState.settleJob?.cancel()
                val minOffset = -headerState.heightPx.toFloat()
                val prev = headerState.offsetPx
                val next = (prev + dy).coerceIn(minOffset, 0f)
                val consumed = next - prev
                if (consumed != 0f) {
                    headerState.offsetPx = next
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val h = headerState.heightPx.toFloat()
                val cur = headerState.offsetPx
                if (h > 0f && cur < 0f && cur > -h) {
                    val target = if (cur < -h * 0.5f) -h else 0f
                    headerState.settleJob?.cancel()
                    headerState.settleJob = coroutineScope.launch {
                        AnimationState(initialValue = cur).animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) {
                            headerState.offsetPx = value
                        }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    TechGridBackground(
        hazeState = hazeState,
        enableHazeSource = hasModalOpen
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hasModalOpen) Modifier.hazeSource(hazeState) else Modifier)
                .statusBarsPadding()
                .navigationBarsPadding()
                .nestedScroll(headerScrollConnection)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Fixed-height Layout: header collapse reads offsetPx ONLY during placement/graphicsLayer
            // so HorizontalPager and all tab LazyColumns experience ZERO remeasurements while scrolling.
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds(),
                content = {
                    // Child 0: Collapsible Header + Wrapped Spotlight Banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .graphicsLayer {
                                val h = headerState.heightPx
                                val visible = (h + headerState.offsetPx).coerceAtLeast(0f)
                                val collapseFrac = if (h > 0) (visible / h).coerceIn(0f, 1f) else 1f
                                alpha = collapseFrac
                                val s = 0.965f + 0.035f * collapseFrac
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            },
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LiquidAppHeader(
                            studentInfo = uiState.studentInfo,
                            isDemo = uiState.isDemo,
                            isSyncing = isSyncing,
                            onSync = { viewModel.syncAllData() },
                            onLogout = onLogout
                        )

                        WrappedSpotlightBanner(
                            onClick = { viewModel.setWrappedModalOpen(true) }
                        )
                    }

                    // Child 1: Sticky 4-Tab Bar synced 1:1 with HorizontalPager on GPU layer
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SegmentedViewTabs(
                            pagerState = pagerState,
                            onSelectTab = { viewModel.selectTab(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Child 2: Constant-height HorizontalPager hosting lazy-virtualized tab screens
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 0,
                        key = { tabs[it].name },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                .coerceIn(-1f, 1f)
                            val absOffset = abs(pageOffset)
                            val s = 1f - absOffset * 0.03f
                            scaleX = s
                            scaleY = s
                            alpha = 1f - absOffset * 0.18f
                        }
                ) {
                    when (tabs[page]) {
                    DashboardTab.CALENDAR -> {
                        CalendarTab(
                            uiState = uiState,
                            onPrevMonth = { viewModel.prevMonth() },
                            onNextMonth = { viewModel.nextMonth() },
                            onGoToday = { viewModel.goToday() },
                            onSelectDate = { viewModel.selectDate(it) }
                        )
                    }

                    DashboardTab.SUBJECT_PLANNER -> {
                        SubjectPlannerTab(
                            uiState = uiState,
                            onTargetChanged = { viewModel.setTargetPercent(it) },
                            onSearchChanged = { viewModel.setSearchQuery(it) },
                            onSimulateSubject = { code, att, cond ->
                                viewModel.simulateSubject(code, att, cond)
                            },
                            onResetSubjectSim = { viewModel.resetSubjectSimulation(it) },
                            onSimScopeChanged = { viewModel.setSimScope(it) },
                            onAdjustSimAttend = { viewModel.adjustSimAttend(it) },
                            onAdjustSimBunk = { viewModel.adjustSimBunk(it) },
                            onApplySimPreset = { att, bunk -> viewModel.applySimPreset(att, bunk) },
                            onResetSimulator = { viewModel.resetSimulator() },
                            onOpenAbsentModal = { viewModel.openAbsentModal(it) },
                            onRetryFetch = { viewModel.loadAttendance() }
                        )
                    }

                    DashboardTab.ACTIVITY_MATRIX -> {
                        ActivityMatrixTab(
                            uiState = uiState,
                            streaks = streaks,
                            weeklyPattern = weeklyPattern,
                            monthlyTrend = monthlyTrend,
                            getFilteredDayStats = { dateStr, sub ->
                                viewModel.getFilteredDayStats(dateStr, sub)
                            },
                            onSelectSubjectFilter = { viewModel.setHeatmapSubjectFilter(it) },
                            onJumpToDate = { viewModel.jumpToDateInCalendar(it) }
                        )
                    }

                    DashboardTab.IA_MARKS -> {
                        IaMarksTab(
                            uiState = uiState,
                            onRefreshIa = { viewModel.loadIaMarks() }
                        )
                    }
                }
                }
                    }
                }
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val headerPlaceable = measurables[0].measure(loose.copy(maxHeight = Constraints.Infinity))
                headerState.heightPx = headerPlaceable.height

                val tabsPlaceable = measurables[1].measure(loose)
                val pagerHeight = (constraints.maxHeight - tabsPlaceable.height).coerceAtLeast(0)
                val pagerPlaceable = measurables[2].measure(
                    Constraints.fixed(constraints.maxWidth, pagerHeight)
                )

                layout(constraints.maxWidth, constraints.maxHeight) {
                    val minOffset = -headerPlaceable.height.toFloat()
                    val offsetInt = headerState.offsetPx.coerceIn(minOffset, 0f).roundToInt()
                    val visibleHeaderH = (headerPlaceable.height + offsetInt).coerceAtLeast(0)

                    headerPlaceable.placeRelative(0, offsetInt)
                    tabsPlaceable.placeRelative(0, visibleHeaderH)
                    pagerPlaceable.placeRelative(0, visibleHeaderH + tabsPlaceable.height)
                }
            }
        }

        // In-window Frosted Modals (RenderEffect Haze blur enabled only while visible)
        AnimatedVisibility(
            visible = uiState.showWrappedModal,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(150)) + scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(150)
            )
        ) {
            AttendanceWrappedModal(
                uiState = uiState,
                streaks = streaks,
                onDismiss = { viewModel.setWrappedModalOpen(false) }
            )
        }

        AnimatedVisibility(
            visible = uiState.absentModalSubject != null,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(150)) + scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(150)
            )
        ) {
            uiState.absentModalSubject?.let { subject ->
                AbsentDatesModal(
                    subject = subject,
                    records = uiState.absentModalRecords,
                    onJumpToDate = { viewModel.jumpToDateInCalendar(it) },
                    onDismiss = { viewModel.closeAbsentModal() }
                )
            }
        }
    }
}

@Composable
private fun LiquidAppHeader(
    studentInfo: StudentInfo?,
    isDemo: Boolean,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    val rawName = studentInfo?.name?.takeIf { it.isNotBlank() } ?: "Student User"
    val usn = studentInfo?.regno?.takeIf { it.isNotBlank() } ?: "NNM24CS1234"
    val rawDegree = studentInfo?.degree?.takeIf { it.isNotBlank() } ?: "B.Tech"
    val rawDept = studentInfo?.branch?.takeIf { it.isNotBlank() } ?: "Computer Science & Eng"

    val cleanDegree = remember(rawDegree) {
        val paren = Regex("\\((.*?)\\)").find(rawDegree)?.groupValues?.getOrNull(1) ?: rawDegree
        paren
            .replace(Regex("Computer Science & Engineering\\s*\\[(.*?)]", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("Computer Science & Engineering", RegexOption.IGNORE_CASE), "CSE")
            .replace(Regex("Information Science & Engineering", RegexOption.IGNORE_CASE), "ISE")
            .replace(Regex("Electronics & Communication", RegexOption.IGNORE_CASE), "ECE")
    }

    val cleanDept = remember(rawDept) {
        when {
            rawDept.contains("NMAM", ignoreCase = true) -> "NMAMIT"
            rawDept.contains("[") -> rawDept.substringBefore("[").trim()
            else -> rawDept
                .replace(Regex("Computer Science & Engineering", RegexOption.IGNORE_CASE), "CSE")
                .replace(Regex("Information Science & Engineering", RegexOption.IGNORE_CASE), "ISE")
                .replace(Regex("Electronics & Communication", RegexOption.IGNORE_CASE), "ECE")
        }
    }

    val initials = remember(rawName) {
        val parts = rawName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        when {
            parts.size > 1 -> "${parts[0][0]}${parts[1][0]}".uppercase()
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "US"
        }
    }

    LiquidGlassCard(
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
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
                Text(
                    text = "NMAMIT Attendance Tracker",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isDemo) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(GlassSubCardBg)
                            .border(1.dp, GlassBorder, PillShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DEMO",
                            fontSize = 9.sp,
                            fontFamily = AppMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = Foreground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SyncHeaderActionButton(
                    isSyncing = isSyncing,
                    onClick = onSync
                )
                CircularHeaderActionButton(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out",
                    tint = Foreground,
                    onClick = onLogout
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        GlassSubCard(
            cornerRadius = 12.dp,
            borderColor = ProfileBorderBrush
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GlassCardTint.copy(alpha = 0.85f))
                        .border(1.5.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = 12.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = Foreground
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rawName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = usn,
                            fontSize = 11.sp,
                            fontFamily = AppMonoFont,
                            fontWeight = FontWeight.SemiBold,
                            color = Foreground
                        )
                        Text(
                            text = " • $cleanDegree • $cleanDept",
                            fontSize = 11.sp,
                            color = ForegroundMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncHeaderActionButton(
    isSyncing: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
            .clip(CircleShape)
            .background(GlassSubCardBg)
            .border(1.dp, HeaderActionBorderBrush, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isSyncing) {
            val spinTransition = rememberInfiniteTransition(label = "syncSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "syncRotation"
            )
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Attendance",
                tint = Foreground,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer { rotationZ = spinAngle }
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh Attendance",
                tint = Foreground,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun CircularHeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .bouncyClickable(pressedScale = 0.90f, onClick = onClick)
            .clip(CircleShape)
            .background(GlassSubCardBg)
            .border(1.dp, HeaderActionBorderBrush, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun WrappedSpotlightBanner(onClick: () -> Unit) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
        glassAlpha = 0.70f,
        accentOverlayBrush = GoldSpotlightOverlay,
        topAccentBarBrush = GoldSpotlightTopBar,
        borderBrush = GoldSpotlightBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(GoldAccent.copy(alpha = 0.16f))
                    .border(1.dp, GoldAccent.copy(alpha = 0.38f), PillShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "✨ VIRAL RECAP",
                    fontSize = 10.sp,
                    fontFamily = AppMonoFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldAccent
                )
            }
            Text(
                text = "View Story →",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Foreground.copy(alpha = 0.9f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GoldAccent.copy(alpha = 0.15f))
                    .border(1.dp, GoldAccent.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Attendance Wrapped 2026",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp,
                    color = Foreground
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Generate your Spotify-style 9:16 recap story card for WhatsApp & Instagram",
                    fontSize = 12.sp,
                    color = ForegroundMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SegmentedViewTabs(
    pagerState: PagerState,
    onSelectTab: (DashboardTab) -> Unit
) {
    val tabs = DashboardTab.entries
    val highlightedIndex by remember(pagerState) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .roundToInt()
                .coerceIn(0, tabs.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(TabTrack, PillShape)
            .border(1.dp, GlassBorderSubtle, PillShape)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            // Smooth GPU-gliding Liquid Glass highlighter pill (0 recompositions during swipe/glide)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val totalW = constraints.maxWidth
                        val spacingPx = 4.dp.roundToPx()
                        val tabW = ((totalW - spacingPx * (tabs.size - 1)) / tabs.size).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            Constraints.fixed(tabW, constraints.maxHeight)
                        )
                        layout(totalW, placeable.height) {
                            placeable.placeRelativeWithLayer(0, 0) {
                                val progress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                    .coerceIn(0f, (tabs.size - 1).toFloat())
                                val stretch = sin(abs(pagerState.currentPageOffsetFraction).coerceIn(0f, 1f) * PI.toFloat())
                                translationX = progress * (tabW + spacingPx)
                                scaleX = 1f + stretch * 0.11f
                                scaleY = 1f - stretch * 0.045f
                            }
                        }
                    }
                    .clip(PillShape)
                    .background(ActiveTabBgBrush, PillShape)
                    .border(1.dp, ActiveTabBorderBrush, PillShape)
            )

            // Foreground Tab Buttons
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { idx, tab ->
                    val isSelected = idx == highlightedIndex
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) Foreground else ForegroundMuted,
                        animationSpec = tween(180),
                        label = "tabTextColor"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 0.96f,
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
                        label = "tabIconScale"
                    )
                    val icon = when (tab) {
                        DashboardTab.CALENDAR -> Icons.Default.DateRange
                        DashboardTab.SUBJECT_PLANNER -> Icons.Default.BarChart
                        DashboardTab.ACTIVITY_MATRIX -> Icons.Default.GridView
                        DashboardTab.IA_MARKS -> Icons.Default.Description
                    }
                    val shortTitle = when (tab) {
                        DashboardTab.CALENDAR -> "Calendar"
                        DashboardTab.SUBJECT_PLANNER -> "Planner"
                        DashboardTab.ACTIVITY_MATRIX -> "Matrix"
                        DashboardTab.IA_MARKS -> "IA Marks"
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(PillShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectTab(tab) }
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.title,
                            tint = contentColor,
                            modifier = Modifier
                                .size(13.5.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = shortTitle,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
