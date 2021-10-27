package moe.nikky

import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Message
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.rest.request.KtorRequestException
import io.klogging.logger
import moe.nikky.db.GuildConfig
import moe.nikky.db.RoleChooserConfig
import moe.nikky.db.TwitchConfig

private val logger = logger("moe.nikky.ConfigExtensions")

suspend fun GuildConfig.adminRole(guildBehavior: GuildBehavior): Role? {
    return adminRole?.let { guildBehavior.getRoleOrNull(it) }
}

val TwitchConfig.twitchUrl: String get() = "https://twitch.tv/$twitchUsername"

suspend fun TwitchConfig.role(guildBehavior: GuildBehavior): Role {
    return guildBehavior.getRoleOrNull(role) ?: relayError("role $role could not be loaded")
}

suspend fun TwitchConfig.channel(guildBehavior: GuildBehavior): TopGuildMessageChannel {
    return guildBehavior.getChannelOfOrNull<TextChannel>(channel)
        ?: guildBehavior.getChannelOfOrNull<NewsChannel>(channel)
        ?: relayError("channel $channel could not be loaded as TextChannel")
}


suspend fun RoleChooserConfig.channel(guildBehavior: GuildBehavior): TextChannel {
    return guildBehavior.getChannelOfOrNull<TextChannel>(channel)
        ?: relayError("channel $channel could not be loaded as TextChannel")
}

suspend fun RoleChooserConfig.getMessageOrRelayError(guildBehavior: GuildBehavior): Message? = try {
    channel(guildBehavior).getMessageOrNull(message)
} catch (e: KtorRequestException) {
    logger.errorF { e.message }
    relayError("cannot access message $message")
}