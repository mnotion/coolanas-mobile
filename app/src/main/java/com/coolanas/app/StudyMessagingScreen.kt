package com.coolanas.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
private data class CoolanasFriendRow(
    @SerialName("friend_id") val friendId: String,
    val username: String,
    @SerialName("google_avatar") val googleAvatar: String? = null
)

@Serializable
private data class CoolanasFriendRequestRow(
    @SerialName("friendship_id") val friendshipId: String,
    @SerialName("user_id") val userId: String,
    val username: String,
    val direction: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("google_avatar") val googleAvatar: String? = null
)

@Serializable
private data class CoolanasFriendSearchRow(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("friendship_status") val friendshipStatus: String? = null,
    @SerialName("google_avatar") val googleAvatar: String? = null,
    @SerialName("matched_username") val matchedUsername: String? = null
)

@Serializable
private data class CoolanasDmTypingPayload(
    val userId: String,
    val typing: Boolean
)

@Serializable
private data class CoolanasUnreadSenderRow(
    @SerialName("sender_id") val senderId: String
)

@Serializable
private data class CoolanasDmRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val ciphertext: JsonObject,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null
) {
    fun messageText(): String =
        if (ciphertext["v"]?.jsonPrimitive?.contentOrNull == "0") {
            ciphertext["plaintext"]?.jsonPrimitive?.contentOrNull.orEmpty()
        } else {
            "This message uses an unsupported message format."
        }
}

private object MessagingPalette {
    val background = Color(0xFFF4F6FB)
    val surface = Color.White
    val chatSurface = Color(0xFFF8FAFE)
    val field = Color(0xFFF1F4F9)
    val outline = Color(0xFFD9E0EA)
    val text = Color(0xFF172033)
    val secondary = Color(0xFF68758A)
    val blue = Color(0xFF347FDF)
    val blueSoft = Color(0xFFDCE9FF)
    val bluePale = Color(0xFFE8F0FC)
    val incomingBubble = Color(0xFFE9EDF3)
    val divider = Color(0xFFE3E8F0)
    val avatar = Color(0xFFE3ECF9)
    val avatarText = Color(0xFF315F9B)
    val error = Color(0xFFB42318)
}

@Composable
private fun messagingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MessagingPalette.text,
    unfocusedTextColor = MessagingPalette.text,
    focusedContainerColor = MessagingPalette.surface,
    unfocusedContainerColor = MessagingPalette.field,
    focusedBorderColor = MessagingPalette.blue,
    unfocusedBorderColor = MessagingPalette.outline,
    focusedPlaceholderColor = MessagingPalette.secondary,
    unfocusedPlaceholderColor = MessagingPalette.secondary,
    cursorColor = MessagingPalette.blue
)

private fun downloadMessagingAvatar(imageUrl: String?): Bitmap? {
    if (imageUrl.isNullOrBlank()) return null
    var connection: HttpURLConnection? = null
    return try {
        val parsed = URL(imageUrl)
        if (parsed.protocol != "https") return null
        connection = parsed.openConnection() as? HttpURLConnection ?: return null
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        connection.connect()
        if (connection.responseCode !in 200..299) return null
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(4_096)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > 3 * 1024 * 1024) return null
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 256).coerceAtLeast(1)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

@Composable
fun StudyMessagingScreen(userId: String) {
    val scope = rememberCoroutineScope()
    var friends by remember(userId) { mutableStateOf<List<CoolanasFriendRow>>(emptyList()) }
    var requests by remember(userId) { mutableStateOf<List<CoolanasFriendRequestRow>>(emptyList()) }
    var search by remember(userId) { mutableStateOf("") }
    var searchResults by remember(userId) { mutableStateOf<List<CoolanasFriendSearchRow>>(emptyList()) }
    var selectedFriend by remember(userId) { mutableStateOf<CoolanasFriendRow?>(null) }
    var conversationId by remember(userId) { mutableStateOf<String?>(null) }
    var conversationOpening by remember(userId) { mutableStateOf(false) }
    var messages by remember(userId) { mutableStateOf<List<CoolanasDmRow>>(emptyList()) }
    var draft by remember(userId) { mutableStateOf("") }
    var loading by remember(userId) { mutableStateOf(true) }
    var sending by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var requestsVisible by remember(userId) { mutableStateOf(false) }
    var friendListVersion by remember(userId) { mutableStateOf(0) }
    var unreadCounts by remember(userId) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var friendAvatars by remember(userId) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var activeTypingChannel by remember(userId) { mutableStateOf<RealtimeChannel?>(null) }
    var remoteTyping by remember(userId) { mutableStateOf(false) }
    var typingHeartbeat by remember(userId) { mutableStateOf<Job?>(null) }
    val messageListState = rememberLazyListState()

    suspend fun loadFriends() {
        friends = withContext(Dispatchers.IO) {
            SupabaseClientProvider.client.postgrest.rpc("coolanas_friend_list")
                .decodeList<CoolanasFriendRow>()
        }
    }

    suspend fun loadRequests() {
        requests = withContext(Dispatchers.IO) {
            SupabaseClientProvider.client.postgrest.rpc("coolanas_friend_requests")
                .decodeList<CoolanasFriendRequestRow>()
        }
    }

    suspend fun refreshUnreadCounts() {
        val friendIds = friends.map { it.friendId }
        if (friendIds.isEmpty()) {
            unreadCounts = emptyMap()
            return
        }
        val rows = withContext(Dispatchers.IO) {
            SupabaseClientProvider.client.postgrest.from("coolanas_dm_messages").select {
                filter {
                    isIn("sender_id", friendIds)
                    exact("read_at", null)
                }
            }.decodeList<CoolanasUnreadSenderRow>()
        }
        unreadCounts = rows.groupingBy { it.senderId }.eachCount()
    }

    fun broadcastTyping(typing: Boolean) {
        val channel = activeTypingChannel ?: return
        scope.launch {
            runCatching {
                channel.broadcast("typing", CoolanasDmTypingPayload(userId, typing))
            }
        }
    }

    LaunchedEffect(userId, friendListVersion) {
        loading = true
        error = null
        try {
            loadFriends()
            loadRequests()
            refreshUnreadCounts()
        } catch (exception: Exception) {
            error = exception.message ?: "Could not load your friends."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(userId) {
        while (true) {
            delay(5_000L)
            runCatching { refreshUnreadCounts() }
        }
    }

    LaunchedEffect(friends, searchResults) {
        val knownIds = friendAvatars.keys
        val sources = (friends.map { it.friendId to it.googleAvatar } +
            searchResults.map { it.userId to it.googleAvatar })
            .filter { it.first !in knownIds && !it.second.isNullOrBlank() }
            .distinctBy { it.first }
        if (sources.isNotEmpty()) {
            val downloaded = withContext(Dispatchers.IO) {
                sources.mapNotNull { (id, url) -> downloadMessagingAvatar(url)?.let { id to it } }.toMap()
            }
            friendAvatars = friendAvatars + downloaded
        }
    }

    LaunchedEffect(search, userId) {
        if (search.trim().length < 2) {
            searchResults = emptyList()
        } else {
            delay(260L)
            try {
                searchResults = withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest.rpc(
                        function = "coolanas_friend_search",
                        parameters = buildJsonObject { put("p_query", JsonPrimitive(search.trim())) }
                    ).decodeList<CoolanasFriendSearchRow>()
                }
            } catch (exception: Exception) {
                error = exception.message ?: "Search failed."
            }
        }
    }

    LaunchedEffect(conversationId, userId) {
        val activeConversation = conversationId ?: return@LaunchedEffect
        messages = emptyList()
        val channel = SupabaseClientProvider.client.realtime.channel("sca-dm-$activeConversation")
        activeTypingChannel = channel
        val typingFlow = channel.broadcastFlow<CoolanasDmTypingPayload>("typing")
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "coolanas_dm_messages"
            filter = "conversation_id=eq.$activeConversation"
        }
        var typingExpiryJob: Job? = null
        val typingObserver = launch {
            typingFlow.collect { payload ->
                if (payload.userId != userId) {
                    typingExpiryJob?.cancel()
                    remoteTyping = payload.typing
                    if (payload.typing) {
                        typingExpiryJob = launch {
                            delay(5_500L)
                            remoteTyping = false
                        }
                    }
                }
            }
        }
        val observer = launch {
            insertFlow.collect { action ->
                val row = runCatching { action.decodeRecord<CoolanasDmRow>() }.getOrNull() ?: return@collect
                if (row.conversationId != activeConversation) return@collect
                if (messages.none { it.id == row.id }) {
                    messages = (messages + row).sortedWith(compareBy<CoolanasDmRow> { it.createdAt }.thenBy { it.id })
                }
                if (row.senderId != userId) {
                    markConversationRead(activeConversation, userId)
                    refreshUnreadCounts()
                }
            }
        }
        try {
            channel.subscribe(blockUntilSubscribed = false)
        } catch (_: Exception) {
            // Initial and manual reloads remain available if Realtime is unavailable.
        }
        try {
            val initial = withContext(Dispatchers.IO) {
                SupabaseClientProvider.client.postgrest.from("coolanas_dm_messages").select {
                    filter { eq("conversation_id", activeConversation) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    limit(150)
                }.decodeList<CoolanasDmRow>()
            }
            messages = initial
            markConversationRead(activeConversation, userId)
        } catch (exception: Exception) {
            error = exception.message ?: "Could not load this conversation."
        }
        try {
            while (true) {
                delay(5_000L)
                val latest = withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client.postgrest.from("coolanas_dm_messages").select {
                        filter { eq("conversation_id", activeConversation) }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                        order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                        limit(150)
                    }.decodeList<CoolanasDmRow>()
                }
                messages = latest
                markConversationRead(activeConversation, userId)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "Live messages could not refresh."
        } finally {
            typingExpiryJob?.cancel()
            typingObserver.cancel()
            observer.cancel()
            if (activeTypingChannel === channel) activeTypingChannel = null
            remoteTyping = false
            typingHeartbeat?.cancel()
            typingHeartbeat = null
            SupabaseClientProvider.client.realtime.removeChannel(channel)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) messageListState.animateScrollToItem(messages.lastIndex)
    }

    val incomingRequests = requests.filter { it.direction == "incoming" }
    val outgoingRequests = requests.filter { it.direction == "outgoing" }
    var addFriendMode by remember(userId) { mutableStateOf(false) }
    var friendTab by remember(userId) { mutableStateOf("search") }
    var conversationSearchVisible by remember(userId) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MessagingPalette.background)) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectedFriend != null) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(Modifier.size(40.dp).noRippleClickable {
                    typingHeartbeat?.cancel()
                    typingHeartbeat = null
                    broadcastTyping(false)
                    selectedFriend = null
                    conversationId = null
                    remoteTyping = false
                    messages = emptyList()
                    draft = ""
                }, shape = CircleShape, color = MessagingPalette.bluePale) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = MessagingPalette.blue, fontSize = 30.sp, lineHeight = 30.sp)
                    }
                }
                Surface(Modifier.size(42.dp), shape = CircleShape, color = MessagingPalette.avatar) {
                    val avatar = selectedFriend?.let { friendAvatars[it.friendId] }
                    if (avatar != null) {
                        Image(bitmap = avatar.asImageBitmap(), contentDescription = "${selectedFriend?.username} profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(selectedFriend?.username?.take(1)?.uppercase().orEmpty(), color = MessagingPalette.avatarText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(selectedFriend?.username.orEmpty(), color = MessagingPalette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (remoteTyping) "Typing…" else "Coolanas friend", color = if (remoteTyping) MessagingPalette.blue else MessagingPalette.secondary, fontSize = 12.sp)
                }
                Text("ⓘ", color = MessagingPalette.secondary, fontSize = 22.sp)
            }

            if (conversationOpening || conversationId == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (conversationOpening) "Opening conversation…" else "Could not open conversation. Go back and try again.", color = MessagingPalette.secondary, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .background(MessagingPalette.chatSurface, RoundedCornerShape(22.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    state = messageListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (messages.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                            Text("Start the conversation", color = Color(0xFF969BA6), fontSize = 13.sp)
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val mine = message.senderId == userId
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                            Column(
                                modifier = Modifier.fillMaxWidth(0.76f).background(
                                    if (mine) MessagingPalette.blueSoft else MessagingPalette.incomingBubble,
                                    RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp, bottomEnd = if (mine) 5.dp else 17.dp, bottomStart = if (mine) 17.dp else 5.dp)
                                ).padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Text(message.messageText(), color = MessagingPalette.text, fontSize = 15.sp, lineHeight = 21.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    val time = runCatching { Instant.parse(message.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
                                    Text(time, color = MessagingPalette.secondary, fontSize = 10.sp)
                                    if (mine) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (message.readAt == null) "✓" else "✓✓", color = MessagingPalette.blue, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (remoteTyping) item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Surface(shape = RoundedCornerShape(18.dp), color = MessagingPalette.incomingBubble) {
                                Text("•••", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = MessagingPalette.secondary, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { value ->
                            draft = value.take(300)
                            typingHeartbeat?.cancel()
                            if (draft.isNotBlank()) {
                                broadcastTyping(true)
                                typingHeartbeat = scope.launch {
                                    while (isActive) { delay(2_000L); broadcastTyping(true) }
                                }
                            } else {
                                broadcastTyping(false)
                                typingHeartbeat = null
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 112.dp),
                        placeholder = { Text("Write a message…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = messagingFieldColors()
                    )
                    Surface(Modifier.size(48.dp).noRippleClickable {
                        val outgoing = draft.trim()
                        val activeConversation = conversationId
                        typingHeartbeat?.cancel()
                        typingHeartbeat = null
                        broadcastTyping(false)
                        if (outgoing.isNotBlank() && activeConversation != null && !sending) {
                            val optimistic = CoolanasDmRow(
                                id = UUID.randomUUID().toString(), conversationId = activeConversation, senderId = userId,
                                ciphertext = buildJsonObject { put("v", JsonPrimitive(0)); put("plaintext", JsonPrimitive(outgoing)) },
                                createdAt = Instant.now().toString(), readAt = null
                            )
                            messages = messages + optimistic
                            draft = ""
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        SupabaseClientProvider.client.postgrest.from("coolanas_dm_messages").insert(
                                            buildJsonObject {
                                                put("id", JsonPrimitive(optimistic.id))
                                                put("conversation_id", JsonPrimitive(activeConversation))
                                                put("sender_id", JsonPrimitive(userId))
                                                put("ciphertext", optimistic.ciphertext)
                                            }
                                        )
                                    }
                                } catch (exception: Exception) {
                                    messages = messages.filterNot { it.id == optimistic.id }
                                    if (draft.isBlank()) draft = outgoing
                                    error = exception.message ?: "Message could not be sent."
                                } finally { sending = false }
                            }
                        }
                    }, shape = CircleShape, color = if (draft.isNotBlank() && !sending) MessagingPalette.blue else Color(0xFFB7C2D1)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("➤", color = Color.White, fontSize = 21.sp) }
                    }
                }
            }
        } else if (addFriendMode) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", modifier = Modifier.noRippleClickable { addFriendMode = false }.padding(end = 12.dp), color = MessagingPalette.blue, fontSize = 30.sp)
                Text("Add friend", modifier = Modifier.weight(1f), color = MessagingPalette.text, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.width(30.dp))
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFFE9EEF6), RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("search" to "Search", "requests" to "Requests", "pending" to "Pending").forEach { (tab, label) ->
                    Surface(Modifier.weight(1f).noRippleClickable {
                        friendTab = tab
                        if (tab != "search") scope.launch { runCatching { loadRequests() } }
                    }, shape = RoundedCornerShape(14.dp), color = if (friendTab == tab) MessagingPalette.blueSoft else Color.Transparent) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            val tabLabel = if (tab == "requests" && incomingRequests.isNotEmpty()) "$label ${incomingRequests.size}" else label
                            Text(tabLabel, color = if (friendTab == tab) MessagingPalette.blue else MessagingPalette.secondary, fontSize = 12.sp, fontWeight = if (friendTab == tab) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
            if (friendTab == "search") {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), singleLine = true, placeholder = { Text("Search by username…") }, shape = RoundedCornerShape(22.dp), colors = messagingFieldColors())
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (search.trim().length < 2) item { Text("Enter at least 2 characters to find people.", Modifier.padding(14.dp), color = MessagingPalette.secondary, fontSize = 12.sp) }
                    else if (searchResults.isEmpty()) item { Text("No users found.", Modifier.padding(14.dp), color = MessagingPalette.secondary, fontSize = 12.sp) }
                    items(searchResults, key = { it.userId }) { person ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(Modifier.size(42.dp), shape = CircleShape, color = MessagingPalette.avatar) {
                                val avatar = friendAvatars[person.userId]
                                if (avatar != null) {
                                    Image(bitmap = avatar.asImageBitmap(), contentDescription = "${person.username} profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                } else {
                                    Box(contentAlignment = Alignment.Center) { Text(person.username.take(1).uppercase(), color = MessagingPalette.avatarText, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(person.username, color = MessagingPalette.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("@${person.username}", color = MessagingPalette.secondary, fontSize = 12.sp)
                            }
                            val isFriend = friends.any { it.friendId == person.userId }
                            val isPending = person.friendshipStatus == "pending"
                            Surface(Modifier.noRippleClickable {
                                if (!isFriend && !isPending) scope.launch {
                                    try {
                                        SupabaseClientProvider.client.postgrest.rpc("coolanas_friend_request", buildJsonObject { put("p_recipient_id", JsonPrimitive(person.userId)) })
                                        search = ""
                                        friendListVersion++
                                        loadRequests()
                                    } catch (exception: Exception) { error = exception.message ?: "Could not send friend request." }
                                }
                            }, shape = RoundedCornerShape(10.dp), color = if (isFriend || isPending) Color(0xFFE9EEF6) else MessagingPalette.blue) {
                                Text(if (isFriend) "Friends" else if (isPending) "Pending" else "＋ Add friend", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = if (isFriend || isPending) MessagingPalette.secondary else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = MessagingPalette.divider)
                    }
                }
            } else {
                val shownRequests = if (friendTab == "requests") incomingRequests else outgoingRequests
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (shownRequests.isEmpty()) item { Text(if (friendTab == "requests") "No new friend requests." else "No pending requests.", Modifier.padding(14.dp), color = MessagingPalette.secondary, fontSize = 12.sp) }
                    items(shownRequests, key = { it.friendshipId }) { request ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(Modifier.size(42.dp), shape = CircleShape, color = MessagingPalette.avatar) {
                                Box(contentAlignment = Alignment.Center) { Text(request.username.take(1).uppercase(), color = MessagingPalette.avatarText, fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(request.username, color = MessagingPalette.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("@${request.username}", color = MessagingPalette.secondary, fontSize = 12.sp)
                            }
                            if (friendTab == "requests") {
                                Surface(Modifier.noRippleClickable {
                                    scope.launch {
                                        runCatching {
                                            SupabaseClientProvider.client.postgrest.rpc("coolanas_friend_respond", buildJsonObject {
                                                put("p_friendship_id", JsonPrimitive(request.friendshipId)); put("p_response", JsonPrimitive("accept"))
                                            })
                                            loadRequests(); loadFriends(); friendListVersion++
                                        }.onFailure { error = it.message ?: "Could not accept request." }
                                    }
                                }, shape = RoundedCornerShape(10.dp), color = MessagingPalette.blue) {
                                    Text("Accept", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Color.White, fontSize = 11.sp)
                                }
                                Text("Decline", Modifier.noRippleClickable {
                                    scope.launch {
                                        runCatching {
                                            SupabaseClientProvider.client.postgrest.rpc("coolanas_friend_respond", buildJsonObject {
                                                put("p_friendship_id", JsonPrimitive(request.friendshipId)); put("p_response", JsonPrimitive("decline"))
                                            })
                                            loadRequests()
                                        }.onFailure { error = it.message ?: "Could not decline request." }
                                    }
                                }.padding(8.dp), color = MessagingPalette.error, fontSize = 11.sp)
                            } else {
                                Text("Cancel", Modifier.noRippleClickable {
                                    scope.launch {
                                        runCatching {
                                            SupabaseClientProvider.client.postgrest.rpc("coolanas_friend_cancel", buildJsonObject { put("p_friendship_id", JsonPrimitive(request.friendshipId)) })
                                            loadRequests()
                                        }.onFailure { error = it.message ?: "Could not cancel request." }
                                    }
                                }.padding(8.dp), color = MessagingPalette.secondary, fontSize = 11.sp)
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = MessagingPalette.divider)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Messages", color = MessagingPalette.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Your conversations", color = MessagingPalette.secondary, fontSize = 12.sp)
                }
                Surface(Modifier.size(40.dp).noRippleClickable {
                    conversationSearchVisible = !conversationSearchVisible
                    if (!conversationSearchVisible) search = ""
                }, shape = CircleShape, color = MessagingPalette.bluePale) {
                    Box(contentAlignment = Alignment.Center) { Text("⌕", color = MessagingPalette.blue, fontSize = 26.sp) }
                }
                Spacer(Modifier.width(7.dp))
                Surface(Modifier.size(40.dp).noRippleClickable { addFriendMode = true; friendTab = "search"; search = "" }, shape = CircleShape, color = MessagingPalette.bluePale) {
                    Box(contentAlignment = Alignment.Center) { Text("＋", color = MessagingPalette.blue, fontSize = 24.sp) }
                }
                Spacer(Modifier.width(7.dp))
                Surface(Modifier.size(40.dp).noRippleClickable { addFriendMode = true; friendTab = "requests"; scope.launch { runCatching { loadRequests() } } }, shape = CircleShape, color = MessagingPalette.bluePale) {
                    Box(contentAlignment = Alignment.Center) { Text("♧", color = MessagingPalette.blue, fontSize = 21.sp) }
                }
            }
            if (conversationSearchVisible) {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), singleLine = true, placeholder = { Text("Search conversations…") }, shape = RoundedCornerShape(22.dp), colors = messagingFieldColors())
            }
            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Loading conversations…", color = MessagingPalette.secondary, fontSize = 13.sp) }
            } else if (friends.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No conversations yet", color = MessagingPalette.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Add a friend to start messaging.", color = MessagingPalette.secondary, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(friends.filter { it.username.contains(search, ignoreCase = true) }, key = { it.friendId }) { friend ->
                        Row(Modifier.fillMaxWidth().noRippleClickable {
                            scope.launch {
                                selectedFriend = friend
                                conversationId = null
                                conversationOpening = true
                                messages = emptyList()
                                remoteTyping = false
                                draft = ""
                                error = null
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        SupabaseClientProvider.client.postgrest.rpc("coolanas_dm_open_conversation", buildJsonObject { put("p_friend_id", JsonPrimitive(friend.friendId)) })
                                    }
                                    conversationId = result.decodeAs<String>().trim('"')
                                    refreshUnreadCounts()
                                } catch (exception: Exception) {
                                    error = exception.message ?: "Could not open this conversation."
                                } finally { conversationOpening = false }
                            }
                        }.padding(vertical = 8.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(Modifier.size(48.dp), shape = CircleShape, color = MessagingPalette.avatar) {
                                val avatar = friendAvatars[friend.friendId]
                                if (avatar != null) {
                                    Image(bitmap = avatar.asImageBitmap(), contentDescription = "${friend.username} profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                } else {
                                    Box(contentAlignment = Alignment.Center) { Text(friend.username.take(1).uppercase(), color = MessagingPalette.avatarText, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(friend.username, color = MessagingPalette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Open conversation", color = MessagingPalette.secondary, fontSize = 12.sp)
                            }
                            val unread = unreadCounts[friend.friendId] ?: 0
                            if (unread > 0) Surface(shape = CircleShape, color = Color(0xFF347FDF)) {
                                Text(if (unread > 9) "9+" else unread.toString(), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = MessagingPalette.divider)
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) Text(error.orEmpty(), color = MessagingPalette.error, fontSize = 11.sp)
    }
    }
}

private suspend fun markConversationRead(conversationId: String, userId: String) {
    withContext(Dispatchers.IO) {
        SupabaseClientProvider.client.postgrest.from("coolanas_dm_messages").update(
            buildJsonObject { put("read_at", JsonPrimitive(Instant.now().toString())) }
        ) {
            filter {
                eq("conversation_id", conversationId)
                neq("sender_id", userId)
                exact("read_at", null)
            }
        }
    }
}
