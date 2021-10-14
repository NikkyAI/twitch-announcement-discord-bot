package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommand
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondEphemeral

fun EphemeralSlashCommandContext<*>.errorMessage(message: String): Nothing {
    throw DiscordRelayedException("A **error** occurred: $message")
}

fun PublicSlashCommandContext<*>.errorMessage(message: String): Nothing {
    throw DiscordRelayedException("A **error** occurred: $message")
}