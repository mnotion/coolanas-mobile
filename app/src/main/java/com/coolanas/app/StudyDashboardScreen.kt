package com.coolanas.app

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.withContext
import kotlin.math.max

data class CoolanasAccountProfile(
    val name: String = "Account",
    val email: String = "",
    val googleAvatarUrl: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudyDashboardScreen(
    userId: String,
    repository: StudySyncManager,
    syncVersion: Int,
    darkMode: Boolean,
    accountProfile: CoolanasAccountProfile,
    onThemeChanged: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val cached = remember(userId) { repository.cachedSubjects(userId) }
    var subjects by remember(userId) {
        mutableStateOf(cached ?: StudySyncManager.defaultSubjects)
    }
    var subjectsEditable by remember(userId) { mutableStateOf(true) }
    var selectedSubject by remember(userId) {
        mutableStateOf(repository.selectedSubject(userId))
    }
    var timer by remember(userId) { mutableStateOf(repository.timerState(userId)) }
    var now by remember(userId) { mutableLongStateOf(System.currentTimeMillis()) }
    var serverClockOffsetMs by remember(userId) { mutableLongStateOf(0L) }
    var stopwatchReady by remember(userId) { mutableStateOf(false) }
    var stopwatchBusy by remember(userId) { mutableStateOf(false) }
    var stopwatchError by remember(userId) { mutableStateOf<String?>(null) }
    var avatar by remember(userId) { mutableStateOf<Bitmap?>(null) }
    var entryVisible by remember(userId) { mutableStateOf(false) }
    var accountMenuOpen by remember(userId) { mutableStateOf(false) }
    var subjectMenu by remember(userId) { mutableStateOf<String?>(null) }
    var editorSubject by remember(userId) { mutableStateOf<StudySubject?>(null) }
    var showSubjectEditor by remember(userId) { mutableStateOf(false) }
    var showAnalytics by remember(userId) { mutableStateOf(false) }
    var analyticsSnapshot by remember(userId) { mutableStateOf(StudyAnalyticsSnapshot()) }
    var analyticsLoading by remember(userId) { mutableStateOf(false) }
    var analyticsError by remember(userId) { mutableStateOf<String?>(null) }
    var analyticsRefreshVersion by remember(userId) { mutableLongStateOf(0L) }

    val timerRunning = timer.startedAtMs != null
    val timerPaused = !timerRunning && timer.elapsedMs > 0L
    val pageBackground = when {
        timerRunning -> Color(0xFF080808)
        timerPaused -> Color(0xFFE9D99A)
        darkMode -> Color(0xFF17191D)
        else -> Color(0xFFDEDEDE)
    }
    val animatedBackground by animateColorAsState(
        targetValue = pageBackground,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "timer-page-background"
    )
    val timerScale by animateFloatAsState(
        targetValue = if (timerRunning) 1.04f else 1f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "timer-focus-scale"
    )
    val cardBackground = if (darkMode && !timerRunning && !timerPaused) {
        Color(0xFF24262B)
    } else {
        Color(0xFFF7F7F7)
    }
    val mainText = if (darkMode && !timerRunning && !timerPaused) {
        Color(0xFFF4F4F4)
    } else {
        Color(0xFF171717)
    }
    val secondaryText = if (darkMode && !timerRunning && !timerPaused) {
        Color(0xFFB7BAC1)
    } else {
        Color(0xFF666666)
    }
    val activeSubjectName = timer.runningSubject ?: selectedSubject
    val activeSubjectColour = Color(
        StudySyncManager.subjectPalette[
            subjects.firstOrNull { it.name == activeSubjectName }?.color ?: 0
        ]
    )

    LaunchedEffect(userId) {
        entryVisible = false
        delay(65L)
        entryVisible = true
        onThemeChanged(repository.cachedTheme(userId) == "dark")
        try {
            val terminal = withContext(Dispatchers.IO) {
                repository.accountStopwatchAction(userId, "get")
            }
            timer = terminal.timer
            serverClockOffsetMs = terminal.serverClockOffsetMs
            stopwatchReady = true
            stopwatchError = null
        } catch (error: Exception) {
            stopwatchError = error.message ?: "Your account stopwatch could not be loaded."
        }
        val loadedSubjects = withContext(Dispatchers.IO) { repository.loadSubjects(userId) }
        subjects = loadedSubjects.subjects
        subjectsEditable = loadedSubjects.editable
        if (selectedSubject != null && subjects.none { it.name == selectedSubject }) {
            selectedSubject = null
            repository.selectSubject(userId, null)
        }
        val cloudDarkMode = withContext(Dispatchers.IO) { repository.loadTheme(userId) }
        onThemeChanged(cloudDarkMode)
        avatar = withContext(Dispatchers.IO) {
            repository.loadProfileAvatar(userId, accountProfile.googleAvatarUrl)
        }
        withContext(Dispatchers.IO) { repository.syncPending(userId) }
    }

    LaunchedEffect(userId, syncVersion) {
        if (syncVersion > 0) {
            try {
                val terminal = withContext(Dispatchers.IO) {
                    repository.accountStopwatchAction(userId, "get")
                }
                timer = terminal.timer
                serverClockOffsetMs = terminal.serverClockOffsetMs
                stopwatchReady = true
                stopwatchError = null
            } catch (error: Exception) {
                stopwatchError = error.message ?: "Your account stopwatch could not refresh."
            }
            val loadedSubjects = withContext(Dispatchers.IO) { repository.loadSubjects(userId) }
            subjects = loadedSubjects.subjects
            subjectsEditable = loadedSubjects.editable
            val cloudDarkMode = withContext(Dispatchers.IO) { repository.loadTheme(userId) }
            onThemeChanged(cloudDarkMode)
            avatar = withContext(Dispatchers.IO) {
                repository.loadProfileAvatar(userId, accountProfile.googleAvatarUrl)
            }
            withContext(Dispatchers.IO) { repository.syncPending(userId) }
        }
    }

    // The database timestamp remains authoritative. This loop only animates the visible clock.
    LaunchedEffect(userId) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250L)
        }
    }

    // Realtime refreshes the same account-owned terminal used by the website and Guild.
    // A 15-second get is retained as a recovery fallback for changes missed while offline.
    LaunchedEffect(userId) {
        val channel = SupabaseClientProvider.client.realtime.channel(
            "coolanas-account-stopwatch-$userId"
        )
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "coolanas_account_stopwatches"
            filter = "user_id=eq.$userId"
        }
        val observer = launch {
            changes.collect {
                try {
                    val latest = withContext(Dispatchers.IO) {
                        repository.accountStopwatchAction(userId, "get")
                    }
                    timer = latest.timer
                    serverClockOffsetMs = latest.serverClockOffsetMs
                    stopwatchReady = true
                    stopwatchError = null
                } catch (error: Exception) {
                    stopwatchError = error.message ?: "Could not refresh the shared stopwatch."
                }
            }
        }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            val initial = withContext(Dispatchers.IO) {
                repository.accountStopwatchAction(userId, "get")
            }
            timer = initial.timer
            serverClockOffsetMs = initial.serverClockOffsetMs
            stopwatchReady = true
            stopwatchError = null
            while (true) {
                delay(15_000L)
                val latest = withContext(Dispatchers.IO) {
                    repository.accountStopwatchAction(userId, "get")
                }
                timer = latest.timer
                serverClockOffsetMs = latest.serverClockOffsetMs
                stopwatchReady = true
                stopwatchError = null
            }
        } catch (error: Exception) {
            stopwatchError = error.message ?: "Live stopwatch updates are unavailable."
        } finally {
            observer.cancel()
            SupabaseClientProvider.client.realtime.removeChannel(channel)
        }
    }

    LaunchedEffect(showAnalytics, userId, syncVersion, analyticsRefreshVersion) {
        if (showAnalytics) {
            while (true) {
                analyticsLoading = analyticsSnapshot.daily.isEmpty()
                analyticsError = null
                try {
                    analyticsSnapshot = withContext(Dispatchers.IO) {
                        repository.loadAnalytics(userId)
                    }
                } catch (error: Exception) {
                    analyticsError = error.message ?: "Could not load your analytics."
                } finally {
                    analyticsLoading = false
                }
                delay(15_000L)
            }
        }
    }

    fun syncInBackground() {
        scope.launch {
            withContext(Dispatchers.IO) { repository.syncPending(userId) }
        }
    }

    fun runStopwatchAction(action: String) {
        if (!stopwatchReady || stopwatchBusy) return
        if (action == "start" && selectedSubject.isNullOrBlank()) {
            subjectMenu = null
            stopwatchError = "Select a subject before starting your study session."
            return
        }
        scope.launch {
            stopwatchBusy = true
            stopwatchError = null
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.accountStopwatchAction(
                        userId = userId,
                        action = action,
                        subjectForStart = selectedSubject
                    )
                }
                timer = result.timer
                serverClockOffsetMs = result.serverClockOffsetMs
                if (action == "pause" || action == "reset") syncInBackground()
            } catch (error: Exception) {
                stopwatchError = error.message ?: "Your personal stopwatch could not sync. Try again."
            } finally {
                stopwatchBusy = false
            }
        }
    }

    fun saveSubjectChanges(updated: List<StudySubject>) {
        subjects = updated
        if (selectedSubject != null && updated.none { it.name == selectedSubject }) {
            selectedSubject = null
            repository.selectSubject(userId, null)
        }
        repository.saveSubjects(userId, updated)
        syncInBackground()
    }

    fun setAccountTheme(nextDarkMode: Boolean) {
        onThemeChanged(nextDarkMode)
        repository.saveTheme(userId, nextDarkMode)
        syncInBackground()
    }

    val glowTransition = rememberInfiniteTransition(label = "timer-glow")
    val greenDrift by glowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "green-drift"
    )
    val redPulse by glowTransition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(8_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "red-pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBackground),
        contentAlignment = Alignment.Center
    ) {
        if (showAnalytics) {
            StudyAnalyticsScreen(
                snapshot = analyticsSnapshot,
                subjects = subjects,
                timer = timer,
                serverNowMillis = now + serverClockOffsetMs,
                activeSubject = timer.runningSubject ?: selectedSubject ?: "Unassigned",
                darkMode = darkMode,
                loading = analyticsLoading,
                error = analyticsError,
                onBack = { showAnalytics = false },
                onRefresh = { analyticsRefreshVersion++ }
            )
        } else {
        if (timerRunning || !selectedSubject.isNullOrBlank()) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = max(size.width, size.height) * 0.9f
                if (timerRunning) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8F1424).copy(alpha = redPulse * 0.48f),
                                Color(0xFF5C0812).copy(alpha = redPulse * 0.23f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.48f),
                            radius = radius
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF239451).copy(alpha = 0.35f),
                                Color(0xFF12693A).copy(alpha = 0.17f),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.24f + greenDrift * 0.52f),
                                size.height * (0.34f + (1f - greenDrift) * 0.34f)
                            ),
                            radius = radius * 0.76f
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF26965A).copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.78f - greenDrift * 0.3f),
                                size.height * (0.72f - greenDrift * 0.24f)
                            ),
                            radius = radius * 0.58f
                        )
                    )
                }
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            activeSubjectColour.copy(alpha = if (timerRunning) 0.26f else 0.22f),
                            activeSubjectColour.copy(alpha = 0.09f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.78f),
                        radius = radius * 0.58f
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = entryVisible && !timerRunning,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 18.dp, start = 20.dp),
            enter = fadeIn(tween(750, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.86f, animationSpec = tween(850, easing = FastOutSlowInEasing)) +
                slideInVertically(initialOffsetY = { -18 }, animationSpec = tween(850, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(320, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.86f, animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                slideOutVertically(targetOffsetY = { -12 }, animationSpec = tween(320, easing = FastOutSlowInEasing))
        ) {
            AnalyticsNavigationButton(
                enabled = stopwatchReady,
                onClick = { showAnalytics = true }
            )
        }

        AnimatedVisibility(
            visible = entryVisible && !timerRunning,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 20.dp),
            enter = fadeIn(tween(750, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.86f, animationSpec = tween(850, easing = FastOutSlowInEasing)) +
                slideInVertically(initialOffsetY = { -18 }, animationSpec = tween(850, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(320, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.86f, animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                slideOutVertically(targetOffsetY = { -12 }, animationSpec = tween(320, easing = FastOutSlowInEasing))
        ) {
            Box {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(5.dp, CircleShape)
                        .combinedClickable(
                            onClick = { accountMenuOpen = true }
                        ),
                    shape = CircleShape,
                    color = if (darkMode) Color(0xFF303238) else Color(0xFFEEEEEE),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (darkMode) Color(0xFF555861) else Color(0xFFD5D5D5)
                    )
                ) {
                    if (avatar != null) {
                        androidx.compose.foundation.Image(
                            bitmap = avatar!!.asImageBitmap(),
                            contentDescription = "Account menu",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = accountProfile.name.trim().firstOrNull()?.uppercase() ?: "?",
                                color = mainText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = accountMenuOpen,
                    onDismissRequest = { accountMenuOpen = false }
                ) {
                    Column(
                        modifier = Modifier.width(254.dp).padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            accountProfile.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        if (accountProfile.email.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                accountProfile.email,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(1.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ) {}
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dark mode", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                            Switch(
                                checked = darkMode,
                                onCheckedChange = {
                                    setAccountTheme(it)
                                    accountMenuOpen = false
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(1.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ) {}
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                accountMenuOpen = false
                                onSignOut()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = entryVisible && !showAnalytics,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(900, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.88f, animationSpec = tween(950, easing = FastOutSlowInEasing)) +
                slideInVertically(initialOffsetY = { 22 }, animationSpec = tween(950, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(320, easing = FastOutSlowInEasing))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = if (timerRunning) 490.dp else 390.dp)
                    .graphicsLayer {
                        scaleX = timerScale
                        scaleY = timerScale
                    }
                    .animateContentSize(tween(550, easing = FastOutSlowInEasing)),
                shape = RoundedCornerShape(17.dp),
                color = if (timerRunning || timerPaused) Color(0xFFFDFDFD) else cardBackground
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = if (timerRunning) 20.dp else 14.dp,
                        vertical = if (timerRunning) 18.dp else 13.dp
                    )
                ) {
                    Text(
                        text = formatStudyTime(timer.elapsedAt(now + serverClockOffsetMs)),
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = if (timerRunning) 44.sp else 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1.8).sp,
                        color = Color(0xFF171717),
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (timerRunning) 54.dp else 46.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimerControlButton(
                            contentDescription = "Reset timer",
                            size = if (timerRunning) 42.dp else 38.dp,
                            enabled = stopwatchReady && !stopwatchBusy,
                            background = Color.White,
                            foreground = Color(0xFF555555),
                            onClick = {
                                accountMenuOpen = false
                                subjectMenu = null
                                runStopwatchAction("reset")
                            },
                            icon = TimerControlIcon.Reset
                        )
                        TimerControlButton(
                            contentDescription = if (timerRunning) "Pause timer" else "Start timer",
                            size = if (timerRunning) 54.dp else 46.dp,
                            background = if (timerPaused) Color(0xFFD6B84E) else Color(0xFF171717),
                            foreground = if (timerPaused) Color(0xFF17140A) else Color.White,
                            onClick = {
                                runStopwatchAction(if (timerRunning) "pause" else "start")
                            },
                            icon = if (timerRunning) TimerControlIcon.Pause else TimerControlIcon.Play,
                            enabled = stopwatchReady && !stopwatchBusy && subjectsEditable &&
                                (timerRunning || !selectedSubject.isNullOrBlank())
                        )
                    }
                }
            }
        }

        if (!stopwatchError.isNullOrBlank()) {
            Text(
                text = stopwatchError.orEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 22.dp, vertical = 18.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        val shownSubject = activeSubjectName
        AnimatedVisibility(
            visible = entryVisible && !shownSubject.isNullOrBlank() && (timerRunning || timerPaused),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = if (timerPaused) 73.dp else 72.dp),
            enter = fadeIn(tween(500, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(500, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(300, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        ) {
            val colourIndex = subjects.firstOrNull { it.name == shownSubject }?.color ?: 0
            Surface(
                shape = CircleShape,
                color = Color(StudySyncManager.subjectPalette[colourIndex]),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8D8D8))
            ) {
                Text(
                    text = shownSubject.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFF555555),
                    fontSize = 12.sp
                )
            }
        }

        AnimatedVisibility(
            visible = entryVisible && !timerRunning,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = if (timerPaused) 160.dp else 68.dp),
            enter = fadeIn(tween(650, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.88f, animationSpec = tween(700, easing = FastOutSlowInEasing)) +
                slideInVertically(initialOffsetY = { 16 }, animationSpec = tween(700, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(320, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.88f, animationSpec = tween(320, easing = FastOutSlowInEasing)) +
                slideOutVertically(targetOffsetY = { 12 }, animationSpec = tween(320, easing = FastOutSlowInEasing))
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(subjects, key = { it.name }) { subject ->
                    Box {
                        val chosen = selectedSubject == subject.name
                        Surface(
                            modifier = Modifier.combinedClickable(
                                enabled = subjectsEditable,
                                onClick = {
                                    selectedSubject = subject.name
                                    repository.selectSubject(userId, subject.name)
                                },
                                onDoubleClick = {
                                    subjectMenu = subject.name
                                },
                                onLongClick = {
                                    subjectMenu = subject.name
                                }
                            ),
                            shape = CircleShape,
                            color = if (chosen || !darkMode) {
                                Color(StudySyncManager.subjectPalette[subject.color])
                            } else {
                                Color(0xFF303238)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                if (chosen) 2.dp else 1.dp,
                                if (chosen) Color(0xFF333333) else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(
                                text = subject.name,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                color = if (chosen || !darkMode) Color(0xFF444444) else Color(0xFFEAEAEA),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = subjectMenu == subject.name,
                            onDismissRequest = { subjectMenu = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    subjectMenu = null
                                    editorSubject = subject
                                    showSubjectEditor = true
                                }
                            )
                            Text(
                                "Colour",
                                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudySyncManager.subjectPalette.forEachIndexed { index, paletteColour ->
                                    Surface(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .combinedClickable(
                                                onClick = {
                                                    val updated = subjects.map {
                                                        if (it.name == subject.name) it.copy(color = index) else it
                                                    }
                                                    saveSubjectChanges(updated)
                                                    subjectMenu = null
                                                }
                                            ),
                                        shape = CircleShape,
                                        color = Color(paletteColour),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (subject.color == index) 2.dp else 1.dp,
                                            if (subject.color == index) Color(0xFF333333) else Color(0xFFBBBBBB)
                                        )
                                    ) {}
                                }
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    saveSubjectChanges(subjects.filterNot { it.name == subject.name })
                                    subjectMenu = null
                                }
                            )
                        }
                    }
                }
                item {
                    Surface(
                        modifier = Modifier
                            .size(30.dp)
                            .combinedClickable(
                                onClick = {
                                    editorSubject = null
                                    showSubjectEditor = true
                                }
                            ),
                        shape = CircleShape,
                        color = if (darkMode) Color(0xFF303238) else Color(0xFFFDFDFD),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (darkMode) Color(0xFF62656D) else Color(0xFFBBBBBB)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        }
    }

    if (showSubjectEditor && !showAnalytics) {
        SubjectEditorDialog(
            subject = editorSubject,
            darkMode = darkMode,
            onDismiss = {
                showSubjectEditor = false
                editorSubject = null
            },
            onSave = { updated ->
                val old = editorSubject
                val duplicate = subjects.any {
                    it.name.equals(updated.name, ignoreCase = true) && it.name != old?.name
                }
                if (!duplicate) {
                    val current = subjects.toMutableList()
                    if (old == null) {
                        if (current.size < 40) {
                            current.add(updated)
                            selectedSubject = updated.name
                            repository.selectSubject(userId, updated.name)
                            saveSubjectChanges(current)
                        }
                    } else {
                        val index = current.indexOfFirst { it.name == old.name }
                        if (index >= 0) {
                            current[index] = updated
                            if (selectedSubject == old.name) {
                                selectedSubject = updated.name
                                repository.selectSubject(userId, updated.name)
                            }
                            saveSubjectChanges(current)
                        }
                    }
                }
                showSubjectEditor = false
                editorSubject = null
            }
        )
    }
}

@Composable
private fun AnalyticsNavigationButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .combinedClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Open analytics" },
        shape = CircleShape,
        color = Color(0xFFF7F7F7),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5D5D5))
    ) {
        Canvas(Modifier.padding(11.dp)) {
            val left = size.width * 0.08f
            val bottom = size.height * 0.9f
            val path = Path().apply {
                moveTo(left, size.height * 0.08f)
                lineTo(left, bottom)
                lineTo(size.width * 0.96f, bottom)
            }
            drawPath(
                path,
                color = Color(0xFF555555),
                style = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
            )
            val trend = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.68f)
                lineTo(size.width * 0.43f, size.height * 0.47f)
                lineTo(size.width * 0.62f, size.height * 0.56f)
                lineTo(size.width * 0.88f, size.height * 0.18f)
            }
            drawPath(
                trend,
                color = Color(0xFF171717),
                style = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
            )
        }
    }
}

private enum class TimerControlIcon { Reset, Play, Pause }

@Composable
private fun TimerControlButton(
    contentDescription: String,
    size: Dp,
    background: Color,
    foreground: Color,
    icon: TimerControlIcon,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(size)
            .combinedClickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = background.copy(alpha = if (enabled) 1f else 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (icon == TimerControlIcon.Play || icon == TimerControlIcon.Pause) background else Color(0xFFDADADA)
        )
    ) {
        Canvas(Modifier.padding(if (icon == TimerControlIcon.Play) 14.dp else 12.dp)) {
            val canvasSize = this.size
            when (icon) {
                TimerControlIcon.Reset -> drawResetIcon(foreground)
                TimerControlIcon.Play -> {
                    val path = Path().apply {
                        moveTo(canvasSize.width * 0.22f, canvasSize.height * 0.08f)
                        lineTo(canvasSize.width * 0.88f, canvasSize.height * 0.5f)
                        lineTo(canvasSize.width * 0.22f, canvasSize.height * 0.92f)
                        close()
                    }
                    drawPath(path, foreground)
                }
                TimerControlIcon.Pause -> {
                    drawRoundRect(
                        foreground,
                        topLeft = Offset(canvasSize.width * 0.16f, canvasSize.height * 0.08f),
                        size = androidx.compose.ui.geometry.Size(canvasSize.width * 0.24f, canvasSize.height * 0.84f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                    drawRoundRect(
                        foreground,
                        topLeft = Offset(canvasSize.width * 0.60f, canvasSize.height * 0.08f),
                        size = androidx.compose.ui.geometry.Size(canvasSize.width * 0.24f, canvasSize.height * 0.84f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawResetIcon(colour: Color) {
    val strokeWidth = size.minDimension * 0.105f
    drawArc(
        color = colour,
        startAngle = -48f,
        sweepAngle = 302f,
        useCenter = false,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
        size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.76f)
    )
    val arrow = Path().apply {
        moveTo(size.width * 0.57f, size.height * 0.04f)
        lineTo(size.width * 0.96f, size.height * 0.07f)
        lineTo(size.width * 0.77f, size.height * 0.37f)
        close()
    }
    drawPath(arrow, colour)
}

@Composable
private fun SubjectEditorDialog(
    subject: StudySubject?,
    darkMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (StudySubject) -> Unit
) {
    var name by remember(subject) { mutableStateOf(subject?.name ?: "") }
    var colorIndex by remember(subject) { mutableStateOf(subject?.color ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (subject == null) "Add subject" else "Rename subject") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    label = { Text("Subject name") }
                )
                Spacer(Modifier.height(14.dp))
                Text("Colour", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudySyncManager.subjectPalette.forEachIndexed { index, colour ->
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .combinedClickable(onClick = { colorIndex = index }),
                            shape = CircleShape,
                            color = Color(colour),
                            border = androidx.compose.foundation.BorderStroke(
                                if (colorIndex == index) 2.dp else 1.dp,
                                if (colorIndex == index) Color(0xFF333333) else Color(0xFFBBBBBB)
                            )
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(StudySubject(name.trim(), colorIndex)) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = if (darkMode) Color(0xFF292B31) else Color(0xFFFFFBFF),
        titleContentColor = if (darkMode) Color(0xFFF4F4F4) else Color(0xFF171717),
        textContentColor = if (darkMode) Color(0xFFE2E2E2) else Color(0xFF444444)
    )
}

private fun formatStudyTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
