package moe.nikky.twitch;

import com.kotlindiscord.kord.extensions.storage.Data
import dev.kord.common.entity.Snowflake;
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import io.klogging.context.logContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable;
import kotlinx.serialization.json.JsonNames
import moe.nikky.relayError

@Serializable
data class TwitchGuildConfig(
    val configs: Map<String, TwitchConfig> = emptyMap()
) : Data {
    fun find(channelId: Snowflake, twitchUserName: String): Pair<String, TwitchConfig>? {
        return configs.entries.firstOrNull { (key, configEntry) ->
            configEntry.channelId == channelId && configEntry.twitchUserName == twitchUserName
        }?.toPair()
    }

    fun remove(key: String): TwitchGuildConfig {
        return copy(
            configs = configs - key
        )
    }

    fun update(key: String, entry: TwitchConfig): TwitchGuildConfig {
        return copy(
            configs = configs + (key to entry)
        )
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class TwitchConfig(
    val channelId: Snowflake,
    val twitchUserName: String,
    val roleId: Snowflake,
    @JsonNames("message")
    val messageId: Snowflake?,
) {
    companion object {
        val ALLOWED_CHARS = setOf('.', ",", '-', '_')
    }

    fun key(channel: TopGuildMessageChannel): String {
        val filteredChannelName = channel.name.filter { it.isLetterOrDigit() || it in ALLOWED_CHARS }
        return "$filteredChannelName-$twitchUserName"
    }

    suspend fun role(guild: GuildBehavior): Role {
        return guild.getRoleOrNull(roleId) ?: relayError("role $roleId could not be loaded")
    }

    suspend fun channel(guild: Guild): TopGuildMessageChannel {
        return withContext(
            logContext("guild" to guild.name)
        ) {
            guild.getChannelOfOrNull<TextChannel>(channelId)
                ?: guild.getChannelOfOrNull<NewsChannel>(channelId)
                ?: relayError("channel $channelId in '${guild.name}' could not be loaded as TextChannel or NewsChannel")
        }
    }

    val twitchUrl: String get() = "https://twitch.tv/$twitchUserName"
}
