package com.coolanas.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuildMemberData(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val username: String = "Member",
    val role: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("picture_url") val pictureUrl: String? = null,
    @SerialName("google_avatar") val googleAvatar: String? = null
) {
    val stableId: String get() = id.ifBlank { userId }
    val imageUrl: String? get() = pictureUrl ?: avatarUrl ?: googleAvatar
}

@Serializable
data class GuildData(
    val id: String = "",
    val name: String = "Study Guild",
    val description: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("banner_url") val bannerUrl: String? = null,
    @SerialName("picture_url") val pictureUrl: String? = null,
    val colour: String? = null,
    val members: List<GuildMemberData> = emptyList(),
    @SerialName("member_count") val memberCount: Int? = null
) {
    val displayMemberCount: Int get() = memberCount ?: members.size
}

@Serializable
data class GuildInvitationData(
    val id: String = "",
    @SerialName("guild_id") val guildId: String? = null,
    @SerialName("guild_name") val guildName: String = "Study Guild",
    @SerialName("inviter_username") val inviterUsername: String = "A member",
    val username: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val status: String? = null
)

@Serializable
data class GuildInvitationsData(
    val incoming: List<GuildInvitationData> = emptyList(),
    val outgoing: List<GuildInvitationData> = emptyList()
)

@Serializable
data class GuildMemberStatusData(
    @SerialName("user_id") val userId: String,
    val status: String = "idle"
)

@Serializable
data class GuildPublicDirectoryData(
    val guilds: List<GuildData> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    val page: Int = 1
)

@Serializable
data class GuildPublicTimerData(
    val username: String = "Student",
    @SerialName("google_avatar") val googleAvatar: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("picture_url") val pictureUrl: String? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0L,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("server_now") val serverNow: String? = null
) {
    val imageUrl: String? get() = pictureUrl ?: avatarUrl ?: googleAvatar
}

@Serializable
data class GuildMessageData(
    val id: String,
    @SerialName("guild_id") val guildId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)
