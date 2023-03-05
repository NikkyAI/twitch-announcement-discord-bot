package moe.nikky

import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.storage.Data
import com.kotlindiscord.kord.extensions.storage.StorageType
import com.kotlindiscord.kord.extensions.storage.StorageUnit
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.getLocale
import com.kotlindiscord.kord.extensions.utils.translate
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.Role
import dev.kord.core.event.Event
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.interaction.InteractionCreateEvent
import io.klogging.Klogging
import kotlinx.serialization.Serializable
import moe.nikky.checks.anyCheck
import moe.nikky.checks.hasRoleNullable
import moe.nikky.db.DiscordbotDatabase
import net.peanuuutz.tomlkt.TomlComment
import org.koin.core.component.inject
import org.koin.dsl.module
import java.util.*

class ConfigurationExtension : Extension(), Klogging {
    override val name: String = "configuration-extension"

    init {
        bot.getKoin().loadModules(
            listOf(
                module {
                    single { this@ConfigurationExtension }
                }
            )
        )
    }

    private val database: DiscordbotDatabase by inject()

    private val guildConfig = StorageUnit(
        StorageType.Config,
        name,
        "guild-config",
        BotConfig::class
    )

    private fun GuildBehavior.config() =
        guildConfig
            .withGuild(id)

    inner class SetAdminRoleArgs : Arguments() {
        val role by role {
            name = "role"
            description = "admin role"
        }
    }

    override suspend fun setup() {
        val self = kord.getSelf()
        ephemeralSlashCommand {
            name = "config"
            description = "${self.username} related commands"
            allowInDms = false

            ephemeralSubCommand(::SetAdminRoleArgs) {
                name = "adminset"
                description = "sets the admin role"

                check {
                    hasPermission(Permission.Administrator)
                }

                action {
                    withLogContext(event, guild) { guild ->

                        val configUnit = guild.config()
                        val config = configUnit.get() ?: BotConfig()
                        config.adminRole = arguments.role.id
                        config.adminRoleName = arguments.role.name
                        configUnit.save(config)
                        database.guildConfigQueries.updateAdminRole(
                            adminRole = arguments.role.id,
                            guildId = guild.id
                        )

                        respond {
                            content = "config saved"
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = "adminunset"
                description = "clears admin role"
                check {
                    hasPermission(Permission.Administrator)
                }

                action {
                    withLogContext(event, guild) { guild ->
                        val configUnit = guild.config()
                        val config = configUnit.get() ?: BotConfig()
                        config.adminRole = null
                        config.adminRoleName = null
                        configUnit.save(config)
                        database.guildConfigQueries.updateAdminRole(
                            adminRole = null,
                            guildId = guild.id
                        )
                        respond {
                            content = "config saved"
                        }
                    }
                }
            }
        }

        event<GuildCreateEvent> {
            action {
                convertConfig(event.guild)
                withLogContext(event, event.guild) { guild ->
                    database.guildConfigQueries.upsert(
                        guildId = guild.id,
                        name = guild.name,
                    )
                }
            }
        }
    }

    suspend fun CheckContext<InteractionCreateEvent>.requiresBotControl() {
        requiresBotControl(event.getLocale())
    }

    suspend fun CheckContext<Event>.requiresBotControl(locale: Locale) {
        val guild = guildFor(event)?.asGuildOrNull() ?: relayError("cannot load guild")
        val configUnit = guild.config()
        val guildConfig = configUnit.get()

        anyCheck(
            {
                hasPermission(Permission.Administrator)
            },
            {
                hasRoleNullable { event ->
                    guildConfig?.adminRole(guild)
                }
            }
        )
        if (!passed) {
            val adminRole = guildConfig?.adminRole(guild)
            fail(
                "must have permission: **${Permission.Administrator.translate(locale)}**"
                        + (adminRole?.let { "\nor role: ** ${it.mention}**" }
                    ?: "\nand no adminrole is configured")
            )
        }
    }

    private suspend fun convertConfig(guild: GuildBehavior) {
        val guildConfig = database.guildConfigQueries.get(guild.id).executeAsOne()

        val adminRole = guildConfig.adminRole(guild)

        guild.config().save(
            BotConfig(
                adminRole = adminRole?.id,
                adminRoleName = adminRole?.name
            )
        )
    }
}

@Serializable
@Suppress("DataClassShouldBeImmutable", "MagicNumber")
data class BotConfig(
    @TomlComment(
        "role that should be treated as administrator for bot control"
    )
    var adminRole: Snowflake? = null,
    var adminRoleName: String? = null,
) : Data {
    suspend fun adminRole(guildBehavior: GuildBehavior): Role? {
        return adminRole?.let { guildBehavior.getRoleOrNull(it) }
    }
}
