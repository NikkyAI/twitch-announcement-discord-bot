package moe.nikky.twitch

import dev.kordex.core.storage.Data
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlBlockArray

@Serializable
@Suppress("DataClassShouldBeImmutable", "MagicNumber")
data class TwitchChannelConfig(
    @TomlBlockArray(1)
    var configurations: List<TwitchEntryConfig>
) : Data
