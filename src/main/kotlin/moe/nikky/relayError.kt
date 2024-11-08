package moe.nikky

import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.types.Key

fun relayError(messageKey: Key): Nothing {
    throw DiscordRelayedException(messageKey)
}

@Deprecated("replace with key")
fun relayError(message: String): Nothing {
    throw DiscordRelayedException(message.toKey())
}
