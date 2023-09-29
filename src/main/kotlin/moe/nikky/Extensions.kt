package moe.nikky

import dev.kord.core.entity.GuildScheduledEvent

val GuildScheduledEvent.location: String?
    get() = entityMetadata?.location?.value
