package moe.nikky

import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import io.klogging.Klogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import moe.nikky.json.VersionMigrator
import moe.nikky.json.VersionedSerializer
import org.koin.core.component.KoinComponent
import java.io.File

class ConfigurationService : KoinComponent, Klogging {
    private val configFolder = File(envOrNull("CONFIG_DIR") ?: "data")
    private val configFile = configFolder.resolve("config.json")

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val configurations: MutableMap<String, GuildConfiguration> = mutableMapOf()
//    private var configurations: MutableMap<String, ConfigurationStateSerialized> = mutableMapOf()

    private fun <K, V> Map<K, V>.replaceKey(oldKey: K, newKey: K, transform: (V) -> V = { it }): Map<K, V> =
        this - oldKey + (newKey to transform(getValue(oldKey)))

    private fun <K, V> Map<K, V>.replaceValue(key: K, transform: (V) -> V): Map<K, V> =
        this + (key to transform(getValue(key)))

    private fun Map<String, JsonElement>.toJsonObject() = JsonObject(this)

    val versionedSerializer = VersionedSerializer(
        MapSerializer(
            String.serializer(),
            GuildConfiguration.serializer()
        ),
        currentVersion = 2,
        migrations = mapOf(
            0..1 to VersionMigrator(
                json,
                JsonObject.serializer(),
                new = JsonObject.serializer()
            ) { obj: JsonObject ->
                obj.mapValues { (_, guildObj) ->
                    guildObj.jsonObject.replaceValue(
                        "twitchNotifications"
                    ) { twitchNotificationsObj ->
                        twitchNotificationsObj.jsonObject.mapValues { (key, entry) ->
                            entry.jsonObject.replaceKey("oldMessage", "message").toJsonObject()
                        }.toJsonObject()
                    }.toJsonObject()
                }.toJsonObject().also {
                    val objectString = json.encodeToString(JsonObject.serializer(), it)
                    runBlocking {
                        logger.infoF { "migration output: ${objectString}" }
                    }
                }
            },
            1..2 to VersionMigrator(
                json, JsonObject.serializer(), JsonObject.serializer()
            ) { obj: JsonObject ->
                obj.mapValues { (_, guildObj) ->
                    guildObj.jsonObject.replaceKey("editableRoles", "roleChooser") {
                        JsonObject(emptyMap())
                    }.toJsonObject()
                }.toJsonObject()
            }
        )
    )

    init {
        runBlocking {
            load()
        }
    }

    suspend fun loadConfig(guild: GuildBehavior?): GuildConfiguration? {
        for (i in (0..10)) {
            delay(200)
            logger.infoF { "trying to load config" }
            val guildConfig = getNullable(guild?.id) ?: continue
            logger.infoF { "loaded config" }
            return guildConfig
        }
        return null
    }

    private suspend fun getNullable(guildId: Snowflake?): GuildConfiguration? {
        if (guildId == null) return null
        return configurations[guildId.asString] ?: run {
            logger.errorF { "no state stored for guild $guildId" }
            null
        }
    }

    operator fun get(guild: GuildBehavior?): GuildConfiguration {
        return runBlocking {
            getNullable(guild?.id) ?: relayError("error fetching configuration state")
        }
    }


    operator fun set(guildId: Snowflake?, value: GuildConfiguration?) {
        if (value != null && guildId != null) {
            configurations[guildId.asString] = value
        } else {
            runBlocking {
                logger.errorF { "failed to store in $guildId : $value" }
            }
        }
    }

    operator fun set(guild: GuildBehavior?, value: GuildConfiguration?) {
        set(guild?.id, value)
    }

    private suspend fun load() {
        logger.infoF { "loading from ${configFile.absolutePath}" }
        configFile.absoluteFile.parentFile.mkdirs()
        val newConfigurations  = if (!configFile.exists()) {
            mutableMapOf()
        } else {
            try {
                json.decodeFromString(
                    versionedSerializer,
                    configFile.readText()
                ).toMutableMap()
            } catch (e: SerializationException) {
                e.printStackTrace()
                throw e
            }
        }
        configurations += newConfigurations
    }

    suspend fun save() {
        val serialized = try {
            json.encodeToString(
                versionedSerializer,
                configurations
            )
        } catch (e: SerializationException) {
            e.printStackTrace()
            e.stackTraceToString()
        }
        logger.infoF { "saving to ${configFile.absolutePath}" }
        configFile.absoluteFile.parentFile.mkdirs()
        if (!configFile.exists()) configFile.createNewFile()
        configFile.writeText(serialized)
    }

//    suspend fun initializeGuild(kord: Kord, guildBehavior: GuildBehavior) {
//        val guild = guildBehavior.asGuild()
//        logger.infoF { "configuring ${guild.name}" }
//
//        val serializedConfig = serializedStates[guildBehavior.id.asString]?.also {
//            logger.infoF { "loaded serialized config from file for ${guild.name}" }
//        } ?: ConfigurationStateSerialized().also {
//            logger.infoF { "created default serialized config" }
//        }
//
//        set(guildBehavior, serializedConfig)
//        logger.infoF { "FINISHED configuration of ${guild.name}" }
//    }

}