package moe.nikky

import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.Guild
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.firstOrNull
import io.klogging.Klogging
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import moe.nikky.db.*
import org.koin.core.component.KoinComponent
import java.io.File
import java.sql.SQLException
import java.util.*

class ConfigurationService : KoinComponent, Klogging {
    private val configFolder = File(envOrNull("CONFIG_DIR") ?: "data")
    private val configFile = configFolder.resolve("config.db")

    private val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${configFile.path.replace('\\','/')}")
    val database = runBlocking { load() }

    fun getTwitchConfigs(guild: Guild): List<TwitchConfig> {
        return database.twitchConfigQueries.getAll(guild.id).executeAsList()
    }

    fun getRoleChoosers(guildId: Snowflake): List<RoleChooserConfig> {
        return database.roleChooserQueries.getAll(guildId).executeAsList()
    }
    fun findRoleChooser(guildId: Snowflake, section: String, channel: Snowflake): RoleChooserConfig? {
        return database.roleChooserQueries.find(guildId, section, channel).executeAsOneOrNull()
    }

    suspend fun getRoleMapping(guildBehavior: GuildBehavior, roleChooserId: Long): Map<ReactionEmoji, Role> {
        val roleMapping = database.roleMappingQueries.getAll(roleChooserId).executeAsList().associate { roleChooserMapping ->
            roleChooserMapping.reaction to roleChooserMapping.role
        }
        return roleMapping.entries.associate { (reactionEmojiName, role) ->
            val reactionEmoji = guildBehavior.emojis.firstOrNull { it.mention == reactionEmojiName }
                ?.let { ReactionEmoji.from(it) }
                ?: ReactionEmoji.Unicode(reactionEmojiName)
            reactionEmoji to guildBehavior.getRole(role)
        }
    }

    private suspend fun load(): DiscordbotDatabase {
        val snowFlakeAdapter = object : ColumnAdapter<Snowflake, Long> {
            override fun decode(databaseValue: Long) = Snowflake(databaseValue.toULong())
            override fun encode(value: Snowflake) = value.value.toLong()
        }
        val database = DiscordbotDatabase(
            driver = driver,
            guildConfigAdapter = GuildConfig.Adapter(
                guildIdAdapter = snowFlakeAdapter,
                adminRoleAdapter = snowFlakeAdapter,
            ),
            roleChooserConfigAdapter = RoleChooserConfig.Adapter(
                guildIdAdapter = snowFlakeAdapter,
                channelAdapter = snowFlakeAdapter,
                messageAdapter = snowFlakeAdapter
            ),
            roleChooserMappingAdapter = RoleChooserMapping.Adapter(
                roleAdapter = snowFlakeAdapter
            ),
            twitchConfigAdapter = TwitchConfig.Adapter(
                guildIdAdapter = snowFlakeAdapter,
                channelAdapter = snowFlakeAdapter,
                roleAdapter = snowFlakeAdapter,
                messageAdapter = snowFlakeAdapter,
            )
        )

        val oldVersion: Int = try {
            database.schemaVersionQueries.getSchemaVersion().executeAsOne().version?.toInt()!!
        } catch (e: SQLException) {
            e.printStackTrace()
            0
        }
        println("database is at version: $oldVersion")

        if (oldVersion < DiscordbotDatabase.Schema.version) {
            println("updating $oldVersion -> ${DiscordbotDatabase.Schema.version}")
            DiscordbotDatabase.Schema.migrate(
                driver = driver,
                oldVersion = oldVersion,
                newVersion = DiscordbotDatabase.Schema.version,
            )

//        database.schemaVersionQueries.setSchemaVersion(
//            DiscordbotDatabase.Schema.version
//        )
        } else {
            println("schema up to date")
        }

        return database
    }

    suspend fun save() {
//        lock.withLock {
//            logger.infoF { "saving to ${configFile.absolutePath}" }
//            val serialized = try {
//                json.encodeToString(
//                    versionedSerializer,
//                    configurations
//                )
//            } catch (e: SerializationException) {
//                e.printStackTrace()
////                e.stackTraceToString()
//                return@withLock
//            }
//            configFile.absoluteFile.parentFile.mkdirs()
////        if (!configFile.exists()) configFile.createNewFile()
//            configFile.writeText(serialized)
//        }
    }


}