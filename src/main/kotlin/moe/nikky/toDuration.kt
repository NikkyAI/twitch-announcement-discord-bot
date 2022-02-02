package moe.nikky

import kotlinx.datetime.*
import kotlin.time.Duration

/**
 * Converts this [DateTimePeriod] to a [Duration].
 */
public fun DateTimePeriod.toDuration(): Duration {
    val now = Clock.System.now()
    val applied = now.plus(this, TimeZone.UTC)

    return applied - now
}
public fun DateTimePeriod.fromNow(): Instant {
    val now = Clock.System.now()
    val applied = now.plus(this, TimeZone.UTC)

    return applied
}