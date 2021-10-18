package moe.nikky

import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import io.klogging.Klogging
import io.klogging.logger
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
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import java.io.File
import java.util.*

class ConfigurationService : KoinComponent, Klogging {
    private val configFolder = File(envOrNull("CONFIG_DIR") ?: "data")
    private val configFile = configFolder.resolve("config.json")

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val states: MutableMap<Snowflake, BotState> = Collections.synchronizedMap(mutableMapOf())
    private var serializedStates: Map<String, ConfigurationStateSerialized> = emptyMap()

    private fun <K, V> Map<K, V>.replaceKey(oldKey: K, newKey: K, transform: (V) -> V = { it }): Map<K, V> =
        this - oldKey + (newKey to transform(getValue(oldKey)))

    private fun <K, V> Map<K, V>.replaceValue(key: K, transform: (V) -> V): Map<K, V> =
        this  + (key to transform(getValue(key)))

    private fun Map<String, JsonElement>.toJsonObject() = JsonObject(this)

    val versionedSerializer = VersionedSerializer(
        MapSerializer(
            String.serializer(),
            ConfigurationStateSerialized.serializer()
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

    suspend fun loadConfig(guild: GuildBehavior?): BotState? {
        for (i in (0..10)) {
            delay(200)
            logger.infoF { "trying to load config" }
            val state = getNullable(guild?.id) ?: continue
            logger.infoF { "loaded config" }
            return state
        }
        return null
    }

    private suspend fun getNullable(guildId: Snowflake?): BotState? {
        if (guildId == null) return null
        return states[guildId] ?: run {
            logger.errorF {"no state stored for guild $guildId" }
            null
        }
    }

    operator fun get(guild: GuildBehavior?): BotState {
        return runBlocking {
            getNullable(guild?.id) ?: relayError("error fetching configuration state")
        }
    }


    operator fun set(guildId: Snowflake?, value: BotState?) {
        if (value != null && guildId != null) {
            states[guildId] = value
        } else {
            runBlocking {
                logger.errorF { "failed to store in $guildId : $value" }
            }
        }
    }

    operator fun set(guild: GuildBehavior?, value: BotState?) {
        set(guild?.id, value)
    }

    private suspend fun load() {
        logger.infoF { "loading from ${configFile.absolutePath}" }
        configFile.absoluteFile.parentFile.mkdirs()
        if(!configFile.exists()) {
            serializedStates = emptyMap()
        } else {
            serializedStates = try {
                json.decodeFromString(
                    versionedSerializer,
                    configFile.readText()
                )
            } catch (e: SerializationException) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun save() {
        serializedStates = states.entries.associate { (k, v) ->
            k.asString to v.asSerialized()
        }.also { serializedStates ->
            val serialized = try {
                json.encodeToString(
                    versionedSerializer,
                    serializedStates
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
    }

    suspend fun initializeGuild(kord: Kord, guildBehavior: GuildBehavior) {
        val guild = guildBehavior.asGuild()
        logger.infoF { "configuring ${guild.name}" }

        val serializedConfig = serializedStates[guildBehavior.id.asString]?.also {
            logger.infoF { "loaded serialized config from file for ${guild.name}" }
        } ?: ConfigurationStateSerialized().also {
            logger.infoF { "created default serialized config" }
        }

        logger.infoF { "building state" }
        val state = serializedConfig.resolve(kord, guildBehavior)

        this[guildBehavior] = state
        logger.infoF { "FINISHED configuration of ${guild.name}" }
    }

}