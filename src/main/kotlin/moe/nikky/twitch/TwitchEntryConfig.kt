package moe.nikky.twitch

import com.kotlindiscord.kord.extensions.storage.Data
import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlComment

@Serializable
@Suppress("DataClassShouldBeImmutable", "MagicNumber")
data class TwitchEntryConfig(
    @TomlComment(
        "twitch username"
    )
    var twitchUserName: String,

    @TomlComment(
        "which role to ping"
    )
    var role: Snowflake,

    @TomlComment(
        "Message reference"
    )
    var message: Snowflake?,
) : Data
