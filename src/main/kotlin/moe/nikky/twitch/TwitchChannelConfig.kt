package moe.nikky.twitch

import com.kotlindiscord.kord.extensions.storage.Data
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlBlockArray

@Serializable
@Suppress("DataClassShouldBeImmutable", "MagicNumber")
data class TwitchChannelConfig(
    @TomlBlockArray(1)
    var configurations: List<TwitchEntryConfig>
) : Data
