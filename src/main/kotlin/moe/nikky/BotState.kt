package moe.nikky

import com.kotlindiscord.kord.extensions.utils.selfMember
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.firstOrNull
import dev.kord.rest.request.KtorRequestException
import io.klogging.Klogging
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable

data class BotState(
    val guildBehavior: GuildBehavior,
//    val selfRole: RoleBehavior?,
    val adminRole: Role? = null,
    val roleChooser: Map<String, RolePickerMessageState> = emptyMap(),
    val twitchNotifications: Map<String, TwitchNotificationState> = emptyMap(),
): Klogging {
    fun asSerialized() = ConfigurationStateSerialized(
        adminRole = adminRole?.id,
        roleChooser = roleChooser.mapValues { (section, rolePickerMessageState) ->
            rolePickerMessageState.asSerialized()
        },
        twitchNotifications = twitchNotifications.mapValues { (_, value) ->
            value.asSerialized()
        }
    )
}


data class TwitchNotificationState(
    val twitchUserName: String,
    val channel: TextChannelBehavior,
    val role: Role,
    val message: Snowflake? = null,
): Klogging {
    val twitchUrl: String = "https://twitch.tv/$twitchUserName"
    fun asSerialized(): TwitchNotificationConfig {
        return TwitchNotificationConfig(
            twitchUserName = twitchUserName,
            channel = channel.id,
            message = message,
            role = role.id
        )
    }
}

data class RolePickerMessageState(
    val channel: GuildMessageChannelBehavior,
    val messageId: Snowflake,
    val roleMapping: Map<ReactionEmoji, Role> = emptyMap(),
    val liveMessageJob: Job = Job(),
): Klogging {
    suspend fun getMessageOrRelayError(): MessageBehavior? = try {
        channel.getMessageOrNull(messageId)
    } catch (e: KtorRequestException) {
        logger.errorF { e.message }
        relayError("cannot access message $messageId")
    }

    fun asSerialized(): RolePickerMessageConfig {
        return RolePickerMessageConfig(
            channel = channel.id,
            message = messageId,
            roleMapping = roleMapping.entries.associate { (reactionEmoji, role) ->
                reactionEmoji.mention to role.id
            }
        )
    }
}

@Serializable
data class ConfigurationStateSerialized(
    val adminRole: Snowflake? = null,
    val roleChooser: Map<String, RolePickerMessageConfig> = emptyMap(),
    val twitchNotifications: Map<String, TwitchNotificationConfig> = emptyMap(),
): Klogging {
    suspend fun resolve(kord: Kord, guildBehavior: GuildBehavior): BotState {
        // 898159203829575691
//        val selfRole = guildBehavior.selfMember().roleBehaviors.firstOrNull { it.asRole().managed }
//        if(selfRole == null) {
//            val guild = guildBehavior.asGuild()
//            val selfMember = guildBehavior.selfMember()
//            logger.errorF { "guild ${guild.name} is missing a managed role for ${selfMember.username} / ${selfMember.displayName}" }
//        }
        return BotState(
            guildBehavior = guildBehavior,
//            selfRole = selfRole,
            adminRole = adminRole?.let { guildBehavior.getRoleOrNull(it) },
            roleChooser = roleChooser.mapValues { (section, rolePickerConfig) ->
                rolePickerConfig.resolve(guildBehavior)
            },
            twitchNotifications = twitchNotifications.mapValues { (_, value) ->
                value.resolve(guildBehavior)
            }
        )
    }
}

@Serializable
data class RolePickerMessageConfig(
    val channel: Snowflake,
    val message: Snowflake,
    val roleMapping: Map<String, Snowflake>,
): Klogging {
    suspend fun resolve(guildBehavior: GuildBehavior): RolePickerMessageState {
        val channel: TextChannelBehavior = guildBehavior.getChannelOf<TextChannel>(channel)
        return RolePickerMessageState(
            channel = channel,
            messageId = message,
            roleMapping = roleMapping.entries.associate { (reactionEmojiName, role) ->
                val reactionEmoji = guildBehavior.emojis.firstOrNull { it.mention == reactionEmojiName }
                    ?.let { ReactionEmoji.from(it) }
                    ?: ReactionEmoji.Unicode(reactionEmojiName)
                reactionEmoji to guildBehavior.getRole(role)
            }
        )
    }
}

@Serializable
data class TwitchNotificationConfig(
    val twitchUserName: String,
    val channel: Snowflake,
    val message: Snowflake?,
    val role: Snowflake,
): Klogging {
    suspend fun resolve(guildBehavior: GuildBehavior): TwitchNotificationState {
        val channel = guildBehavior.getChannelOf<TextChannel>(channel)
        return TwitchNotificationState(
            channel = channel,
            twitchUserName = twitchUserName,
            message = message,
            role = guildBehavior.getRole(role)
        )
    }
}
