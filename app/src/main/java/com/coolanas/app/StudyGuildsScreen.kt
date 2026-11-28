package com.coolanas.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.max

private val GuildBlue = Color(0xFF2879DF)
private val GuildBlueSoft = Color(0xFFE1EDFF)
private val GuildBackground = Color(0xFFF3F6FD)
private val GuildSurface = Color(0xFFFFFFFF)
private val GuildText = Color(0xFF172033)
private val GuildMuted = Color(0xFF77849A)
private val GuildBorder = Color(0xFFE0E7F2)
private val GuildPalette = listOf("#347FDF", "#8B65D9", "#E86A9A", "#F0C85A", "#55B77A")

private enum class GuildPage { Home, Chat, Bazaar, StudyingNow, Create, Invitations, Edit }

private object NativeGuildRepository {
    private val client get() = SupabaseClientProvider.client

    suspend fun currentGuild(): GuildData? = withContext(Dispatchers.IO) {
        client.postgrest.rpc("coolanas_guild_get_or_create").decodeAs<GuildData?>()
    }

    suspend fun invitations(): GuildInvitationsData = withContext(Dispatchers.IO) {
        client.postgrest.rpc("coolanas_guild_list_invitations").decodeAs<GuildInvitationsData>()
    }

    suspend fun memberStatuses(): List<GuildMemberStatusData> = withContext(Dispatchers.IO) {
        client.postgrest.rpc("coolanas_guild_member_status").decodeList<GuildMemberStatusData>()
    }

    suspend fun publicDirectory(page: Int): GuildPublicDirectoryData = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_public_directory",
            parameters = buildJsonObject {
                put("p_page", JsonPrimitive(page))
                put("p_page_size", JsonPrimitive(10))
            }
        ).decodeAs<GuildPublicDirectoryData>()
    }

    suspend fun publicTimers(): List<GuildPublicTimerData> = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_public_running_timers",
            parameters = buildJsonObject { put("p_limit", JsonPrimitive(500)) }
        ).decodeList<GuildPublicTimerData>()
    }

    suspend fun messages(guildId: String): List<GuildMessageData> = withContext(Dispatchers.IO) {
        client.postgrest.from("coolanas_guild_messages").select {
            filter { eq("guild_id", guildId) }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            limit(100)
        }.decodeList<GuildMessageData>()
    }

    suspend fun latestMessages(guildId: String, pageSize: Int = 50): List<GuildMessageData> = withContext(Dispatchers.IO) {
        client.postgrest.from("coolanas_guild_messages").select {
            filter { eq("guild_id", guildId) }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(pageSize)
        }.decodeList<GuildMessageData>().reversed()
    }

    suspend fun olderMessages(guildId: String, before: String, pageSize: Int = 50): List<GuildMessageData> = withContext(Dispatchers.IO) {
        client.postgrest.from("coolanas_guild_messages").select {
            filter {
                eq("guild_id", guildId)
                lt("created_at", before)
            }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(pageSize)
        }.decodeList<GuildMessageData>().reversed()
    }

    suspend fun sendMessage(guildId: String, userId: String, body: String) = withContext(Dispatchers.IO) {
        client.postgrest.from("coolanas_guild_messages").insert(
            buildJsonObject {
                put("guild_id", JsonPrimitive(guildId))
                put("sender_id", JsonPrimitive(userId))
                put("body", JsonPrimitive(body))
            }
        )
    }

    suspend fun createGuild(
        name: String, description: String, isPublic: Boolean,
        bannerUrl: String?, pictureUrl: String?, colour: String
    ): GuildData = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_create",
            parameters = buildJsonObject {
                put("p_name", JsonPrimitive(name))
                put("p_description", JsonPrimitive(description))
                put("p_is_public", JsonPrimitive(isPublic))
                put("p_banner_url", JsonPrimitive(bannerUrl ?: ""))
                put("p_picture_url", JsonPrimitive(pictureUrl ?: ""))
                put("p_colour", JsonPrimitive(colour))
            }
        ).decodeAs<GuildData>()
    }

    suspend fun updateGuild(
        guildId: String, name: String, description: String, isPublic: Boolean,
        bannerUrl: String?, pictureUrl: String?, colour: String
    ): GuildData = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_update",
            parameters = buildJsonObject {
                put("p_guild_id", JsonPrimitive(guildId))
                put("p_name", JsonPrimitive(name))
                put("p_description", JsonPrimitive(description))
                put("p_is_public", JsonPrimitive(isPublic))
                put("p_banner_url", JsonPrimitive(bannerUrl ?: ""))
                put("p_picture_url", JsonPrimitive(pictureUrl ?: ""))
                put("p_colour", JsonPrimitive(colour))
            }
        ).decodeAs<GuildData>()
    }

    suspend fun invite(guildId: String, username: String) = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_invite_user",
            parameters = buildJsonObject {
                put("p_username", JsonPrimitive(username))
                put("p_guild_id", JsonPrimitive(guildId))
            }
        )
    }

    suspend fun respondInvitation(invitationId: String, accept: Boolean) = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_respond_invitation",
            parameters = buildJsonObject {
                put("p_invitation_id", JsonPrimitive(invitationId))
                put("p_accept", JsonPrimitive(accept))
            }
        )
    }

    suspend fun joinPublicGuild(guildId: String) = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_join_public",
            parameters = buildJsonObject { put("p_guild_id", JsonPrimitive(guildId)) }
        )
    }

    suspend fun leaveGuild(guildId: String) = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_leave",
            parameters = buildJsonObject { put("p_guild_id", JsonPrimitive(guildId)) }
        )
    }

    suspend fun disbandGuild(guildId: String) = withContext(Dispatchers.IO) {
        client.postgrest.rpc(
            function = "coolanas_guild_disband",
            parameters = buildJsonObject { put("p_guild_id", JsonPrimitive(guildId)) }
        )
    }

    suspend fun uploadImage(context: Context, userId: String, uri: Uri, kind: String): String =
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when (mime.lowercase(Locale.ROOT)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read the selected image.")
            require(bytes.isNotEmpty()) { "The selected image is empty." }
            require(bytes.size <= 10 * 1024 * 1024) { "Guild images must be 10 MB or smaller." }
            val path = "$userId/" + UUID.randomUUID().toString() + "/$kind.$extension"
            val bucket = client.storage.from("coolanas-guild-assets")
            bucket.upload(path, bytes) { upsert = false }
            bucket.publicUrl(path)
        }
}

@Composable
fun StudyGuildsScreen(
    userId: String,
    timer: StudyTimerState,
    serverNowMillis: Long,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onOpenTimer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember(userId) { mutableStateOf(GuildPage.Home) }
    var guild by remember(userId) { mutableStateOf<GuildData?>(null) }
    var invitations by remember(userId) { mutableStateOf(GuildInvitationsData()) }
    var statuses by remember(userId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var directory by remember(userId) { mutableStateOf<GuildPublicDirectoryData?>(null) }
    var directoryPage by remember(userId) { mutableStateOf(1) }
    var directorySearch by remember(userId) { mutableStateOf("") }
    var publicTimers by remember(userId) { mutableStateOf<List<GuildPublicTimerData>>(emptyList()) }
    var activeTimerClockOffset by remember(userId) { mutableStateOf(0L) }
    var messages by remember(userId) { mutableStateOf<List<GuildMessageData>>(emptyList()) }
    var messagesLoaded by remember(userId) { mutableStateOf(false) }
    var hasOlderMessages by remember(userId) { mutableStateOf(false) }
    var messageDraft by remember(userId) { mutableStateOf("") }
    var loading by remember(userId) { mutableStateOf(true) }
    var busy by remember(userId) { mutableStateOf(false) }
    var sending by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var infoMessage by remember(userId) { mutableStateOf<String?>(null) }
    var outgoingInvitations by remember(userId) { mutableStateOf(false) }
    var inviteUsername by remember(userId) { mutableStateOf("") }
    var confirmAction by remember(userId) { mutableStateOf<String?>(null) }
    var imageTarget by remember(userId) { mutableStateOf("banner") }

    var draftName by remember(userId) { mutableStateOf("") }
    var draftDescription by remember(userId) { mutableStateOf("") }
    var draftPublic by remember(userId) { mutableStateOf(true) }
    var draftBannerUrl by remember(userId) { mutableStateOf<String?>(null) }
    var draftPictureUrl by remember(userId) { mutableStateOf<String?>(null) }
    var draftColour by remember(userId) { mutableStateOf(GuildPalette.first()) }
    var draftInvitees by remember(userId) { mutableStateOf("") }
    var wizardStep by remember(userId) { mutableStateOf(1) }

    suspend fun refreshGuild() {
        loading = true
        error = null
        try {
            val loaded = NativeGuildRepository.currentGuild()
            guild = loaded
            if (loaded != null) {
                runCatching { NativeGuildRepository.memberStatuses() }
                    .onSuccess { statuses = it.associate { row -> row.userId to row.status } }
                runCatching { NativeGuildRepository.invitations() }
                    .onSuccess { invitations = it }
            } else {
                statuses = emptyMap()
                invitations = GuildInvitationsData()
            }
        } catch (exception: Exception) {
            error = exception.message ?: "Could not load your Guild."
        } finally {
            loading = false
        }
    }

    suspend fun refreshInvitations() {
        invitations = NativeGuildRepository.invitations()
    }

    suspend fun refreshDirectory() {
        directory = NativeGuildRepository.publicDirectory(directoryPage)
    }

    suspend fun refreshPublicTimers() {
        val rows = NativeGuildRepository.publicTimers()
        publicTimers = rows
        val server = rows.firstNotNullOfOrNull { row ->
            row.serverNow?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
        }
        if (server != null) activeTimerClockOffset = server - System.currentTimeMillis()
    }

    suspend fun loadOlderGuildMessages(before: String) {
        val activeGuildId = guild?.id ?: return
        val older = NativeGuildRepository.olderMessages(activeGuildId, before)
        hasOlderMessages = older.size == 50
        if (older.isNotEmpty()) messages = mergeGuildMessageLists(older, messages)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                error = null
                try {
                    val url = NativeGuildRepository.uploadImage(context, userId, uri, imageTarget)
                    if (imageTarget == "banner") draftBannerUrl = url else draftPictureUrl = url
                } catch (exception: Exception) {
                    error = exception.message ?: "The image could not be uploaded."
                } finally {
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(userId) { refreshGuild() }

    LaunchedEffect(guild?.id, page) {
        if (guild != null && page == GuildPage.Home) {
            while (isActive) {
                delay(10_000L)
                runCatching {
                    NativeGuildRepository.memberStatuses().also { rows ->
                        statuses = rows.associate { it.userId to it.status }
                    }
                }
            }
        }
    }

    LaunchedEffect(page, directoryPage) {
        if (page == GuildPage.Bazaar) {
            loading = true
            runCatching { refreshDirectory() }
                .onFailure { error = it.message ?: "Could not load public Guilds." }
            loading = false
        }
    }

    LaunchedEffect(page) {
        if (page == GuildPage.Invitations) {
            runCatching { refreshInvitations() }
                .onFailure { error = it.message ?: "Could not load invitations." }
        }
        if (page == GuildPage.StudyingNow) {
            loading = true
            runCatching { refreshPublicTimers() }
                .onFailure { error = it.message ?: "Could not load studying members." }
            loading = false
            while (isActive) {
                delay(5_000L)
                runCatching { refreshPublicTimers() }
            }
        }
    }

    LaunchedEffect(guild?.id, page) {
        val activeGuild = guild ?: return@LaunchedEffect
        val joinedHome = page == GuildPage.Home
        if (!joinedHome && page != GuildPage.Chat) return@LaunchedEffect

        messages = emptyList()
        messagesLoaded = false
        hasOlderMessages = false

        val channel = SupabaseClientProvider.client.realtime.channel("coolanas-guild-chat-" + activeGuild.id)
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "coolanas_guild_messages"
            filter = "guild_id=eq." + activeGuild.id
        }
        val observer = launch {
            insertFlow.collect { action ->
                val row = runCatching { action.decodeRecord<GuildMessageData>() }.getOrNull() ?: return@collect
                if (row.guildId == activeGuild.id && messages.none { it.id == row.id }) {
                    messages = mergeGuildMessageLists(messages, listOf(row))
                }
            }
        }
        try {
            channel.subscribe(blockUntilSubscribed = false)
            val initial = if (joinedHome) {
                NativeGuildRepository.latestMessages(activeGuild.id)
            } else {
                NativeGuildRepository.messages(activeGuild.id)
            }
            messages = mergeGuildMessageLists(messages, initial)
            hasOlderMessages = joinedHome && initial.size == 50
            messagesLoaded = true

            while (isActive) {
                delay(5_000L)
                val latest = if (joinedHome) {
                    NativeGuildRepository.latestMessages(activeGuild.id)
                } else {
                    NativeGuildRepository.messages(activeGuild.id)
                }
                messages = mergeGuildMessageLists(messages, latest)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            messagesLoaded = true
            error = exception.message ?: "Guild chat could not refresh."
        } finally {
            observer.cancel()
            SupabaseClientProvider.client.realtime.removeChannel(channel)
        }
    }

    fun beginCreate() {
        draftName = ""
        draftDescription = ""
        draftPublic = true
        draftBannerUrl = null
        draftPictureUrl = null
        draftColour = GuildPalette.first()
        draftInvitees = ""
        wizardStep = 1
        error = null
        page = GuildPage.Create
    }

    fun beginEdit() {
        val current = guild ?: return
        draftName = current.name
        draftDescription = current.description.orEmpty()
        draftPublic = current.isPublic
        draftBannerUrl = current.bannerUrl
        draftPictureUrl = current.pictureUrl
        draftColour = current.colour?.takeIf { it.matches(Regex("^#[0-9a-fA-F]{6}$")) } ?: GuildPalette.first()
        draftInvitees = ""
        wizardStep = 1
        error = null
        page = GuildPage.Edit
    }

    fun submitMessage() {
        val current = guild ?: return
        val body = messageDraft.trim()
        if (body.isBlank() || sending) return
        sending = true
        error = null
        scope.launch {
            try {
                NativeGuildRepository.sendMessage(current.id, userId, body)
                messageDraft = ""
                val refreshed = if (page == GuildPage.Home) {
                    NativeGuildRepository.latestMessages(current.id)
                } else {
                    NativeGuildRepository.messages(current.id)
                }
                messages = mergeGuildMessageLists(messages, refreshed)
            } catch (exception: Exception) {
                error = exception.message ?: "Message could not be sent."
            } finally {
                sending = false
            }
        }
    }

    fun saveGuild() {
        if (busy) return
        if (draftName.trim().length < 3) {
            error = "Guild names must contain at least 3 characters."
            wizardStep = 1
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val wasCreating = page == GuildPage.Create
                val saved = if (wasCreating) {
                    NativeGuildRepository.createGuild(
                        draftName.trim(), draftDescription.trim(), draftPublic,
                        draftBannerUrl, draftPictureUrl, draftColour
                    )
                } else {
                    val existing = guild ?: throw IllegalStateException("Guild not found.")
                    NativeGuildRepository.updateGuild(
                        existing.id, draftName.trim(), draftDescription.trim(), draftPublic,
                        draftBannerUrl, draftPictureUrl, draftColour
                    )
                }
                guild = saved
                page = GuildPage.Home
                infoMessage = "Guild saved."
                if (wasCreating && draftInvitees.isNotBlank() && saved.id.isNotBlank()) {
                    val usernames = draftInvitees.split(Regex("[\\n,]+"))
                        .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
                    val failed = mutableListOf<String>()
                    usernames.forEach { username ->
                        runCatching { NativeGuildRepository.invite(saved.id, username) }
                            .onFailure { failed += username }
                    }
                    if (failed.isNotEmpty()) error = "Guild created, but invitations failed for: " + failed.joinToString()
                    runCatching { refreshInvitations() }
                }
                refreshGuild()
            } catch (exception: Exception) {
                error = exception.message ?: "Guild could not be saved."
            } finally {
                busy = false
            }
        }
    }

    fun respondToInvitation(invitation: GuildInvitationData, accept: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                NativeGuildRepository.respondInvitation(invitation.id, accept)
                refreshGuild()
                refreshInvitations()
                infoMessage = if (accept) "Guild invitation accepted." else "Invitation declined."
            } catch (exception: Exception) {
                error = exception.message ?: "Invitation could not be updated."
            } finally {
                busy = false
            }
        }
    }

    fun joinGuild(target: GuildData) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                NativeGuildRepository.joinPublicGuild(target.id)
                refreshGuild()
                page = GuildPage.Home
                infoMessage = "You joined " + target.name + "."
            } catch (exception: Exception) {
                error = exception.message ?: "Could not join this Guild."
            } finally {
                busy = false
            }
        }
    }

    fun inviteMember() {
        val current = guild ?: return
        val username = inviteUsername.trim()
        if (username.isBlank() || busy) return
        busy = true
        scope.launch {
            try {
                NativeGuildRepository.invite(current.id, username)
                inviteUsername = ""
                refreshInvitations()
                infoMessage = "Invitation sent to $username."
            } catch (exception: Exception) {
                error = exception.message ?: "Could not invite this user."
            } finally {
                busy = false
            }
        }
    }

    fun performConfirmedAction(action: String) {
        val current = guild ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (action == "leave") NativeGuildRepository.leaveGuild(current.id)
                else NativeGuildRepository.disbandGuild(current.id)
                guild = null
                invitations = GuildInvitationsData()
                statuses = emptyMap()
                page = GuildPage.Home
                infoMessage = if (action == "leave") "You left the Guild." else "Guild disbanded."
                refreshGuild()
            } catch (exception: Exception) {
                error = exception.message ?: "The Guild action failed."
            } finally {
                busy = false
                confirmAction = null
            }
        }
    }

    val currentGuild = guild
    val owner = currentGuild?.ownerId == userId
    val elapsed = timer.elapsedAt(serverNowMillis)
    val members = currentGuild?.members.orEmpty()

    val joinedHomeLayout = page == GuildPage.Home && currentGuild != null
    Box(Modifier.fillMaxSize().background(GuildBackground)) {
        Column(
            Modifier.fillMaxSize().then(
                if (joinedHomeLayout) {
                    Modifier.padding(top = 10.dp, bottom = 100.dp)
                } else {
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
                }
            )
        ) {
            when (page) {
                GuildPage.Home -> if (currentGuild != null) {
                    GuildJoinedHomeContent(
                        guild = currentGuild,
                        currentUserId = userId,
                        members = members,
                        messages = messages,
                        messagesLoaded = messagesLoaded,
                        hasOlderMessages = hasOlderMessages,
                        elapsedMs = elapsed,
                        draft = messageDraft,
                        sending = sending,
                        error = error,
                        statusMessage = infoMessage,
                        inviteUsername = inviteUsername,
                        isOwner = owner,
                        onDraftChange = { messageDraft = it },
                        onSend = ::submitMessage,
                        onLoadOlder = ::loadOlderGuildMessages,
                        onInviteUsernameChange = { inviteUsername = it },
                        onInvite = ::inviteMember,
                        onOpenTimer = onOpenTimer,
                        onOpenBazaar = { page = GuildPage.Bazaar },
                        onOpenInvitations = { page = GuildPage.Invitations },
                        onOpenStudyingNow = { page = GuildPage.StudyingNow },
                        onEditGuild = ::beginEdit,
                        onLeaveGuild = { confirmAction = "leave" },
                        onDisbandGuild = { confirmAction = "disband" }
                    )
                } else {
                    GuildHomeContent(
                        guild = null, currentUserId = userId, members = members, statuses = statuses,
                        elapsedMs = elapsed, timerRunning = timer.startedAtMs != null, loading = loading,
                        onCreate = ::beginCreate, onOpenBazaar = { page = GuildPage.Bazaar },
                        onOpenChat = { page = GuildPage.Chat }, onOpenStudying = { page = GuildPage.StudyingNow },
                        onOpenInvitations = { page = GuildPage.Invitations }, onEdit = ::beginEdit,
                        onLeave = { confirmAction = "leave" }, onDisband = { confirmAction = "disband" },
                        onTimerToggle = onToggleTimer, onTimerReset = onResetTimer, onOpenTimer = onOpenTimer,
                        isOwner = owner, inviteUsername = inviteUsername, onInviteUsername = { inviteUsername = it },
                        onInvite = ::inviteMember, busy = busy
                    )
                }
                GuildPage.Chat -> GuildChatContent(
                    guild = currentGuild, members = members, userId = userId,
                    messages = messages, draft = messageDraft, sending = sending, error = error,
                    onBack = { page = GuildPage.Home }, onDraftChange = { messageDraft = it },
                    onSend = ::submitMessage, onGuildInfo = ::beginEdit
                )
                GuildPage.Bazaar -> GuildBazaarContent(
                    guilds = directory?.guilds.orEmpty().filter {
                        directorySearch.isBlank() || it.name.contains(directorySearch, true) ||
                            it.description.orEmpty().contains(directorySearch, true)
                    },
                    search = directorySearch, page = directoryPage,
                    totalCount = directory?.totalCount ?: 0, loading = loading, busy = busy,
                    error = error, onSearch = { directorySearch = it },
                    onBack = { page = GuildPage.Home }, onJoin = ::joinGuild,
                    onCreate = ::beginCreate,
                    onPrevious = { directoryPage = (directoryPage - 1).coerceAtLeast(1) },
                    onNext = { directoryPage += 1 }
                )
                GuildPage.StudyingNow -> GuildStudyingNowContent(
                    timers = publicTimers, clockOffsetMs = activeTimerClockOffset,
                    loading = loading, error = error, onBack = { page = GuildPage.Home }
                )
                GuildPage.Create -> GuildEditorContent(
                    title = "Create Guild", isEdit = false, step = wizardStep,
                    name = draftName, description = draftDescription, isPublic = draftPublic,
                    bannerUrl = draftBannerUrl, pictureUrl = draftPictureUrl, colour = draftColour,
                    invitees = draftInvitees, busy = busy, error = error,
                    onBack = { if (wizardStep > 1) wizardStep -= 1 else page = GuildPage.Home },
                    onName = { draftName = it }, onDescription = { draftDescription = it },
                    onPrivacy = { draftPublic = it },
                    onBanner = { imageTarget = "banner"; imagePicker.launch("image/*") },
                    onPicture = { imageTarget = "picture"; imagePicker.launch("image/*") },
                    onColour = { draftColour = it }, onInvitees = { draftInvitees = it },
                    onNext = {
                        if (wizardStep == 1 && draftName.trim().length < 3) {
                            error = "Guild names must contain at least 3 characters."
                        } else if (wizardStep < 4) {
                            error = null
                            wizardStep += 1
                        } else saveGuild()
                    },
                    onSave = ::saveGuild
                )
                GuildPage.Edit -> GuildEditorContent(
                    title = "Edit Guild", isEdit = true, step = 2,
                    name = draftName, description = draftDescription, isPublic = draftPublic,
                    bannerUrl = draftBannerUrl, pictureUrl = draftPictureUrl, colour = draftColour,
                    invitees = "", busy = busy, error = error,
                    onBack = { page = GuildPage.Home }, onName = { draftName = it },
                    onDescription = { draftDescription = it }, onPrivacy = { draftPublic = it },
                    onBanner = { imageTarget = "banner"; imagePicker.launch("image/*") },
                    onPicture = { imageTarget = "picture"; imagePicker.launch("image/*") },
                    onColour = { draftColour = it }, onInvitees = {},
                    onNext = ::saveGuild, onSave = ::saveGuild
                )
                GuildPage.Invitations -> GuildInvitationsContent(
                    data = invitations, outgoingTab = outgoingInvitations, busy = busy, error = error,
                    onBack = { page = GuildPage.Home }, onTab = { outgoingInvitations = it },
                    onRespond = ::respondToInvitation
                )
            }

            if (error != null && !(page == GuildPage.Home && currentGuild != null) &&
                page !in listOf(GuildPage.Chat, GuildPage.Bazaar, GuildPage.StudyingNow)
            ) {
                Text(error.orEmpty(), color = Color(0xFFB42318), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            if (infoMessage != null && !(page == GuildPage.Home && currentGuild != null)) {
                Text(infoMessage.orEmpty(), color = Color(0xFF246CCB), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }

    if (confirmAction != null) {
        val action = confirmAction ?: "leave"
        AlertDialog(
            onDismissRequest = { if (!busy) confirmAction = null },
            title = { Text(if (action == "leave") "Leave Guild?" else "Disband Guild?") },
            text = {
                Text(if (action == "leave") {
                    "You will lose access to this Guild's members and conversation. You can join another public Guild later."
                } else {
                    "This permanently removes the Guild, its membership, invitations and chat history."
                })
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { performConfirmedAction(action) }) {
                    Text(if (action == "leave") "Leave" else "Disband", color = Color(0xFFB42318))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }, enabled = !busy) { Text("Cancel") }
            },
            containerColor = GuildSurface
        )
    }
}

@Composable
private fun GuildHomeContent(
    guild: GuildData?, currentUserId: String, members: List<GuildMemberData>, statuses: Map<String, String>,
    elapsedMs: Long, timerRunning: Boolean, loading: Boolean,
    onCreate: () -> Unit, onOpenBazaar: () -> Unit, onOpenChat: () -> Unit,
    onOpenStudying: () -> Unit, onOpenInvitations: () -> Unit, onEdit: () -> Unit,
    onLeave: () -> Unit, onDisband: () -> Unit, onTimerToggle: () -> Unit,
    onTimerReset: () -> Unit, onOpenTimer: () -> Unit, isOwner: Boolean,
    inviteUsername: String, onInviteUsername: (String) -> Unit, onInvite: () -> Unit, busy: Boolean
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Guilds", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GuildText)
                Text("Your study guild", fontSize = 13.sp, color = GuildMuted)
            }
            GuildRoundAction("+", "Create Guild", onCreate)
        }
        if (loading && guild == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GuildBlue) }
        } else if (guild == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Find your study people", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuildText)
                    Text("Create a Guild or discover a public community to study together.", color = GuildMuted, fontSize = 13.sp)
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth(), colors = guildButtonColors()) { Text("Create a Guild") }
                    OutlinedButton(onClick = onOpenBazaar, modifier = Modifier.fillMaxWidth()) { Text("Explore Guild Bazaar") }
                }
            }
            GuildQuickLinks(onOpenBazaar, onOpenInvitations, onOpenStudying)
        } else {
            GuildHeroCard(guild, onOpenChat)
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your study time", color = GuildMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(formatGuildDuration(elapsedMs), color = GuildText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text("Synced with your Timer tab", color = GuildMuted, fontSize = 11.sp)
                        }
                        Text("♧", color = GuildBlue, fontSize = 24.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onTimerReset, modifier = Modifier.weight(1f), enabled = elapsedMs > 0L, shape = RoundedCornerShape(28.dp)) { Text("Reset") }
                        Button(
                            onClick = if (timerRunning) onTimerToggle else onOpenTimer,
                            modifier = Modifier.weight(1f), colors = guildButtonColors(), shape = RoundedCornerShape(28.dp)
                        ) { Text(if (timerRunning) "Pause" else "Open timer") }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChat),
                shape = RoundedCornerShape(16.dp), color = Color(0xFFEAF1FF)
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▤", color = GuildBlue, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("# general", color = GuildText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Guild conversation", color = GuildMuted, fontSize = 11.sp)
                    }
                    Text("›", color = GuildMuted, fontSize = 22.sp)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Members", fontWeight = FontWeight.Bold, color = GuildText, modifier = Modifier.weight(1f))
                        Text(guild.displayMemberCount.toString(), color = GuildMuted, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onOpenStudying) { Text("Studying now") }
                    }
                    members.take(8).forEach { member ->
                        GuildMemberLine(
                            member = member,
                            status = statuses[member.stableId] ?: "idle",
                            current = member.stableId.isNotBlank() && member.stableId == currentUserId
                        )
                    }
                    if (members.isEmpty()) Text("Your Guild is ready. Invite members to get started.", color = GuildMuted, fontSize = 12.sp)
                    if (isOwner) {
                        OutlinedTextField(
                            value = inviteUsername, onValueChange = onInviteUsername,
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Invite by username") }, placeholder = { Text("Enter exact username") },
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(onClick = onInvite, modifier = Modifier.fillMaxWidth(), enabled = inviteUsername.isNotBlank() && !busy, colors = guildButtonColors()) {
                            Text(if (busy) "Sending…" else "Send invitation")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit Guild") }
                            OutlinedButton(onClick = onDisband, modifier = Modifier.weight(1f)) { Text("Disband") }
                        }
                    } else {
                        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Leave Guild") }
                    }
                }
            }
            GuildQuickLinks(onOpenBazaar, onOpenInvitations, onOpenStudying)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GuildQuickLinks(onBazaar: () -> Unit, onInvitations: () -> Unit, onStudying: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GuildQuickLink("Bazaar", "Discover", onBazaar, Modifier.weight(1f))
        GuildQuickLink("Invitations", "Received / sent", onInvitations, Modifier.weight(1f))
        GuildQuickLink("Studying", "Live activity", onStudying, Modifier.weight(1f))
    }
}

@Composable
private fun GuildQuickLink(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(15.dp),
        color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GuildText, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = GuildMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GuildHeroCard(guild: GuildData, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)
    ) {
        Column {
            GuildRemoteImage(guild.bannerUrl, Modifier.fillMaxWidth().height(100.dp), "Guild banner")
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GuildAvatar(guild.pictureUrl, guild.name, 54.dp, guild.colour)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(guild.name, fontWeight = FontWeight.Bold, color = GuildText, fontSize = 16.sp, maxLines = 1)
                    Text(
                        guild.displayMemberCount.toString() + " members  ·  " + if (guild.isPublic) "Public" else "Private",
                        color = GuildMuted, fontSize = 11.sp
                    )
                }
                Text("›", color = GuildMuted, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun GuildMemberLine(member: GuildMemberData, status: String, current: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        GuildAvatar(member.imageUrl, member.username, 34.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(member.username + if (current) " (You)" else "", color = GuildText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (member.role == "owner") {
                    Spacer(Modifier.width(5.dp))
                    Text("♛", color = Color(0xFFDAA52E), fontSize = 14.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = when (status.lowercase()) { "working" -> Color(0xFF45B96D); "paused" -> Color(0xFFF0B43B); else -> Color(0xFF9AA5B5) }
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(5.dp))
                Text(when (status.lowercase()) { "working" -> "Working"; "paused" -> "Paused"; else -> "Idle" }, color = GuildMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun GuildChatContent(
    guild: GuildData?, members: List<GuildMemberData>, userId: String,
    messages: List<GuildMessageData>, draft: String, sending: Boolean, error: String?,
    onBack: () -> Unit, onDraftChange: (String) -> Unit, onSend: () -> Unit, onGuildInfo: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize()) {
        GuildSubHeader(guild?.name ?: "Guild chat", "# general", onBack, "ⓘ", onGuildInfo)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "No messages yet. Start the conversation.", color = GuildMuted, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == userId
                val sender = members.firstOrNull { it.stableId == message.senderId }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!mine) {
                        GuildAvatar(sender?.imageUrl, sender?.username ?: "Member", 30.dp)
                        Spacer(Modifier.width(7.dp))
                    }
                    Column(Modifier.widthIn(max = 280.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        if (!mine) Text(sender?.username ?: "Guild member", fontSize = 10.sp, color = GuildText, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 15.dp, topEnd = 15.dp,
                                bottomStart = if (mine) 15.dp else 4.dp,
                                bottomEnd = if (mine) 4.dp else 15.dp
                            ),
                            color = if (mine) Color(0xFFD8E9FF) else Color(0xFFE8ECF3)
                        ) {
                            Text(message.body, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = GuildText, fontSize = 13.sp)
                        }
                        Text(formatGuildTime(message.createdAt), fontSize = 9.sp, color = GuildMuted, modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp))
                    }
                }
            }
        }
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFB42318), fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft, onValueChange = onDraftChange, modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message…") }, maxLines = 4,
                shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                colors = guildTextFieldColors()
            )
            Surface(
                modifier = Modifier.size(46.dp).clip(CircleShape).clickable(enabled = draft.isNotBlank() && !sending, onClick = onSend),
                shape = CircleShape, color = if (draft.isNotBlank() && !sending) GuildBlue else Color(0xFFB7C3D3)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("➤", color = Color.White, fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun GuildBazaarContent(
    guilds: List<GuildData>, search: String, page: Int, totalCount: Int,
    loading: Boolean, busy: Boolean, error: String?,
    onSearch: (String) -> Unit, onBack: () -> Unit, onJoin: (GuildData) -> Unit,
    onCreate: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GuildSubHeader("Guild Bazaar", "Discover public Guilds", onBack)
        OutlinedTextField(
            value = search, onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            singleLine = true, placeholder = { Text("Search guilds…") },
            leadingIcon = { Text("⌕", color = GuildMuted, fontSize = 20.sp) },
            shape = RoundedCornerShape(16.dp), colors = guildTextFieldColors()
        )
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFB42318), fontSize = 11.sp)
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GuildBlue) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(guilds, key = { it.id }) { item ->
                    Surface(shape = RoundedCornerShape(17.dp), color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GuildAvatar(item.pictureUrl, item.name, 54.dp, item.colour)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.name, color = GuildText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                Text(item.displayMemberCount.toString() + " members", color = GuildMuted, fontSize = 10.sp)
                                Text(item.description.orEmpty(), color = GuildMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = { onJoin(item) }, enabled = !busy,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = guildButtonColors()
                            ) { Text("Join", fontSize = 11.sp) }
                        }
                    }
                }
                if (guilds.isEmpty()) {
                    item {
                        Text(
                            if (search.isBlank()) "No public Guilds found." else "No Guilds match that search.",
                            color = GuildMuted, modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onPrevious, enabled = page > 1) { Text("Previous") }
                Text("Page $page", color = GuildMuted, fontSize = 11.sp)
                TextButton(onClick = onNext, enabled = totalCount > 0 && page * 10 < totalCount) { Text("Next") }
            }
        }
        OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create your own Guild") }
    }
}

@Composable
private fun GuildStudyingNowContent(
    timers: List<GuildPublicTimerData>, clockOffsetMs: Long, loading: Boolean, error: String?, onBack: () -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) { delay(1_000L); now = System.currentTimeMillis() }
    }
    Column(Modifier.fillMaxSize()) {
        GuildSubHeader("Studying now", "People currently studying", onBack)
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFB42318), fontSize = 11.sp)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GuildBlue) }
        } else if (timers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nobody is publicly studying right now.", color = GuildMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(timers, key = { it.username }) { person ->
                    val started = person.startedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    val running = started?.let { max(0L, now + clockOffsetMs - it) } ?: 0L
                    val duration = person.elapsedMs + running
                    Surface(shape = RoundedCornerShape(17.dp), color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GuildAvatar(person.imageUrl, person.username, 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.username, color = GuildText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF45B96D)))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Studying", color = GuildMuted, fontSize = 10.sp)
                                }
                            }
                            Surface(shape = RoundedCornerShape(16.dp), color = GuildBlueSoft) {
                                Text(formatGuildDuration(duration), color = Color(0xFF315F9B), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuildInvitationsContent(
    data: GuildInvitationsData, outgoingTab: Boolean, busy: Boolean, error: String?,
    onBack: () -> Unit, onTab: (Boolean) -> Unit, onRespond: (GuildInvitationData, Boolean) -> Unit
) {
    val entries = if (outgoingTab) data.outgoing else data.incoming
    Column(Modifier.fillMaxSize()) {
        GuildSubHeader("Invitations", "Guild invitations", onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFFE7EDF8)).padding(4.dp)
        ) {
            GuildTab("Received " + data.incoming.size, !outgoingTab, Modifier.weight(1f)) { onTab(false) }
            GuildTab("Sent " + data.outgoing.size, outgoingTab, Modifier.weight(1f)) { onTab(true) }
        }
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFB42318), fontSize = 11.sp)
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (outgoingTab) "No sent invitations." else "No invitations received.", color = GuildMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(entries, key = { it.id }) { invite ->
                    Surface(shape = RoundedCornerShape(17.dp), color = GuildSurface, border = androidx.compose.foundation.BorderStroke(1.dp, GuildBorder)) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GuildAvatar(null, invite.guildName, 42.dp, "#8B65D9")
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(invite.guildName, color = GuildText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        if (outgoingTab) "Invitation sent to " + (invite.username ?: "a user")
                                        else invite.inviterUsername + " invited you to join " + invite.guildName + ".",
                                        color = GuildMuted, fontSize = 11.sp
                                    )
                                }
                                Text(formatGuildAge(invite.createdAt), color = GuildMuted, fontSize = 9.sp)
                            }
                            if (!outgoingTab) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onRespond(invite, true) }, enabled = !busy, modifier = Modifier.weight(1f), colors = guildButtonColors()) { Text("Accept") }
                                    OutlinedButton(onClick = { onRespond(invite, false) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Decline") }
                                }
                            } else {
                                Text("Pending", color = GuildMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuildEditorContent(
    title: String, isEdit: Boolean, step: Int, name: String, description: String, isPublic: Boolean,
    bannerUrl: String?, pictureUrl: String?, colour: String, invitees: String, busy: Boolean, error: String?,
    onBack: () -> Unit, onName: (String) -> Unit, onDescription: (String) -> Unit,
    onPrivacy: (Boolean) -> Unit, onBanner: () -> Unit, onPicture: () -> Unit,
    onColour: (String) -> Unit, onInvitees: (String) -> Unit, onNext: () -> Unit, onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GuildSubHeader(title, if (isEdit) "Customise your Guild" else "Create your study community", onBack)
        if (!isEdit) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                (1..4).forEach { index ->
                    Surface(shape = CircleShape, color = if (index <= step) GuildBlue else Color(0xFFE5EAF2)) {
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                            Text(if (index < step) "✓" else index.toString(), color = if (index <= step) Color.White else GuildMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !isEdit && step == 1 -> {
                    Text("Details", color = GuildText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Basic information about your Guild", color = GuildMuted, fontSize = 12.sp)
                    GuildField("Guild name", name, onName, "Revision Guild")
                    GuildField("Description", description, onDescription, "A space to study, stay accountable and make progress together.", minLines = 3)
                    Text("Privacy", color = GuildText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    GuildPrivacyOption("Public", "Anyone can find and join.", true, isPublic, onPrivacy)
                    GuildPrivacyOption("Private", "Only by invitation.", false, isPublic, onPrivacy)
                }
                (!isEdit && step == 2) || isEdit -> {
                    Text("Appearance", color = GuildText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Customise your Guild", color = GuildMuted, fontSize = 12.sp)
                    Text("Banner (optional)", color = GuildText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(112.dp).clickable(onClick = onBanner),
                        shape = RoundedCornerShape(17.dp), color = Color(0xFFDCE7F6)
                    ) {
                        Box {
                            GuildRemoteImage(bannerUrl, Modifier.fillMaxSize(), "Choose a banner")
                            Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.94f)) {
                                Text("▧", color = GuildBlue, modifier = Modifier.padding(9.dp))
                            }
                        }
                    }
                    Text("Guild picture", color = GuildText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GuildAvatar(pictureUrl, name.ifBlank { "Guild" }, 78.dp, colour)
                        OutlinedButton(onClick = onPicture) { Text("Choose picture") }
                    }
                    Text("Accent colour", color = GuildText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GuildPalette.forEach { option ->
                            val selected = option.equals(colour, true)
                            Box(
                                modifier = Modifier.size(if (selected) 38.dp else 32.dp).clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(option)))
                                    .then(if (selected) Modifier.border(2.dp, GuildBlue, CircleShape).padding(3.dp) else Modifier)
                                    .clickable { onColour(option) }
                            )
                        }
                    }
                    if (isEdit) {
                        Text("Guild details", color = GuildText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        GuildField("Guild name", name, onName, "Guild name")
                        GuildField("Description", description, onDescription, "Guild description", minLines = 3)
                        Text("Privacy", color = GuildText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        GuildPrivacyOption("Public", "Anyone can find and join.", true, isPublic, onPrivacy)
                        GuildPrivacyOption("Private", "Only by invitation.", false, isPublic, onPrivacy)
                    }
                }
                !isEdit && step == 3 -> {
                    Text("Guild guidelines", color = GuildText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Help members understand how your study community works.", color = GuildMuted, fontSize = 12.sp)
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEAF1FF)) {
                        Text(
                            "Guidelines editing is not part of the current Guild backend contract. You can create the Guild now and manage its community through membership and chat.",
                            modifier = Modifier.padding(14.dp), color = GuildText, fontSize = 12.sp
                        )
                    }
                }
                !isEdit && step == 4 -> {
                    Text("Invite members", color = GuildText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Optional. Enter exact usernames separated by commas or new lines.", color = GuildMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = invitees, onValueChange = onInvitees,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                        placeholder = { Text("alex, fern, salman") }, minLines = 4,
                        shape = RoundedCornerShape(15.dp), colors = guildTextFieldColors()
                    )
                }
            }
            if (!error.isNullOrBlank()) Text(error, color = Color(0xFFB42318), fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            if (isEdit) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !busy) { Text("Cancel") }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !busy, colors = guildButtonColors()) { Text(if (busy) "Saving…" else "Save changes") }
            } else {
                if (step > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !busy) { Text("Back") }
                Button(onClick = onNext, modifier = Modifier.weight(1f), enabled = !busy, colors = guildButtonColors()) {
                    Text(when { busy -> "Saving…"; step == 4 -> "Create Guild"; else -> "Next" })
                }
            }
        }
    }
}

@Composable
private fun GuildField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = GuildText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) }, minLines = minLines, maxLines = if (minLines > 1) 5 else 1,
            shape = RoundedCornerShape(13.dp), colors = guildTextFieldColors()
        )
    }
}

@Composable
private fun GuildPrivacyOption(title: String, description: String, value: Boolean, selected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable { onSelect(value) }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Column {
            Text(title, color = GuildText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = GuildMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun GuildSubHeader(title: String, subtitle: String, onBack: () -> Unit, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
        Surface(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack), shape = CircleShape, color = GuildSurface) {
            Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 27.sp, color = GuildText) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = GuildText, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = GuildMuted, fontSize = 11.sp)
        }
        if (action != null && onAction != null) {
            Surface(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onAction), shape = CircleShape, color = GuildSurface) {
                Box(contentAlignment = Alignment.Center) { Text(action, color = GuildMuted, fontSize = 19.sp) }
            }
        }
    }
}

@Composable
private fun GuildTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick), color = if (selected) GuildBlueSoft else Color.Transparent, shape = RoundedCornerShape(22.dp)) {
        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) GuildBlue else GuildMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GuildRoundAction(symbol: String, description: String, onClick: () -> Unit) {
    Surface(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), shape = CircleShape, color = GuildBlueSoft) {
        Box(contentAlignment = Alignment.Center) { Text(symbol, color = GuildBlue, fontSize = 24.sp) }
    }
}

@Composable
internal fun GuildAvatar(url: String?, label: String, size: Dp, accent: String? = null) {
    val background = accent?.takeIf { it.matches(Regex("^#[0-9a-fA-F]{6}$")) }
        ?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color(0xFFDCE9FA)
    Surface(Modifier.size(size), shape = CircleShape, color = background) {
        Box(contentAlignment = Alignment.Center) {
            if (!url.isNullOrBlank()) {
                GuildRemoteImage(url, Modifier.fillMaxSize(), label, isCircle = true)
            } else {
                Text(label.trim().firstOrNull()?.uppercase() ?: "G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
            }
        }
    }
}

@Composable
internal fun GuildRemoteImage(url: String?, modifier: Modifier = Modifier, placeholder: String = "Image", isCircle: Boolean = false) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val parsed = URL(url)
                require(parsed.protocol == "https")
                val connection = parsed.openConnection() as? HttpURLConnection ?: return@runCatching null
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = true
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 8 * 1024 * 1024) return@runCatching null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = if (isCircle) null else placeholder,
            modifier = modifier.then(if (isCircle) Modifier.clip(CircleShape) else Modifier),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.background(Color(0xFFDCE7F6)), contentAlignment = Alignment.Center) {
            Text(if (placeholder == "Image") "▧" else placeholder, color = GuildMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun guildButtonColors() = ButtonDefaults.buttonColors(
    containerColor = GuildBlue, contentColor = Color.White,
    disabledContainerColor = Color(0xFFB8C9E1), disabledContentColor = Color.White
)

@Composable
private fun guildTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GuildBlue, unfocusedBorderColor = GuildBorder,
    focusedContainerColor = Color(0xFFF8FAFE), unfocusedContainerColor = Color(0xFFF8FAFE),
    focusedTextColor = GuildText, unfocusedTextColor = GuildText,
    focusedPlaceholderColor = GuildMuted, unfocusedPlaceholderColor = GuildMuted
)

private fun formatGuildDuration(milliseconds: Long): String {
    val total = (milliseconds / 1000L).coerceAtLeast(0L)
    val days = total / 86_400L
    val hours = (total % 86_400L) / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return if (days > 0L) {
        String.format(Locale.getDefault(), "%dd %02d:%02d:%02d", days, hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

private fun formatGuildTime(value: String): String = runCatching {
    Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
}.getOrDefault("")


private fun mergeGuildMessageLists(
    first: List<GuildMessageData>,
    second: List<GuildMessageData>
): List<GuildMessageData> {
    val byId = LinkedHashMap<String, GuildMessageData>()
    first.forEach { byId[it.id] = it }
    second.forEach { byId[it.id] = it }
    return byId.values.sortedBy { it.createdAt }
}

private fun formatGuildAge(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val created = runCatching { Instant.parse(value) }.getOrNull() ?: return ""
    val minutes = Duration.between(created, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 60 -> minutes.toString() + "m ago"
        minutes < 1_440 -> (minutes / 60).toString() + "h ago"
        else -> (minutes / 1_440).toString() + "d ago"
    }
}
