package com.coolanas.app

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateColorAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var avatar by remember(userId) { mutableStateOf<Bitmap?>(null) }
    var entryVisible by remember(userId) { mutableStateOf(false) }
    var accountMenuOpen by remember(userId) { mutableStateOf(false) }
    var subjectMenu by remember(userId) { mutableStateOf<String?>(null) }
    var editorSubject by remember(userId) { mutableStateOf<StudySubject?>(null) }
    var showSubjectEditor by remember(userId) { mutableStateOf(false) }

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
        animationSpec = tween(450),
        label = "timer-page-background"
    )
    val timerScale by animateFloatAsState(
        targetValue = if (timerRunning) 1.04f else 1f,
        animationSpec = tween(350),
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
    val borderColour = if (darkMode && !timerRunning && !timerPaused) {
        Color(0xFF4D5058)
    } else {
        Color(0xFFE5E5E5)
    }

    LaunchedEffect(userId) {
        entryVisible = false
        delay(65L)
        entryVisible = true
        onThemeChanged(repository.cachedTheme(userId) == "dark")
        if (timer.startedAtMs != null) {
            withContext(Dispatchers.IO) { repository.checkpointTimer(userId) }
            timer = repository.timerState(userId)
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

    LaunchedEffect(userId, timer.startedAtMs) {
        var lastCheckpointAt = if (timer.startedAtMs == null) {
            System.currentTimeMillis()
        } else {
            System.currentTimeMillis() - 10_000L
        }
        while (true) {
            now = System.currentTimeMillis()
            if (timer.startedAtMs != null && now - lastCheckpointAt >= 10_000L) {
                val checkpointTime = now
                withContext(Dispatchers.IO) {
                    repository.checkpointTimer(userId, checkpointTime)
                    repository.syncPending(userId)
                }
                lastCheckpointAt = checkpointTime
                timer = repository.timerState(userId)
            }
            delay(250L)
        }
    }

    fun syncInBackground() {
        scope.launch {
            withContext(Dispatchers.IO) { repository.syncPending(userId) }
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
        if (timerRunning) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = max(size.width, size.height) * 0.9f
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
        }

        AnimatedVisibility(
            visible = entryVisible && !timerRunning,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 20.dp),
            enter = fadeIn(tween(520)) + scaleIn(initialScale = 0.86f, animationSpec = tween(650)) +
                slideInVertically(initialOffsetY = { -18 }, animationSpec = tween(650)),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.86f, animationSpec = tween(220)) +
                slideOutVertically(targetOffsetY = { -12 }, animationSpec = tween(220))
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
                                Text(
                                    "Syncs across your Coolanas account",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
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
            visible = entryVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(700)) + scaleIn(initialScale = 0.88f, animationSpec = tween(760)) +
                slideInVertically(initialOffsetY = { 22 }, animationSpec = tween(760)),
            exit = fadeOut(tween(180))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = if (timerRunning) 490.dp else 390.dp)
                    .graphicsLayer {
                        scaleX = timerScale
                        scaleY = timerScale
                    }
                    .animateContentSize(tween(350))
                    .shadow(if (timerRunning) 16.dp else 6.dp, RoundedCornerShape(17.dp)),
                shape = RoundedCornerShape(17.dp),
                color = if (timerRunning || timerPaused) Color(0xFFFDFDFD) else cardBackground,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (timerPaused) Color(0xFFD3BD70) else borderColour
                )
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = if (timerRunning) 20.dp else 14.dp,
                        vertical = if (timerRunning) 18.dp else 13.dp
                    )
                ) {
                    Text(
                        text = formatStudyTime(timer.elapsedAt(now)),
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
                            background = Color.White,
                            foreground = Color(0xFF555555),
                            onClick = {
                                timer = repository.resetTimer(userId)
                                accountMenuOpen = false
                                subjectMenu = null
                                syncInBackground()
                            },
                            icon = TimerControlIcon.Reset
                        )
                        TimerControlButton(
                            contentDescription = if (timerRunning) "Pause timer" else "Start timer",
                            size = if (timerRunning) 54.dp else 46.dp,
                            background = if (timerPaused) Color(0xFFD6B84E) else Color(0xFF171717),
                            foreground = if (timerPaused) Color(0xFF17140A) else Color.White,
                            onClick = {
                                timer = if (timerRunning) {
                                    repository.pauseTimer(userId)
                                } else {
                                    repository.startTimer(userId, selectedSubject.orEmpty())
                                }
                                syncInBackground()
                            },
                            icon = if (timerRunning) TimerControlIcon.Pause else TimerControlIcon.Play,
                            enabled = subjectsEditable && (timerRunning || !selectedSubject.isNullOrBlank())
                        )
                    }
                }
            }
        }

        val shownSubject = timer.runningSubject ?: selectedSubject
        AnimatedVisibility(
            visible = entryVisible && !shownSubject.isNullOrBlank() && (timerRunning || timerPaused),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = if (timerPaused) 73.dp else 72.dp),
            enter = fadeIn(tween(320)) + scaleIn(initialScale = 0.9f, animationSpec = tween(320)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.9f, animationSpec = tween(180))
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
            enter = fadeIn(tween(420)) + scaleIn(initialScale = 0.88f, animationSpec = tween(520)) +
                slideInVertically(initialOffsetY = { 16 }, animationSpec = tween(520)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.88f, animationSpec = tween(200)) +
                slideOutVertically(targetOffsetY = { 12 }, animationSpec = tween(200))
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

    if (showSubjectEditor) {
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
            .combinedClickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = background.copy(alpha = if (enabled) 1f else 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (icon == TimerControlIcon.Play || icon == TimerControlIcon.Pause) background else Color(0xFFDADADA)
        )
    ) {
        Canvas(Modifier.padding(if (icon == TimerControlIcon.Play) 14.dp else 12.dp)) {
            when (icon) {
                TimerControlIcon.Reset -> drawResetIcon(foreground)
                TimerControlIcon.Play -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.22f, size.height * 0.08f)
                        lineTo(size.width * 0.88f, size.height * 0.5f)
                        lineTo(size.width * 0.22f, size.height * 0.92f)
                        close()
                    }
                    drawPath(path, foreground)
                }
                TimerControlIcon.Pause -> {
                    drawRoundRect(
                        foreground,
                        topLeft = Offset(size.width * 0.16f, size.height * 0.08f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.84f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                    drawRoundRect(
                        foreground,
                        topLeft = Offset(size.width * 0.60f, size.height * 0.08f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.84f),
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
