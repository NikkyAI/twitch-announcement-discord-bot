package moe.nikky

import com.kotlindiscord.kord.extensions.utils.envOrNull
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.rest.request.KtorRequestException
import io.klogging.context.logContext
import io.klogging.logger
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.nikky.db.*
import java.io.File
import java.sql.SQLException

private val logger = logger(object{}.javaClass.enclosingClass.canonicalName)


@Deprecated("stop using the database")
fun DiscordbotDatabase.Companion.load(): DiscordbotDatabase = runBlocking {
    try {
        Class.forName("org.sqlite.JDBC")
    } catch (e: ClassNotFoundException) {
        logger.errorF(e) { "could not load sqlite classes" }
    }

    val configFolder = File(envOrNull("CONFIG_DIR") ?: "data")
    val configFile = configFolder.resolve("config.db")
    val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${configFile.path.replace('\\','/')}")

    val snowFlakeAdapter = object : ColumnAdapter<Snowflake, Long> {
        override fun decode(databaseValue: Long) = Snowflake(databaseValue.toULong())
        override fun encode(value: Snowflake) = value.value.toLong()
    }
    val database = DiscordbotDatabase(
        driver = driver,
        guildConfigAdapter = moe.nikky.db.GuildConfig.Adapter(
            guildIdAdapter = snowFlakeAdapter,
            adminRoleAdapter = snowFlakeAdapter,
        ),
        roleChooserConfigAdapter = moe.nikky.db.RoleChooserConfig.Adapter(
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

    val oldVersion: Long = try {
        database.schemaVersionQueries.getSchemaVersion().executeAsOne().version!!
    } catch (e: SQLException) {
        e.printStackTrace()
        0L
    }
    logger.infoF { "database is at version: $oldVersion" }

    if (oldVersion < DiscordbotDatabase.Schema.version) {
        logger.infoF { "updating $oldVersion -> ${DiscordbotDatabase.Schema.version}" }
        DiscordbotDatabase.Schema.migrate(
            driver = driver,
            oldVersion = oldVersion,
            newVersion = DiscordbotDatabase.Schema.version,
        )
    } else {
        logger.infoF { "schema up to date" }
    }

    database
}

@Deprecated("stop using the database")
fun DiscordbotDatabase.getTwitchConfigs(guild: Guild): List<TwitchConfig> {
    return twitchConfigQueries.getAll(guild.id).executeAsList()
}


@Deprecated("stop using the database")
suspend fun DiscordbotDatabase.getRoleMapping(guildBehavior: GuildBehavior, roleChooser: moe.nikky.db.RoleChooserConfig): List<Pair<ReactionEmoji, Role>> {
    val roleMapping = roleMappingQueries.getAll(roleChooser.roleChooserId).executeAsList().associate { roleChooserMapping ->
        roleChooserMapping.reaction to roleChooserMapping.role
    }
    return roleMapping.entries.map { (reactionEmojiName, role) ->
        val reactionEmoji = guildBehavior.emojis.firstOrNull { it.mention == reactionEmojiName }
            ?.let { ReactionEmoji.from(it) }
            ?: ReactionEmoji.Unicode(reactionEmojiName)
        reactionEmoji to guildBehavior.getRole(role)
    }
}

suspend fun moe.nikky.db.GuildConfig.adminRole(guildBehavior: GuildBehavior): Role? {
    return adminRole?.let { guildBehavior.getRoleOrNull(it) }
}

val TwitchConfig.twitchUrl: String get() = "https://twitch.tv/$twitchUserName"

suspend fun TwitchConfig.role(guildBehavior: GuildBehavior): Role {
    return guildBehavior.getRoleOrNull(role) ?: relayError("role $role could not be loaded")
}

suspend fun TwitchConfig.channel(guildBehavior: Guild): TopGuildMessageChannel {
    return withContext(
        logContext("guild" to guildBehavior.name)
    ) {
        guildBehavior.getChannelOfOrNull<TextChannel>(channel)
            ?: guildBehavior.getChannelOfOrNull<NewsChannel>(channel)
            ?: relayError("channel $channel in '${guildBehavior.name}' could not be loaded as TextChannel")
    }
}


suspend fun moe.nikky.db.RoleChooserConfig.channel(guildBehavior: Guild): TextChannel {
    return withContext(
        logContext("guild" to guildBehavior.name)
    ) {
        guildBehavior.getChannelOfOrNull<TextChannel>(channel)
            ?: relayError("channel $channel in '${guildBehavior.name}' could not be loaded as TextChannel")
    }
}


suspend fun moe.nikky.db.RoleChooserConfig.getMessageOrRelayError(guildBehavior: Guild): Message? = try {
    channel(guildBehavior).getMessageOrNull(message)
} catch (e: KtorRequestException) {
    logger.errorF { e.message }
    relayError("cannot access message $message")
}
