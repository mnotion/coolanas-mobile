package com.coolanas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.util.Locale

private val GuildJoinedBlue = Color(0xFF2879DF)

@Composable
internal fun GuildJoinedHomeContent(
    guild: GuildData,
    currentUserId: String,
    members: List<GuildMemberData>,
    messages: List<GuildMessageData>,
    messagesLoaded: Boolean,
    hasOlderMessages: Boolean,
    elapsedMs: Long,
    draft: String,
    sending: Boolean,
    error: String?,
    statusMessage: String?,
    inviteUsername: String,
    isOwner: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: suspend (String) -> Unit,
    onInviteUsernameChange: (String) -> Unit,
    onInvite: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenBazaar: () -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenStudyingNow: () -> Unit,
    onEditGuild: () -> Unit,
    onLeaveGuild: () -> Unit,
    onDisbandGuild: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val screenBackground = if (isDark) Color(0xFF17191D) else Color(0xFFF3F6FD)
    val surface = if (isDark) Color(0xFF24262B) else Color(0xFFFFFFFF)
    val foreground = if (isDark) Color(0xFFF4F4F4) else Color(0xFF20242B)
    val muted = if (isDark) Color(0xFFB7BAC1) else Color(0xFF77849A)
    val outline = if (isDark) Color(0xFF4D5058) else Color(0xFFE0E7F2)
    val messageBackground = if (isDark) Color(0xFF222222) else Color(0xFFFFFFFF)
    val gridLine = if (isDark) Color.White.copy(alpha = 0.035f) else Color(0xFF505050).copy(alpha = 0.045f)
    val incomingBubble = if (isDark) Color(0xFF303238) else Color(0xFFF5F6F8)
    val outgoingBubble = if (isDark) Color(0xFF33445A) else Color(0xFFEAF1FB)
    val bubbleBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF787878).copy(alpha = 0.16f)

    var channelMenuExpanded by remember(guild.id) { mutableStateOf(false) }
    var toolsMenuExpanded by remember(guild.id) { mutableStateOf(false) }
    var inviteDialogOpen by remember(guild.id) { mutableStateOf(false) }
    var loadingOlder by remember(guild.id) { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val latestMessages by rememberUpdatedState(messages)
    val latestHasOlder by rememberUpdatedState(hasOlderMessages)
    val latestLoadingOlder by rememberUpdatedState(loadingOlder)
    val latestLoadOlder by rememberUpdatedState(onLoadOlder)

    LaunchedEffect(guild.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                if (
                    firstVisible <= 1 &&
                    latestHasOlder &&
                    !latestLoadingOlder &&
                    latestMessages.isNotEmpty()
                ) {
                    val oldestTimestamp = latestMessages.first().createdAt
                    loadingOlder = true
                    try {
                        latestLoadOlder(oldestTimestamp)
                    } finally {
                        loadingOlder = false
                    }
                }
            }
    }

    var initialScrollDone by remember(guild.id) { mutableStateOf(false) }
    LaunchedEffect(guild.id, messagesLoaded, messages.size) {
        if (!messagesLoaded) return@LaunchedEffect
        val layout = listState.layoutInfo
        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
        val atBottom = !initialScrollDone || lastVisible >= layout.totalItemsCount - 3
        if (messages.isNotEmpty() && atBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
        initialScrollDone = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground)
    ) {
        GuildMobileControlRow(
            elapsedMs = elapsedMs,
            surface = surface,
            foreground = foreground,
            muted = muted,
            outline = outline,
            onOpenTimer = onOpenTimer,
            channelMenuExpanded = channelMenuExpanded,
            onChannelMenuChange = { channelMenuExpanded = it },
            toolsMenuExpanded = toolsMenuExpanded,
            onToolsMenuChange = { toolsMenuExpanded = it },
            onOpenBazaar = onOpenBazaar,
            onOpenInvitations = onOpenInvitations,
            onOpenStudyingNow = onOpenStudyingNow,
            onInvite = { inviteDialogOpen = true },
            onEditGuild = onEditGuild,
            onLeaveGuild = onLeaveGuild,
            onDisbandGuild = onDisbandGuild,
            isOwner = isOwner
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    drawRect(messageBackground)
                    val spacing = 55.dp.toPx()
                    val strokeWidth = 1.dp.toPx()
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(
                            color = gridLine,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = strokeWidth
                        )
                        x += spacing
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(
                            color = gridLine,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                        y += spacing
                    }
                }
        ) {
            val bubbleMaxWidth = maxWidth * 0.82f
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (loadingOlder) {
                    item(key = "guild-history-loading") {
                        Text(
                            "Loading older messages…",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = muted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (messages.isEmpty() && messagesLoaded) {
                    item(key = "guild-empty-conversation") {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No messages yet. Start the conversation.",
                                color = muted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (messages.isEmpty()) {
                    item(key = "guild-loading-conversation") {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading conversation…", color = muted, fontSize = 12.sp)
                        }
                    }
                }

                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    val previous = messages.getOrNull(index - 1)
                    val sameSender = previous?.senderId == message.senderId
                    val mine = message.senderId == currentUserId
                    val sender = members.firstOrNull { it.stableId == message.senderId }

                    GuildMobileMessageRow(
                        message = message,
                        sender = sender,
                        mine = mine,
                        sameSender = sameSender,
                        bubbleMaxWidth = bubbleMaxWidth,
                        foreground = foreground,
                        muted = muted,
                        bubbleBackground = if (mine) outgoingBubble else incomingBubble,
                        bubbleBorder = bubbleBorder
                    )
                }
            }
        }

        if (!error.isNullOrBlank() || !statusMessage.isNullOrBlank()) {
            Text(
                text = error ?: statusMessage.orEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                color = if (!error.isNullOrBlank()) Color(0xFFB42318) else muted,
                fontSize = 10.sp,
                maxLines = 2
            )
        }

        GuildMobileComposer(
            value = draft,
            sending = sending,
            surface = surface,
            foreground = foreground,
            muted = muted,
            outline = outline,
            onValueChange = onDraftChange,
            onSend = onSend
        )
    }

    if (inviteDialogOpen) {
        AlertDialog(
            onDismissRequest = { inviteDialogOpen = false },
            title = { Text("Invite a member") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the member's exact Coolanas username.", color = muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = inviteUsername,
                        onValueChange = onInviteUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Username") },
                        placeholder = { Text("e.g. fern") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = inviteUsername.isNotBlank(),
                    onClick = {
                        inviteDialogOpen = false
                        onInvite()
                    }
                ) { Text("Send invitation") }
            },
            dismissButton = {
                TextButton(onClick = { inviteDialogOpen = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }
}

@Composable
private fun GuildMobileControlRow(
    elapsedMs: Long,
    surface: Color,
    foreground: Color,
    muted: Color,
    outline: Color,
    onOpenTimer: () -> Unit,
    channelMenuExpanded: Boolean,
    onChannelMenuChange: (Boolean) -> Unit,
    toolsMenuExpanded: Boolean,
    onToolsMenuChange: (Boolean) -> Unit,
    onOpenBazaar: () -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenStudyingNow: () -> Unit,
    onInvite: () -> Unit,
    onEditGuild: () -> Unit,
    onLeaveGuild: () -> Unit,
    onDisbandGuild: () -> Unit,
    isOwner: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(52.dp)) {
            GuildMobileRoundControl(
                surface = surface,
                outline = outline,
                contentDescription = "Guild channels",
                onClick = { onChannelMenuChange(true) }
            ) {
                GuildMobileHashtagGlyph(foreground)
            }
            DropdownMenu(
                expanded = channelMenuExpanded,
                onDismissRequest = { onChannelMenuChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("# general", fontWeight = FontWeight.SemiBold) },
                    onClick = { onChannelMenuChange(false) },
                    leadingIcon = { Text("#", color = GuildJoinedBlue, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Box(
            modifier = Modifier.weight(1f).height(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .height(52.dp)
                    .widthIn(max = 210.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenTimer),
                shape = RoundedCornerShape(999.dp),
                color = surface,
                border = BorderStroke(1.dp, outline)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        formatGuildMobileTimer(elapsedMs),
                        color = foreground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1
                    )
                }
            }
        }

        Box(Modifier.size(52.dp)) {
            GuildMobileRoundControl(
                surface = surface,
                outline = outline,
                contentDescription = "Guild tools",
                onClick = { onToolsMenuChange(true) }
            ) {
                GuildMobileBucketGlyph(foreground)
            }
            DropdownMenu(
                expanded = toolsMenuExpanded,
                onDismissRequest = { onToolsMenuChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("Guild Bazaar") },
                    onClick = { onToolsMenuChange(false); onOpenBazaar() }
                )
                DropdownMenuItem(
                    text = { Text("Invitations") },
                    onClick = { onToolsMenuChange(false); onOpenInvitations() }
                )
                DropdownMenuItem(
                    text = { Text("Studying now") },
                    onClick = { onToolsMenuChange(false); onOpenStudyingNow() }
                )
                if (isOwner) {
                    DropdownMenuItem(
                        text = { Text("Invite member") },
                        onClick = { onToolsMenuChange(false); onInvite() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Guild") },
                        onClick = { onToolsMenuChange(false); onEditGuild() }
                    )
                    DropdownMenuItem(
                        text = { Text("Disband Guild", color = Color(0xFFB42318)) },
                        onClick = { onToolsMenuChange(false); onDisbandGuild() }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Leave Guild", color = Color(0xFFB42318)) },
                        onClick = { onToolsMenuChange(false); onLeaveGuild() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GuildMobileRoundControl(
    surface: Color,
    outline: Color,
    contentDescription: String,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = surface,
        border = BorderStroke(1.dp, outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            glyph()
        }
    }
}

@Composable
private fun GuildMobileHashtagGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.36f, size.height * 0.12f), Offset(size.width * 0.24f, size.height * 0.88f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.73f, size.height * 0.12f), Offset(size.width * 0.61f, size.height * 0.88f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.12f, size.height * 0.39f), Offset(size.width * 0.88f, size.height * 0.39f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.08f, size.height * 0.68f), Offset(size.width * 0.84f, size.height * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun GuildMobileBucketGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val bucket = Path().apply {
            moveTo(size.width * 0.20f, size.height * 0.34f)
            lineTo(size.width * 0.28f, size.height * 0.86f)
            lineTo(size.width * 0.72f, size.height * 0.86f)
            lineTo(size.width * 0.80f, size.height * 0.34f)
        }
        drawPath(bucket, tint, style = stroke)
        drawLine(
            tint,
            Offset(size.width * 0.13f, size.height * 0.34f),
            Offset(size.width * 0.87f, size.height * 0.34f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(size.width * 0.36f, size.height * 0.20f),
            Offset(size.width * 0.64f, size.height * 0.20f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(size.width * 0.36f, size.height * 0.20f),
            Offset(size.width * 0.36f, size.height * 0.34f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(size.width * 0.64f, size.height * 0.20f),
            Offset(size.width * 0.64f, size.height * 0.34f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun GuildMobileMessageRow(
    message: GuildMessageData,
    sender: GuildMemberData?,
    mine: Boolean,
    sameSender: Boolean,
    bubbleMaxWidth: Dp,
    foreground: Color,
    muted: Color,
    bubbleBackground: Color,
    bubbleBorder: Color
) {
    val groupTop = if (sameSender) 4.dp else 12.dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = groupTop),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!mine) {
            if (sameSender) {
                Spacer(Modifier.width(42.dp))
            } else {
                GuildAvatar(
                    url = sender?.imageUrl,
                    label = sender?.username ?: "Guild member",
                    size = 34.dp
                )
                Spacer(Modifier.width(8.dp))
            }
        }

        Column(
            modifier = Modifier.widthIn(max = bubbleMaxWidth),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start
        ) {
            if (!mine && !sameSender) {
                Text(
                    sender?.username ?: "Guild member",
                    modifier = Modifier.padding(start = 1.dp, bottom = 3.dp),
                    color = muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            val bubbleShape = RoundedCornerShape(
                topStart = if (!mine && !sameSender) 5.dp else 12.dp,
                topEnd = if (mine && !sameSender) 5.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp
            )
            Box(
                modifier = Modifier
                    .widthIn(max = bubbleMaxWidth)
                    .clip(bubbleShape)
                    .background(bubbleBackground)
                    .border(1.dp, bubbleBorder, bubbleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    message.body,
                    color = foreground,
                    fontSize = 13.sp,
                    lineHeight = 18.8.sp
                )
            }

            Text(
                formatGuildMobileTime(message.createdAt),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 3.dp, end = 3.dp),
                color = muted.copy(alpha = 0.78f),
                fontSize = 9.sp,
                textAlign = if (mine) TextAlign.End else TextAlign.Start,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun GuildMobileComposer(
    value: String,
    sending: Boolean,
    surface: Color,
    foreground: Color,
    muted: Color,
    outline: Color,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(shape)
            .background(surface)
            .border(1.dp, outline, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 38.dp, max = 120.dp)
                .padding(horizontal = 7.dp),
            enabled = !sending,
            singleLine = false,
            maxLines = 4,
            textStyle = TextStyle(
                color = foreground,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            cursorBrush = SolidColor(GuildJoinedBlue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank() && !sending) onSend() }),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text("Message the Guild…", color = muted, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            }
        )

        Surface(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = value.isNotBlank() && !sending, onClick = onSend),
            shape = RoundedCornerShape(10.dp),
            color = if (value.isNotBlank() && !sending) GuildJoinedBlue else Color(0xFF9BAABD)
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(19.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.46f)
                        lineTo(size.width * 0.91f, size.height * 0.08f)
                        lineTo(size.width * 0.59f, size.height * 0.92f)
                        lineTo(size.width * 0.45f, size.height * 0.59f)
                        close()
                    }
                    drawPath(path, Color.White)
                    drawLine(
                        Color.White,
                        Offset(size.width * 0.45f, size.height * 0.59f),
                        Offset(size.width * 0.91f, size.height * 0.08f),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private fun formatGuildMobileTimer(milliseconds: Long): String {
    val total = (milliseconds / 1000L).coerceAtLeast(0L)
    val days = total / 86_400L
    val hours = (total % 86_400L) / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return if (days > 0L) {
        String.format(Locale.getDefault(), "%dd %02d:%02d:%02d", days, hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", total / 3_600L, minutes, seconds)
    }
}

private fun formatGuildMobileTime(value: String): String = runCatching {
    Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
}.getOrDefault("")
