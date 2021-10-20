package moe.nikky

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.firstOrNull
import dev.kord.rest.request.KtorRequestException
import io.klogging.Klogging
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GuildConfiguration(
    val adminRole: Snowflake? = null,
    val roleChooser: Map<String, RolePickerMessageConfig> = emptyMap(),
    val twitchNotifications: Map<String, TwitchNotificationConfig> = emptyMap(),
) : Klogging {
    @Transient
    var twitchJob: Job? = null

    suspend fun adminRole(guildBehavior: GuildBehavior): Role? {
        return adminRole?.let { guildBehavior.getRoleOrNull(it) }
    }
}

@Serializable
data class RolePickerMessageConfig(
    val channel: Snowflake,
    val message: Snowflake,
    val roleMapping: Map<String, Snowflake>,
) : Klogging {
    @Transient
    var liveMessageJob: Job = Job()
    suspend fun roleMapping(guildBehavior: GuildBehavior): Map<ReactionEmoji, Role> {
        return roleMapping.entries.associate { (reactionEmojiName, role) ->
            val reactionEmoji = guildBehavior.emojis.firstOrNull { it.mention == reactionEmojiName }
                ?.let { ReactionEmoji.from(it) }
                ?: ReactionEmoji.Unicode(reactionEmojiName)
            reactionEmoji to guildBehavior.getRole(role)
        }
    }

    suspend fun channel(guildBehavior: GuildBehavior): TextChannel {
        return guildBehavior.getChannelOfOrNull<TextChannel>(channel)
            ?: relayError("channel $channel could not be loaded as TextChannel")
    }

    suspend fun getMessageOrRelayError(guildBehavior: GuildBehavior): Message? = try {
        channel(guildBehavior).getMessageOrNull(message)
    } catch (e: KtorRequestException) {
        logger.errorF { e.message }
        relayError("cannot access message $message")
    }
}

@Serializable
data class TwitchNotificationConfig(
    val twitchUserName: String,
    val channel: Snowflake,
    val role: Snowflake,
    val message: Snowflake? = null,
) : Klogging {
    val twitchUrl: String get() = "https://twitch.tv/$twitchUserName"

    suspend fun role(guildBehavior: GuildBehavior): Role {
        return guildBehavior.getRoleOrNull(role) ?: relayError("role $role could not be loaded")
    }

    suspend fun channel(guildBehavior: GuildBehavior): TextChannel {
        return guildBehavior.getChannelOfOrNull<TextChannel>(channel)
            ?: relayError("channel $channel could not be loaded as TextChannel")
    }
}
