package com.coolanas.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

@Composable
fun StudyMessagingScreen(userId: String) {
    val scope = rememberCoroutineScope()
    var friends by remember(userId) { mutableStateOf<List<CoolanasFriendRow>>(emptyList()) }
    var requests by remember(userId) { mutableStateOf<List<CoolanasFriendRequestRow>>(emptyList()) }
    var search by remember(userId) { mutableStateOf("") }
    var searchResults by remember(userId) { mutableStateOf<List<CoolanasFriendSearchRow>>(emptyList()) }
    var selectedFriend by remember(userId) { mutableStateOf<CoolanasFriendRow?>(null) }
    var conversationId by remember(userId) { mutableStateOf<String?>(null) }
    var messages by remember(userId) { mutableStateOf<List<CoolanasDmRow>>(emptyList()) }
    var draft by remember(userId) { mutableStateOf("") }
    var loading by remember(userId) { mutableStateOf(true) }
    var sending by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var requestsVisible by remember(userId) { mutableStateOf(false) }
    var friendListVersion by remember(userId) { mutableStateOf(0) }
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

    LaunchedEffect(userId, friendListVersion) {
        loading = true
        error = null
        try {
            loadFriends()
            loadRequests()
        } catch (exception: Exception) {
            error = exception.message ?: "Could not load your friends."
        } finally {
            loading = false
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
        val channel = SupabaseClientProvider.client.realtime.channel("coolanas-mobile-dm-$activeConversation")
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "coolanas_dm_messages"
            filter = "conversation_id=eq.$activeConversation"
        }
        val observer = launch {
            insertFlow.collect { action ->
                val row = runCatching { action.decodeRecord<CoolanasDmRow>() }.getOrNull() ?: return@collect
                if (row.conversationId != activeConversation) return@collect
                if (messages.none { it.id == row.id }) {
                    messages = (messages + row).sortedWith(compareBy<CoolanasDmRow> { it.createdAt }.thenBy { it.id })
                }
                if (row.senderId != userId) markConversationRead(activeConversation, userId)
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
        } catch (_: kotlinx.coroutines.CancellationException) {
            throw
        } catch (exception: Exception) {
            error = exception.message ?: "Live messages could not refresh."
        } finally {
            observer.cancel()
            SupabaseClientProvider.client.realtime.removeChannel(channel)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) messageListState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Messages", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text("Coolanas friends", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            TextButton(onClick = {
                requestsVisible = !requestsVisible
                if (requestsVisible) scope.launch { runCatching { loadRequests() } }
            }) {
                Text("Requests ${requests.count { it.direction == "incoming" }}")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Find a Coolanas user") },
            label = { Text("Search friends") }
        )

        if (search.trim().length >= 2) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF24272E),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (searchResults.isEmpty()) Text("No matching users.", color = Color(0xFFB9BEC8), fontSize = 12.sp)
                    searchResults.forEach { person ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(person.username, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(
                                enabled = person.friendshipStatus != "pending" && friends.none { it.friendId == person.userId },
                                onClick = {
                                    scope.launch {
                                        try {
                                            SupabaseClientProvider.client.postgrest.rpc(
                                                "coolanas_friend_request",
                                                buildJsonObject { put("p_recipient_id", JsonPrimitive(person.userId)) }
                                            )
                                            search = ""
                                            friendListVersion++
                                        } catch (exception: Exception) {
                                            error = exception.message ?: "Could not send friend request."
                                        }
                                    }
                                }
                            ) { Text(if (person.friendshipStatus == "pending") "Pending" else "Add") }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(82.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.forEach { friend ->
                            Surface(
                                modifier = Modifier.noRippleClickable {
                                    scope.launch {
                                        selectedFriend = friend
                                        draft = ""
                                        error = null
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                SupabaseClientProvider.client.postgrest.rpc(
                                                    "coolanas_dm_open_conversation",
                                                    buildJsonObject { put("p_friend_id", JsonPrimitive(friend.friendId)) }
                                                )
                                            }
                                            conversationId = result.decodeAs<String>().trim('"')
                                        } catch (exception: Exception) {
                                            error = exception.message ?: "Could not open this conversation."
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selectedFriend?.friendId == friend.friendId) Color(0xFF514568) else Color(0xFF262A31),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selectedFriend?.friendId == friend.friendId) Color(0xFFB8A3E6) else Color(0xFF3C414A)
                                )
                            ) {
                                Text(
                                    friend.username,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (requestsVisible) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF24272E)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (requests.isEmpty()) Text("No pending friend requests.", color = Color(0xFFB9BEC8), fontSize = 12.sp)
                    requests.forEach { request ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(request.username, color = Color.White, fontSize = 14.sp)
                                Text(request.direction, color = Color(0xFF9EA5B2), fontSize = 10.sp)
                            }
                            if (request.direction == "incoming") {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            SupabaseClientProvider.client.postgrest.rpc(
                                                "coolanas_friend_respond",
                                                buildJsonObject {
                                                    put("p_friendship_id", JsonPrimitive(request.friendshipId))
                                                    put("p_response", JsonPrimitive("accept"))
                                                }
                                            )
                                            loadRequests()
                                            loadFriends()
                                        } catch (exception: Exception) {
                                            error = exception.message ?: "Could not accept request."
                                        }
                                    }
                                }) { Text("Accept") }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            SupabaseClientProvider.client.postgrest.rpc(
                                                "coolanas_friend_respond",
                                                buildJsonObject {
                                                    put("p_friendship_id", JsonPrimitive(request.friendshipId))
                                                    put("p_response", JsonPrimitive("decline"))
                                                }
                                            )
                                            loadRequests()
                                        }
                                    }
                                }) { Text("Decline") }
                            } else {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            SupabaseClientProvider.client.postgrest.rpc(
                                                "coolanas_friend_cancel",
                                                buildJsonObject { put("p_friendship_id", JsonPrimitive(request.friendshipId)) }
                                            )
                                            loadRequests()
                                        }
                                    }
                                }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        } else if (selectedFriend != null && conversationId != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(selectedFriend!!.username, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    selectedFriend = null
                    conversationId = null
                    messages = emptyList()
                }) { Text("Close") }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = messageListState,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.senderId == userId
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .background(
                                    if (mine) Color(0xFF514568) else Color(0xFF292E36),
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomEnd = if (mine) 5.dp else 16.dp,
                                        bottomStart = if (mine) 16.dp else 5.dp
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(message.messageText(), color = Color.White, fontSize = 14.sp)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val time = runCatching {
                                    Instant.parse(message.createdAt).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                }.getOrDefault("")
                                Text(time, color = Color(0xFFB9B9C5), fontSize = 9.sp)
                                if (mine) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (message.readAt == null) "✓" else "✓✓", color = Color(0xFFC8B7EF), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(300) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a message") },
                    maxLines = 4
                )
                Button(
                    enabled = draft.isNotBlank() && !sending,
                    onClick = {
                        val outgoing = draft.trim()
                        val activeConversation = conversationId ?: return@Button
                        if (outgoing.isBlank()) return@Button
                        val optimistic = CoolanasDmRow(
                            id = UUID.randomUUID().toString(),
                            conversationId = activeConversation,
                            senderId = userId,
                            ciphertext = buildJsonObject {
                                put("v", JsonPrimitive(0))
                                put("plaintext", JsonPrimitive(outgoing))
                            },
                            createdAt = Instant.now().toString(),
                            readAt = null
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
                            } finally {
                                sending = false
                            }
                        }
                    }
                ) { Text("Send") }
            }
        } else {
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = Color(0xFF24272E),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (loading) "Loading friends…" else "Choose a friend to start messaging.",
                        color = Color(0xFFB9BEC8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
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
