package moe.nikky

import com.kotlindiscord.kord.extensions.storage.Data
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.rest.request.KtorRequestException
import io.github.xn32.json5k.SerialComment
import io.klogging.context.logContext
import io.klogging.logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private val logger = logger(object{}.javaClass.enclosingClass.canonicalName)

@Serializable
data class RoleChooserConfig(
    val section: String,
    @SerialComment("channel")
    val channelId: Snowflake,
    @SerialComment("message")
    val messageId: Snowflake,
    val roleMapping: List<RoleMappingConfig>,
) : Data {

    companion object {
        val ALLOWED_CHARS = setOf('.', ",", '-', '_')
    }

    fun key(channel: TextChannel): String {
        val filteredChannelName = channel.name.filter { it.isLetterOrDigit() || it in ALLOWED_CHARS }
        val filteredSection = section.filter { it.isLetterOrDigit() || it in ALLOWED_CHARS }
        return "${filteredChannelName}_${filteredSection}"
    }

    suspend fun channel(guildBehavior: Guild): TextChannel {
        return withContext(
            logContext("guild" to guildBehavior.name)
        ) {
            guildBehavior.getChannelOfOrNull<TextChannel>(channelId)
                ?: relayError("channel $channelId in '${guildBehavior.name}' could not be loaded as TextChannel")
        }
    }

    suspend fun getMessageOrRelayError(guildBehavior: Guild): Message? {
        return withContext(
            logContext("guild" to guildBehavior.name)
        ) {
            try {
                channel(guildBehavior).getMessageOrNull(messageId)
            } catch (e: KtorRequestException) {
                logger.errorF { e.message }
                relayError("cannot access message $messageId")
            }
        }
    }
}

@Serializable
data class RoleMappingConfig(
    val emoji: String,
    val emojiName: String? = null,
    val role: Snowflake,
    val roleName: String,
) : Data {
    suspend fun reactionEmoji(guildBehavior: GuildBehavior): ReactionEmoji {
        if(emoji.startsWith("<") && emoji.endsWith(">")) {
            val id = emoji.substringAfterLast(":").substringBefore("<")
            return guildBehavior.emojis.first { it.id.toString() == id }
                .let {
                    logger.traceF { "found emoji ${it.name}, turning into reaction emoji" }
                    ReactionEmoji.from(it)
                }
        }

        val guildEmoji = guildBehavior.emojis.firstOrNull { it.name == emoji || it.id.toString() == emoji }

        return guildEmoji
            ?.let {
                logger.traceF { "found emoji ${it.name}, turning into reaction emoji" }
                ReactionEmoji.from(it)
            }
            ?: run {
                logger.traceF { "creating unicode emoji from '$emoji'" }
                ReactionEmoji.Unicode(emoji)
            }
    }
    suspend fun getRole(guildBehavior: GuildBehavior): Role {
        return guildBehavior.getRole(role)
    }
}


@Serializable
data class RoleChooserConfigOld(
    val section: String,
    @SerialComment("channel")
    val channelId: Snowflake,
    @SerialComment("message")
    val messageId: Snowflake,
    val roleMapping: List<RoleMappingConfigOld>,
) : Data


@Serializable
data class RoleMappingConfigOld(
    val reaction: String,
    val role: Snowflake,
    val roleName: String,
) : Data