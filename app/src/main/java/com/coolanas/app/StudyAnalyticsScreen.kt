package com.coolanas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

private enum class AnalyticsSection { Monthly, Calendar }

@Composable
fun StudyAnalyticsScreen(
    snapshot: StudyAnalyticsSnapshot,
    subjects: List<StudySubject>,
    darkMode: Boolean,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    var section by remember { mutableStateOf(AnalyticsSection.Monthly) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val monthKey = month.toString()
    val monthValues = (1..month.lengthOfMonth()).map { day ->
        val key = monthKey + "-" + day.toString().padStart(2, '0')
        max(0L, snapshot.daily[key] ?: 0L)
    }
    val monthTotal = monthValues.sum()
    val daysStudied = monthValues.count { it > 0L }
    val average = if (month.lengthOfMonth() > 0) monthTotal / month.lengthOfMonth() else 0L
    val bestDay = monthValues.maxOrNull() ?: 0L
    val subjectTotals = linkedMapOf<String, Long>()
    snapshot.dailySubjects.forEach { (date, entries) ->
        if (date.startsWith("$monthKey-")) {
            entries.forEach { (name, duration) ->
                subjectTotals[name] = (subjectTotals[name] ?: 0L) + duration
            }
        }
    }
    val maxSubject = subjectTotals.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val dailyKey = selectedDate.toString()
    val selectedTotal = snapshot.daily[dailyKey] ?: 0L
    val selectedSubjects = snapshot.dailySubjects[dailyKey].orEmpty()
        .filterValues { it > 0L }
        .toList()
        .sortedByDescending { it.second }
    val selectedHours = snapshot.dailyHours[dailyKey].orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(42.dp).clickable(onClick = onBack),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp)
                    }
                }
                Column {
                    Text("Analytics", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
                    Text(
                        "Your study activity",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsTab("Monthly", section == AnalyticsSection.Monthly, Modifier.weight(1f)) {
                section = AnalyticsSection.Monthly
            }
            AnalyticsTab("Calendar", section == AnalyticsSection.Calendar, Modifier.weight(1f)) {
                section = AnalyticsSection.Calendar
            }
        }

        if (loading && snapshot.daily.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && snapshot.daily.isEmpty()) {
            AnalyticsCard {
                Text("Analytics couldn't load", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                TextButton(onClick = onRefresh) { Text("Try again") }
            }
        } else if (section == AnalyticsSection.Monthly) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                    TextButton(
                        onClick = { month = month.plusMonths(1) },
                        enabled = month < YearMonth.from(today)
                    ) { Text("›") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("Study time", formatAnalyticsDuration(monthTotal), Modifier.weight(1f))
                MetricCard("Days studied", "$daysStudied", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("Daily average", formatAnalyticsDuration(average), Modifier.weight(1f))
                MetricCard("Best day", formatAnalyticsDuration(bestDay), Modifier.weight(1f))
            }
            AnalyticsCard {
                Text("Daily study time", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Text(
                    "Recorded time for each day of the month",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                MonthlyBars(monthValues)
            }
            AnalyticsCard {
                Text("Study activity this year", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Text(
                    "Each square represents one day",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                YearHeatmap(snapshot.daily, today)
            }
            AnalyticsCard {
                Text("Subject breakdown", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                if (subjectTotals.isEmpty()) {
                    Text("No subject time recorded this month.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    subjectTotals.entries.sortedByDescending { it.value }.forEach { (name, duration) ->
                        SubjectProgress(name, duration, maxSubject, subjects)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        month = month.minusMonths(1)
                        selectedDate = month.atDay(1)
                    }) { Text("‹") }
                    TextButton(
                        onClick = {
                            month = month.plusMonths(1)
                            selectedDate = month.atDay(1)
                        },
                        enabled = month < YearMonth.from(today)
                    ) { Text("›") }
                }
            }
            AnalyticsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
                repeat(count / 7) { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(7) { weekday ->
                            val day = week * 7 + weekday - offset + 1
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                val amount = snapshot.daily[date.toString()] ?: 0L
                                val isSelected = date == selectedDate
                                val intensity = when {
                                    amount <= 0L -> MaterialTheme.colorScheme.surfaceVariant
                                    amount < 30 * 60 * 1000L -> Color(0xFFB8D5AE)
                                    amount < 90 * 60 * 1000L -> Color(0xFF80B97A)
                                    amount < 180 * 60 * 1000L -> Color(0xFF4F955C)
                                    else -> Color(0xFF286C43)
                                }
                                Surface(
                                    modifier = Modifier.weight(1f).height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedDate = date },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else intensity
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "$day",
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF243024),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f).height(42.dp))
                            }
                        }
                    }
                }
            }
            AnalyticsCard {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Text(formatAnalyticsDuration(selectedTotal), color = MaterialTheme.colorScheme.primary, fontSize = 25.sp)
                Text("Subject breakdown", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                if (selectedSubjects.isEmpty()) {
                    Text("No subjects recorded for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    selectedSubjects.forEach { (name, duration) ->
                        SubjectProgress(name, duration, max(1L, selectedTotal), subjects)
                    }
                }
                Text("Hourly activity", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                HourlyBars(selectedHours)
            }
        }

        if (snapshot.legacyImportPending) {
            Text("Restoring older analytics data from the cloud archive.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        } else if (snapshot.mirrorPending) {
            Text("Your analytics are saved. The JSON archive mirror will retry automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        if (loading) {
            Text("Refreshing…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        if (error != null && snapshot.daily.isNotEmpty()) {
            Text("Showing the last loaded analytics. Refresh failed: $error", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AnalyticsTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(42.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AnalyticsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp)
        }
    }
}

@Composable
private fun MonthlyBars(values: List<Long>) {
    val maximum = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val bar = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(142.dp)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            val barWidth = slot * 0.62f
            val baseline = size.height - 2.dp.toPx()
            values.forEachIndexed { index, amount ->
                val height = (amount.toFloat() / maximum) * (size.height - 8.dp.toPx())
                val x = index * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = if (amount > 0L) bar else axis.copy(alpha = 0.12f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, baseline - height),
                    size = androidx.compose.ui.geometry.Size(barWidth, height.coerceAtLeast(2.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(1, 5, 10, 15, 20, 25, values.size).distinct().forEach {
                Text("$it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
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
    val maximum = daily.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(weeks) { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { weekday ->
                    val date = start.plusDays((week * 7 + weekday).toLong())
                    if (date <= today) {
                        val amount = daily[date.toString()] ?: 0L
                        val shade = when {
                            amount <= 0L -> MaterialTheme.colorScheme.surfaceVariant
                            amount <= maximum / 4L -> Color(0xFFD6E8CF)
                            amount <= maximum / 2L -> Color(0xFFA8D09A)
                            amount <= maximum * 3L / 4L -> Color(0xFF70AD72)
                            else -> Color(0xFF347E50)
                        }
                        Box(
                            Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(shade)
                        )
                    } else {
                        Spacer(Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectProgress(
    name: String,
    duration: Long,
    maximum: Long,
    subjects: List<StudySubject>
) {
    val colorIndex = subjects.firstOrNull { it.name == name }?.color ?: 0
    val color = Color(StudySyncManager.subjectPalette.getOrElse(colorIndex) { StudySyncManager.subjectPalette[0] })
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Text(formatAnalyticsDuration(duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(
                MaterialTheme.colorScheme.surfaceVariant,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
            drawRoundRect(
                color,
                size = androidx.compose.ui.geometry.Size(size.width * (duration.toFloat() / maximum.toFloat()).coerceIn(0f, 1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
        }
    }
}

@Composable
private fun HourlyBars(hours: Map<Int, Long>) {
    val maximum = (hours.values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    Row(
        modifier = Modifier.fillMaxWidth().height(104.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (0..23).forEach { hour ->
            val amount = hours[hour] ?: 0L
            val height = if (amount <= 0L) 3.dp else (amount / maximum * 88f).dp.coerceAtLeast(4.dp)
            Box(
                modifier = Modifier.weight(1f).height(height)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (amount > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

private fun formatAnalyticsDuration(milliseconds: Long): String {
    val minutes = (milliseconds.coerceAtLeast(0L) / 60_000L)
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return if (hours > 0L) "${hours}h ${remaining}m" else "${remaining}m"
}
