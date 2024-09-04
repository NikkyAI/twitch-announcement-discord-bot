package moe.nikky

import dev.kordex.core.DiscordRelayedException

fun relayError(message: String): Nothing {
    throw DiscordRelayedException("A **error** occurred: $message")
}
