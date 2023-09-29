package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException

fun relayError(message: String): Nothing {
    throw DiscordRelayedException("A **error** occurred: $message")
}
