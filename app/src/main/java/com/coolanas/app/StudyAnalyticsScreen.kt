package com.coolanas.app

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class AnalyticsSection { Monthly, Calendar }

private val chartPalette = listOf(
    Color(0xFF8CC7F2), Color(0xFFC8A6F3), Color(0xFFF2A8D5),
    Color(0xFFEDC36F), Color(0xFF8DD0B4), Color(0xFFE98E87),
    Color(0xFF9FADEB), Color(0xFFB4C47B)
)

private fun addRunningSessionPreview(
    snapshot: StudyAnalyticsSnapshot,
    timer: StudyTimerState,
    serverNowMillis: Long,
    subject: String
): StudyAnalyticsSnapshot {
    val start = timer.startedAtMs ?: return snapshot
    if (serverNowMillis <= start) return snapshot
    val daily = snapshot.daily.toMutableMap()
    val subjects = snapshot.dailySubjects.mapValues { it.value.toMutableMap() }.toMutableMap()
    val hours = snapshot.dailyHours.mapValues { it.value.toMutableMap() }.toMutableMap()
    val zone = ZoneId.systemDefault()
    var cursor = start
    while (cursor < serverNowMillis) {
        val local = Instant.ofEpochMilli(cursor).atZone(zone)
        val nextHour = local.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
            .plusHours(1).toInstant().toEpochMilli()
        val end = minOf(serverNowMillis, nextHour.coerceAtLeast(cursor + 1L))
        val duration = end - cursor
        val date = local.toLocalDate().toString()
        val hour = local.hour
        val subjectName = subject.ifBlank { "Unassigned" }
        daily[date] = (daily[date] ?: 0L) + duration
        val subjectDay = subjects.getOrPut(date) { mutableMapOf() }
        subjectDay[subjectName] = (subjectDay[subjectName] ?: 0L) + duration
        val hourDay = hours.getOrPut(date) { mutableMapOf() }
        hourDay[hour] = (hourDay[hour] ?: 0L) + duration
        cursor = end
    }
    return snapshot.copy(
        daily = daily,
        dailySubjects = subjects.mapValues { it.value.toMap() },
        dailyHours = hours.mapValues { it.value.toMap() }
    )
}

@Composable
fun StudyAnalyticsScreen(
    snapshot: StudyAnalyticsSnapshot,
    subjects: List<StudySubject>,
    timer: StudyTimerState,
    serverNowMillis: Long,
    activeSubject: String,
    loading: Boolean,
    profileAvatar: Bitmap?,
    onBack: () -> Unit,
    onProfileClick: () -> Unit
) {
    var section by remember { mutableStateOf(AnalyticsSection.Monthly) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val data = addRunningSessionPreview(snapshot, timer, serverNowMillis, activeSubject)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 34.dp, bottom = 22.dp)
                .height(68.dp)
        ) {
            val backArrowColor = MaterialTheme.colorScheme.onSurface
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(42.dp)
                    .noRippleClickable(onBack),
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Canvas(Modifier.padding(13.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.72f, size.height * 0.08f)
                        lineTo(size.width * 0.30f, size.height * 0.5f)
                        lineTo(size.width * 0.72f, size.height * 0.92f)
                    }
                    drawPath(path, backArrowColor, style = Stroke(width = 2.5.dp.toPx()))
                }
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Analytics",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Your study activity",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(42.dp)
                    .noRippleClickable(onProfileClick),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                if (profileAvatar != null) {
                    androidx.compose.foundation.Image(
                        bitmap = profileAvatar.asImageBitmap(),
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text("•", color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp)
                    }
                }
            }
        }

        if (loading && data.daily.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            AnimatedContent(
                targetState = section,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    val direction = if (targetState == AnalyticsSection.Calendar) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(520, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
                        initialOffsetX = { it * direction }
                    ) + fadeIn(tween(330))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(420, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
                            targetOffsetX = { -it * direction }
                        ) + fadeOut(tween(250)))
                },
                label = "analytics-page-swipe"
            ) { page ->
                when (page) {
                    AnalyticsSection.Monthly -> MonthlyAnalytics(
                        snapshot = data,
                        subjects = subjects,
                        month = month,
                        onMonthChanged = { month = it }
                    )
                    AnalyticsSection.Calendar -> CalendarAnalytics(
                        snapshot = data,
                        subjects = subjects,
                        month = month,
                        selectedDate = selectedDate,
                        today = today,
                        onMonthChanged = { requested ->
                            val latestMonth = YearMonth.from(today)
                            val next = if (requested > latestMonth) latestMonth else requested
                            month = next
                            selectedDate = if (next == latestMonth) today else next.atDay(1)
                        },
                        onDateSelected = { selectedDate = it }
                    )
                }
            }
        }

        AnalyticsSegmentedControl(
            section = section,
            onSelect = { section = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 88.dp)
        )
    }
}

@Composable
private fun AnalyticsSegmentedControl(
    section: AnalyticsSection,
    onSelect: (AnalyticsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    var swipeDistance by remember(section) { mutableFloatStateOf(0f) }
    Surface(
        modifier = modifier
            .height(54.dp)
            .pointerInput(section) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(swipeDistance) > 55f) {
                            onSelect(if (swipeDistance < 0f) AnalyticsSection.Calendar else AnalyticsSection.Monthly)
                        }
                        swipeDistance = 0f
                    },
                    onHorizontalDrag = { _, amount -> swipeDistance += amount }
                )
            },
        shape = RoundedCornerShape(28.dp),
        color = Color(0xB82A2D33),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.11f)),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(AnalyticsSection.Monthly, AnalyticsSection.Calendar).forEach { item ->
                val selected = item == section
                Surface(
                    modifier = Modifier.weight(1f).fillMaxSize().noRippleClickable { onSelect(item) },
                    shape = RoundedCornerShape(23.dp),
                    color = if (selected) Color(0xFF4B405E) else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (item == AnalyticsSection.Monthly) "Monthly" else "Calendar",
                            color = if (selected) Color(0xFFF1E8FF) else Color(0xFFB8BBC4),
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyAnalytics(
    snapshot: StudyAnalyticsSnapshot,
    subjects: List<StudySubject>,
    month: YearMonth,
    onMonthChanged: (YearMonth) -> Unit
) {
    val today = LocalDate.now()
    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val prefix = month.toString()
    val values = monthDays.map { snapshot.daily[it.toString()] ?: 0L }
    val historyStart = snapshot.daily.keys
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .minOrNull() ?: today.minusDays(6)
    val chartDayCount = ChronoUnit.DAYS.between(historyStart, today).toInt().coerceIn(6, 3650)
    val chartDays = (chartDayCount downTo 0).map { today.minusDays(it.toLong()) }
    val chartValues = chartDays.map { snapshot.daily[it.toString()] ?: 0L }
    val total = values.sum()
    val studiedDays = values.count { it > 0L }
    val elapsedDays = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
    val dailyAverage = if (elapsedDays > 0) total / elapsedDays else 0L
    val bestDay = values.maxOrNull() ?: 0L
    val dailyScroll = rememberLazyListState()
    LaunchedEffect(today) {
        dailyScroll.scrollToItem(max(0, chartDays.size - 7))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )

        AnalyticsCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MetricCell("Study time", formatAnalyticsDuration(total), Modifier.weight(1f))
                Surface(
                    Modifier.width(1.dp).height(48.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ) {}
                MetricCell("Days studied", studiedDays.toString(), Modifier.weight(1f))
            }
            Surface(Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) {}
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MetricCell("Daily average", formatAnalyticsDuration(dailyAverage), Modifier.weight(1f))
                Surface(
                    Modifier.width(1.dp).height(48.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ) {}
                MetricCell("Best day", formatAnalyticsDuration(bestDay), Modifier.weight(1f))
            }
        }

        AnalyticsCard {
            Text("Daily study time", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Latest 7 days · swipe back through your full history",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            val maxValue = chartValues.maxOrNull()?.coerceAtLeast(60_000L) ?: 60_000L
            LazyRow(
                state = dailyScroll,
                modifier = Modifier.fillMaxWidth().height(184.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                itemsIndexed(chartDays, key = { _, date -> date.toString() }) { index, date ->
                    val amount = chartValues[index]
                    val target = (amount.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                    val animated by animateFloatAsState(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = 540,
                            delayMillis = index * 16,
                            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                        ),
                        label = "daily-bar-$date"
                    )
                    Column(
                        modifier = Modifier.width(34.dp).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.58f)
                                .height((132f * animated).coerceAtLeast(2f).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (amount > 0L) Color(0xFF9B82C7) else MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (date.dayOfMonth == 1) date.format(DateTimeFormatter.ofPattern("MMM")) else date.dayOfMonth.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        AnalyticsCard {
            Text("Yearly activity", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("Scrolls to the latest date automatically", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            YearHeatmap(snapshot.daily, today)
        }

        val subjectTotals = linkedMapOf<String, Long>()
        snapshot.dailySubjects.forEach { (date, valuesForDay) ->
            if (date.startsWith(prefix)) {
                valuesForDay.forEach { (name, duration) ->
                    subjectTotals[name] = (subjectTotals[name] ?: 0L) + duration
                }
            }
        }
        if (subjectTotals.isNotEmpty()) {
            AnalyticsCard {
                Text("Subjects this month", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                PieBreakdown(subjectTotals, subjects, showLeaders = false)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CalendarAnalytics(
    snapshot: StudyAnalyticsSnapshot,
    subjects: List<StudySubject>,
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onMonthChanged: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val key = selectedDate.toString()
    val total = snapshot.daily[key] ?: 0L
    val subjectTotals = snapshot.dailySubjects[key].orEmpty().filterValues { it > 0L }
    val hourly = snapshot.dailyHours[key].orEmpty()
    val taskSnapshot = snapshot.taskDays[key]
    val taskRows = taskSnapshot?.tasks
        ?: snapshot.tasks.filter { it.completedAt == key }.map { it.copy(completed = true) }
    var monthDrag by remember(month) { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(month) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (monthDrag < -75f) onMonthChanged(month.plusMonths(1))
                            else if (monthDrag > 75f) onMonthChanged(month.minusMonths(1))
                            monthDrag = 0f
                        },
                        onHorizontalDrag = { _, delta -> monthDrag += delta }
                    )
                }
        ) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium
            )
            Text("Swipe left or right to change month", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }

        AnalyticsCard {
            CalendarGrid(
                snapshot = snapshot,
                month = month,
                selectedDate = selectedDate,
                today = today,
                onDateSelected = onDateSelected
            )
        }

        AnalyticsCard {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                formatAnalyticsDuration(total),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium
            )
            if (subjectTotals.isNotEmpty()) {
                Text("Subject breakdown", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                PieBreakdown(subjectTotals, subjects, showLeaders = true)
            } else {
                Text("No subjects recorded for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        AnalyticsCard {
            Text("Hourly activity", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            HourlyActivity(hourly)
        }

        if (taskSnapshot != null || selectedDate.isBefore(today)) {
            AnalyticsCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tasks", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    if (taskSnapshot?.locked == true) {
                        Text("Locked", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
                if (taskRows.isEmpty()) {
                    Text("No tasks recorded for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    taskRows.forEach { task ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(if (task.completed) "✓" else "○", color = if (task.completed) Color(0xFF85C89B) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(task.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CalendarGrid(
    snapshot: StudyAnalyticsSnapshot,
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val startOffset = month.atDay(1).dayOfWeek.value - 1
    val count = ((startOffset + month.lengthOfMonth() + 6) / 7) * 7
    val dates = (0 until count).map { index ->
        val day = index - startOffset + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
    val maxDay = snapshot.daily.filterKeys { it.startsWith(month.toString()) }.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    var waveStarted by remember(month) { mutableStateOf(false) }
    LaunchedEffect(month) {
        delay(35L)
        waveStarted = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        dates.chunked(7).forEachIndexed { week, weekDates ->
            Row(Modifier.fillMaxWidth()) {
                weekDates.forEachIndexed { column, date ->
                    if (date == null || date > today) {
                        Spacer(Modifier.weight(1f).height(58.dp))
                    } else {
                        val amount = snapshot.daily[date.toString()] ?: 0L
                        val selected = date == selectedDate
                        val cellScale by animateFloatAsState(
                            targetValue = if (waveStarted) 1f else 0.78f,
                            animationSpec = tween(
                                durationMillis = 420,
                                delayMillis = week * 65 + column * 28,
                                easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                            ),
                            label = "calendar-day-$date"
                        )
                        val level = when {
                            amount <= 0L -> 0
                            amount <= maxDay / 4L -> 1
                            amount <= maxDay / 2L -> 2
                            amount <= maxDay * 3L / 4L -> 3
                            else -> 4
                        }
                        val background = when (level) {
                            0 -> Color.Transparent
                            1 -> Color(0xFF2E473D)
                            2 -> Color(0xFF316C52)
                            3 -> Color(0xFF3F9469)
                            else -> Color(0xFF62B982)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                                .graphicsLayer {
                                    scaleX = cellScale
                                    scaleY = cellScale
                                    alpha = cellScale
                                }
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (selected) Color(0xFF49405B) else Color.Transparent)
                                .noRippleClickable { onDateSelected(date) }
                                .padding(top = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (amount >= 60_000L) {
                                Text(
                                    formatHhMm(amount),
                                    color = if (selected) Color(0xFFE8DFFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeatmap(daily: Map<String, Long>, today: LocalDate) {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .withDayOfYear(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val totalDays = ChronoUnit.DAYS.between(start, today).toInt() + 1
    val weeks = (totalDays + 6) / 7
    val yearPrefix = today.year.toString() + "-"
    val maximum = daily.filterKeys { it.startsWith(yearPrefix) }.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState()
    LaunchedEffect(today, daily.size) {
        delay(100L)
        scrollState.scrollTo(scrollState.maxValue)
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(weeks) { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { weekday ->
                    val date = start.plusDays((week * 7 + weekday).toLong())
                    if (date <= today) {
                        val amount = daily[date.toString()] ?: 0L
                        val shade = when {
                            amount <= 0L -> Color(0xFF30343B)
                            amount <= maximum / 4L -> Color(0xFF355243)
                            amount <= maximum / 2L -> Color(0xFF477E60)
                            amount <= maximum * 3L / 4L -> Color(0xFF67A979)
                            else -> Color(0xFF8CCB98)
                        }
                        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(shade))
                    } else {
                        Spacer(Modifier.size(11.dp))
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Jan", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        Text(today.format(DateTimeFormatter.ofPattern("MMM d")), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

@Composable
private fun PieBreakdown(
    totals: Map<String, Long>,
    subjects: List<StudySubject>,
    showLeaders: Boolean
) {
    val entries = totals.entries.filter { it.value > 0L }.sortedByDescending { it.value }
    val total = entries.sumOf { it.value }.coerceAtLeast(1L)
    val colorFor: (String) -> Color = { name ->
        val index = subjects.firstOrNull { it.name == name }?.color ?: 0
        Color(StudySyncManager.subjectPalette.getOrElse(index) { StudySyncManager.subjectPalette[0] })
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(Modifier.size(if (showLeaders) 152.dp else 132.dp)) {
            var start = -90f
            entries.forEachIndexed { index, entry ->
                val sweep = entry.value.toFloat() / total.toFloat() * 360f
                val colour = colorFor(entry.key)
                drawArc(colour, start, sweep, useCenter = true)
                if (showLeaders) {
                    val mid = Math.toRadians((start + sweep / 2f).toDouble())
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val outer = size.minDimension * 0.47f
                    val end = androidx.compose.ui.geometry.Offset(
                        center.x + cos(mid).toFloat() * outer,
                        center.y + sin(mid).toFloat() * outer
                    )
                    val elbow = androidx.compose.ui.geometry.Offset(
                        center.x + cos(mid).toFloat() * outer * 1.1f,
                        center.y + sin(mid).toFloat() * outer * 1.1f
                    )
                    drawLine(Color(0xFFB7B0C6), end, elbow, strokeWidth = 1.5.dp.toPx())
                }
                start += sweep
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.take(8).forEach { entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colorFor(entry.key)))
                    Column(Modifier.weight(1f)) {
                        Text(entry.key, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, maxLines = 1)
                        Text(formatHhMm(entry.value), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }
    }
    if (showLeaders && entries.size > 1) {
        Text(
            "Subject sections are linked to the legend by matching colours.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun HourlyActivity(hours: Map<Int, Long>) {
    val maximum = (hours.values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    Row(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (0..23).forEach { hour ->
            val amount = hours[hour] ?: 0L
            val target = if (amount <= 0L) 0.02f else (amount / maximum).coerceIn(0.03f, 1f)
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(
                    480,
                    delayMillis = hour * 16,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                ),
                label = "hour-bar-$hour"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height((98f * animated).dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (amount > 0L) Color(0xFF9B82C7) else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

@Composable
private fun AnalyticsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF22252B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.035f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content
        )
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text(label, color = Color(0xFFAEB3BE), fontSize = 11.sp)
        Text(value, color = Color(0xFFF4F4F6), fontSize = 21.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatAnalyticsDuration(milliseconds: Long): String {
    val minutes = milliseconds.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return if (hours > 0L) "${hours}h ${remaining}m" else "${remaining}m"
}

private fun formatHhMm(milliseconds: Long): String {
    val minutes = milliseconds.coerceAtLeast(0L) / 60_000L
    return "${(minutes / 60L).toString().padStart(2, '0')}:${(minutes % 60L).toString().padStart(2, '0')}"
}
