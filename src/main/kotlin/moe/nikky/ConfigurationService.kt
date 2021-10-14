package moe.nikky

import com.kotlindiscord.kord.extensions.utils.envOrNull
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
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

class ConfigurationService : KoinComponent {
    private val logger = KotlinLogging.logger {}
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
                    logger.info { "migration output: \n$objectString" }
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
        load()
    }

    operator fun get(guildId: Snowflake?): BotState? {
        if (guildId == null) return null
        return states[guildId] ?: run {
            logger.error { "no state stored for guild $guildId" }
            null
        }
    }

    operator fun get(guild: GuildBehavior?): BotState? {
        return get(guild?.id)
    }


    operator fun set(guildId: Snowflake?, value: BotState?) {
        if (value != null && guildId != null) {
            states[guildId] = value
        } else {
            logger.error { "failed to store in $guildId : $value" }
        }
    }

    operator fun set(guild: GuildBehavior?, value: BotState?) {
        set(guild?.id, value)
    }

    private fun load() {
        logger.info { "loading from ${configFile.absolutePath}" }
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

    fun save() {
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
            logger.info { "saving to ${configFile.absolutePath}" }
            configFile.absoluteFile.parentFile.mkdirs()
            if (!configFile.exists()) configFile.createNewFile()
            configFile.writeText(serialized)
        }
    }

    suspend fun initializeGuild(kord: Kord, guildBehavior: GuildBehavior) {
        val guild = guildBehavior.asGuild()
        logger.info { "configuring ${guild.name}" }

        serializedStates[guildBehavior.id.asString]?.resolve(guildBehavior)?.let { state ->
            states[guildBehavior.id] = state
            logger.info { "loaded successfully from file" }
            return
        }

        logger.info { "building state" }
        val defaultConfigState = BotState(
            botname = guildBehavior.getMember(kord.selfId).displayName,
            guildBehavior = guildBehavior,
        )

        set(guildBehavior, defaultConfigState)
        logger.info { "FINISHED configuration of ${guild.name}" }
    }
}