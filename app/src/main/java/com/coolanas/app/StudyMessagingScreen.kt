package com.coolanas.app

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

private fun messagingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFF3F2F7),
    unfocusedTextColor = Color(0xFFF3F2F7),
    focusedContainerColor = Color(0xFF1D2026),
    unfocusedContainerColor = Color(0xFF1D2026),
    focusedBorderColor = Color(0xFF9B82C7),
    unfocusedBorderColor = Color(0xFF454952),
    focusedPlaceholderColor = Color(0xFF969BA6),
    unfocusedPlaceholderColor = Color(0xFF969BA6),
    cursorColor = Color(0xFFBDA9E0)
)

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
                }, shape = CircleShape, color = Color(0xFF2D3039)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = Color(0xFFBDA9E0), fontSize = 30.sp, lineHeight = 30.sp)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(selectedFriend?.username.orEmpty(), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (remoteTyping) "Typing…" else "Coolanas friend", color = if (remoteTyping) Color(0xFF2F80E7) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("ⓘ", color = Color(0xFF969BA6), fontSize = 22.sp)
            }

            if (conversationOpening || conversationId == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (conversationOpening) "Opening conversation…" else "Could not open conversation. Go back and try again.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 8.dp),
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
                                    if (mine) Color(0xFF3D3552) else Color(0xFF292D35),
                                    RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp, bottomEnd = if (mine) 5.dp else 17.dp, bottomStart = if (mine) 17.dp else 5.dp)
                                ).padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Text(message.messageText(), color = Color(0xFFF2F1F6), fontSize = 15.sp, lineHeight = 21.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    val time = runCatching { Instant.parse(message.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
                                    Text(time, color = Color(0xFFADB2BE), fontSize = 10.sp)
                                    if (mine) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (message.readAt == null) "✓" else "✓✓", color = Color(0xFFC5B2E8), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (remoteTyping) item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF292D35)) {
                                Text("•••", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFFBDA9E0), fontSize = 14.sp)
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
                    }, shape = CircleShape, color = if (draft.isNotBlank() && !sending) Color(0xFF9B82C7) else Color(0xFF3A3E47)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("➤", color = Color.White, fontSize = 21.sp) }
                    }
                }
            }
        } else if (addFriendMode) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", modifier = Modifier.noRippleClickable { addFriendMode = false }.padding(end = 12.dp), color = Color(0xFF347FDF), fontSize = 30.sp)
                Text("Add friend", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.width(30.dp))
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF24262D), RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("search" to "Search", "requests" to "Requests", "pending" to "Pending").forEach { (tab, label) ->
                    Surface(Modifier.weight(1f).noRippleClickable {
                        friendTab = tab
                        if (tab != "search") scope.launch { runCatching { loadRequests() } }
                    }, shape = RoundedCornerShape(14.dp), color = if (friendTab == tab) Color(0xFF3D3552) else Color.Transparent) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            val tabLabel = if (tab == "requests" && incomingRequests.isNotEmpty()) "$label ${incomingRequests.size}" else label
                            Text(tabLabel, color = if (friendTab == tab) Color(0xFFE1D5F3) else Color(0xFFADB2BE), fontSize = 12.sp, fontWeight = if (friendTab == tab) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
            if (friendTab == "search") {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), singleLine = true, placeholder = { Text("Search by username…") }, shape = RoundedCornerShape(22.dp), colors = messagingFieldColors())
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (search.trim().length < 2) item { Text("Enter at least 2 characters to find people.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    else if (searchResults.isEmpty()) item { Text("No users found.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    items(searchResults, key = { it.userId }) { person ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(Modifier.size(42.dp), shape = CircleShape, color = Color(0xFF3D3552)) {
                                Box(contentAlignment = Alignment.Center) { Text(person.username.take(1).uppercase(), color = Color(0xFFE1D5F3), fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(person.username, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("@${person.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                            }, shape = RoundedCornerShape(10.dp), color = if (isFriend || isPending) Color(0xFF30343D) else Color(0xFF80699F)) {
                                Text(if (isFriend) "Friends" else if (isPending) "Pending" else "＋ Add friend", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = if (isFriend || isPending) Color(0xFFADB2BE) else Color(0xFFF8F5FC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFF343842))
                    }
                }
            } else {
                val shownRequests = if (friendTab == "requests") incomingRequests else outgoingRequests
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (shownRequests.isEmpty()) item { Text(if (friendTab == "requests") "No new friend requests." else "No pending requests.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    items(shownRequests, key = { it.friendshipId }) { request ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(Modifier.size(42.dp), shape = CircleShape, color = Color(0xFF3D3552)) {
                                Box(contentAlignment = Alignment.Center) { Text(request.username.take(1).uppercase(), color = Color(0xFFE1D5F3), fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(request.username, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("@${request.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                                }, shape = RoundedCornerShape(10.dp), color = Color(0xFF80699F)) {
                                    Text("Accept", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Color(0xFFF8F5FC), fontSize = 11.sp)
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
                                }.padding(8.dp), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            } else {
                                Text("Cancel", Modifier.noRippleClickable {
                                    scope.launch {
                                        runCatching {
                                            SupabaseClientProvider.client.postgrest.rpc("coolanas_friend_cancel", buildJsonObject { put("p_friendship_id", JsonPrimitive(request.friendshipId)) })
                                            loadRequests()
                                        }.onFailure { error = it.message ?: "Could not cancel request." }
                                    }
                                }.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFF343842))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Messages", color = MaterialTheme.colorScheme.onBackground, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Your conversations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Surface(Modifier.size(42.dp).noRippleClickable { addFriendMode = true; friendTab = "search"; search = "" }, shape = CircleShape, color = Color(0xFF30343D)) {
                    Box(contentAlignment = Alignment.Center) { Text("＋", color = Color(0xFFE1D5F3), fontSize = 24.sp) }
                }
                Spacer(Modifier.width(8.dp))
                Surface(Modifier.size(42.dp).noRippleClickable { addFriendMode = true; friendTab = "requests"; scope.launch { runCatching { loadRequests() } } }, shape = CircleShape, color = Color(0xFF30343D)) {
                    Box(contentAlignment = Alignment.Center) { Text("♧", color = Color(0xFFE1D5F3), fontSize = 21.sp) }
                }
            }
            OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), singleLine = true, placeholder = { Text("Search conversations…") }, shape = RoundedCornerShape(22.dp), colors = messagingFieldColors())
            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Loading conversations…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            } else if (friends.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No conversations yet", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Add a friend to start messaging.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                            Surface(Modifier.size(54.dp), shape = CircleShape, color = Color(0xFFE5ECF8)) {
                                Box(contentAlignment = Alignment.Center) { Text(friend.username.take(1).uppercase(), color = Color(0xFFE1D5F3), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(friend.username, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Open conversation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            val unread = unreadCounts[friend.friendId] ?: 0
                            if (unread > 0) Surface(shape = CircleShape, color = Color(0xFF347FDF)) {
                                Text(if (unread > 9) "9+" else unread.toString(), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFF343842))
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
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
