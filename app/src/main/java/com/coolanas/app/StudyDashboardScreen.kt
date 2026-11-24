package com.coolanas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudyDashboardScreen(
    userId: String,
    repository: StudySyncManager,
    syncVersion: Int,
    onSignOut: () -> Unit,
    onSyncStatus: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cached = remember(userId) { repository.cachedSubjects(userId) }
    var subjects by remember(userId) {
        mutableStateOf(cached ?: StudySyncManager.defaultSubjects)
    }
    var subjectsEditable by remember(userId) { mutableStateOf(cached != null) }
    var selectedSubject by remember(userId) {
        mutableStateOf(repository.selectedSubject(userId))
    }
    var timer by remember(userId) { mutableStateOf(repository.timerState(userId)) }
    var now by remember(userId) { mutableLongStateOf(System.currentTimeMillis()) }
    var syncLabel by remember(userId) {
        mutableStateOf(if (cached != null) "Saved on this phone" else "Loading your subjects…")
    }
    var editorSubject by remember(userId) { mutableStateOf<StudySubject?>(null) }
    var showAddSubject by remember(userId) { mutableStateOf(false) }

    LaunchedEffect(userId) {
        if (timer.startedAtMs != null) {
            withContext(Dispatchers.IO) { repository.checkpointTimer(userId) }
            timer = repository.timerState(userId)
        }
        val loaded = withContext(Dispatchers.IO) { repository.loadSubjects(userId) }
        subjects = loaded.subjects
        subjectsEditable = loaded.editable
        if (selectedSubject != null && subjects.none { it.name == selectedSubject }) {
            selectedSubject = null
            repository.selectSubject(userId, null)
        }
        val synced = withContext(Dispatchers.IO) { repository.syncPending(userId) }
        syncLabel = if (synced) "Synced with Coolanas" else "Saved on this phone · waiting to sync"
        onSyncStatus(synced)
    }

    LaunchedEffect(userId, syncVersion) {
        if (syncVersion > 0) {
            val loaded = withContext(Dispatchers.IO) { repository.loadSubjects(userId) }
            subjects = loaded.subjects
            subjectsEditable = loaded.editable
            val synced = withContext(Dispatchers.IO) { repository.syncPending(userId) }
            syncLabel = if (synced) "Synced with Coolanas" else "Saved on this phone · waiting to sync"
            onSyncStatus(synced)
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
                }
                lastCheckpointAt = checkpointTime
                val synced = withContext(Dispatchers.IO) { repository.syncPending(userId) }
                syncLabel = if (synced) "Synced with Coolanas" else "Saved on this phone · waiting to sync"
                onSyncStatus(synced)
                timer = repository.timerState(userId)
            }
            delay(250L)
        }
    }

    fun refreshAfterLocalChange() {
        timer = repository.timerState(userId)
    }

    fun syncInBackground() {
        scope.launch {
            val synced = withContext(Dispatchers.IO) { repository.syncPending(userId) }
            syncLabel = if (synced) "Synced with Coolanas" else "Saved on this phone · waiting to sync"
            onSyncStatus(synced)
        }
    }

    fun saveSubjectChanges(updated: List<StudySubject>) {
        subjects = updated
        if (selectedSubject != null && updated.none { it.name == selectedSubject }) {
            selectedSubject = null
            repository.selectSubject(userId, null)
        }
        repository.saveSubjects(userId, updated)
        syncLabel = "Saved on this phone · syncing"
        syncInBackground()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFDEDEDE))
            .padding(horizontal = 22.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Coolanas",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171717)
                )
                TextButton(onClick = onSignOut) {
                    Text("Sign out", color = Color(0xFF555555))
                }
            }

            Spacer(Modifier.height(30.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(if (timer.startedAtMs != null) 18.dp else 6.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = if (timer.startedAtMs != null) Color(0xFFFDFDFD) else Color(0xFFF7F7F7),
                border = BorderStroke(1.dp, if (timer.startedAtMs != null) Color(0xFFE6E6E6) else Color(0xFFE5E5E5))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatStudyTime(timer.elapsedAt(now)),
                        fontSize = if (timer.startedAtMs != null) 43.sp else 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1.5).sp,
                        color = Color(0xFF171717),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimerCircleButton(
                            label = "↻",
                            contentDescription = "Reset timer",
                            background = Color.White,
                            foreground = Color(0xFF555555),
                            size = 42.dp,
                            onClick = {
                                timer = repository.resetTimer(userId)
                                syncInBackground()
                            }
                        )
                        TimerCircleButton(
                            label = if (timer.startedAtMs != null) "Ⅱ" else "▶",
                            contentDescription = if (timer.startedAtMs != null) "Pause timer" else "Start timer",
                            background = if (timer.startedAtMs != null) Color(0xFF171717) else Color(0xFF222222),
                            foreground = Color.White,
                            size = 52.dp,
                            enabled = subjectsEditable && (timer.startedAtMs != null || !selectedSubject.isNullOrBlank()),
                            onClick = {
                                timer = if (timer.startedAtMs != null) {
                                    repository.pauseTimer(userId)
                                } else {
                                    repository.startTimer(userId, selectedSubject.orEmpty())
                                }
                                refreshAfterLocalChange()
                                syncInBackground()
                            }
                        )
                    }
                    val shownSubject = timer.runningSubject ?: selectedSubject
                    if (!shownSubject.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = shownSubject,
                            fontSize = 13.sp,
                            color = Color(0xFF555555),
                            modifier = Modifier
                                .background(
                                    color = StudySyncManager.subjectPalette[
                                        subjects.firstOrNull { it.name == shownSubject }?.color ?: 0
                                    ].let { Color(it) },
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (timer.startedAtMs == null) {
                Spacer(Modifier.height(26.dp))
                Text(
                    text = if (subjectsEditable) "Subjects" else "Connect to load your subjects",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF555555)
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(subjects, key = { it.name }) { subject ->
                        var menuOpen by remember(subject.name) { mutableStateOf(false) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                FilterChip(
                                    selected = selectedSubject == subject.name,
                                    onClick = {
                                        selectedSubject = subject.name
                                        repository.selectSubject(userId, subject.name)
                                        timer = repository.timerState(userId)
                                    },
                                    label = { Text(subject.name, maxLines = 1) },
                                    enabled = subjectsEditable,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(StudySyncManager.subjectPalette[subject.color]),
                                        containerColor = Color(0xFFF7F7F7),
                                        selectedLabelColor = Color(0xFF333333),
                                        labelColor = Color(0xFF444444)
                                    ),
                                )
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Edit subject") },
                                        onClick = {
                                            menuOpen = false
                                            editorSubject = subject
                                        }
                                    )
                                }
                            }
                            TextButton(
                                onClick = { menuOpen = true },
                                enabled = subjectsEditable,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("•••", fontSize = 11.sp, color = Color(0xFF777777))
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { showAddSubject = true },
                            enabled = subjectsEditable,
                            shape = CircleShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF7F7F7),
                                contentColor = Color(0xFF333333)
                            )
                        ) {
                            Text("+", fontSize = 19.sp)
                        }
                    }
                }
                if (selectedSubject.isNullOrBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "Select a subject before starting the timer.",
                        fontSize = 12.sp,
                        color = Color(0xFF777777),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Focus session running",
                    fontSize = 13.sp,
                    color = Color(0xFF666666)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = syncLabel,
                fontSize = 12.sp,
                color = Color(0xFF686868),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your study analytics are available on the website.",
                fontSize = 11.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showAddSubject || editorSubject != null) {
        SubjectEditorDialog(
            subject = editorSubject,
            onDismiss = {
                showAddSubject = false
                editorSubject = null
            },
            onSave = { updated ->
                val current = subjects.toMutableList()
                val old = editorSubject
                if (old == null) {
                    if (current.none { it.name.equals(updated.name, ignoreCase = true) } && current.size < 40) {
                        current.add(updated)
                        selectedSubject = updated.name
                        repository.selectSubject(userId, updated.name)
                        saveSubjectChanges(current)
                    }
                } else {
                    val index = current.indexOfFirst { it.name == old.name }
                    if (index >= 0 && current.none { it.name.equals(updated.name, ignoreCase = true) && it.name != old.name }) {
                        current[index] = updated
                        if (selectedSubject == old.name) {
                            selectedSubject = updated.name
                            repository.selectSubject(userId, updated.name)
                        }
                        saveSubjectChanges(current)
                    }
                }
                showAddSubject = false
                editorSubject = null
            },
            onDelete = {
                val current = subjects.filterNot { it.name == editorSubject?.name }
                saveSubjectChanges(current)
                showAddSubject = false
                editorSubject = null
            }
        )
    }
}

@Composable
private fun TimerCircleButton(
    label: String,
    contentDescription: String,
    background: Color,
    foreground: Color,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = background.copy(alpha = if (enabled) 1f else 0.45f),
        border = BorderStroke(1.dp, Color(0xFFDADADA))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SubjectEditorDialog(
    subject: StudySubject?,
    onDismiss: () -> Unit,
    onSave: (StudySubject) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(subject) { mutableStateOf(subject?.name ?: "") }
    var colorIndex by remember(subject) { mutableStateOf(subject?.color ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (subject == null) "Add subject" else "Edit subject") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    label = { Text("Subject name") }
                )
                Spacer(Modifier.height(14.dp))
                Text("Colour", fontSize = 13.sp, color = Color(0xFF666666))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudySyncManager.subjectPalette.forEachIndexed { index, colour ->
                        Surface(
                            modifier = Modifier.size(28.dp).clickable { colorIndex = index },
                            shape = CircleShape,
                            color = Color(colour),
                            border = BorderStroke(
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
            Row {
                if (subject != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFB3261E)) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun formatStudyTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
