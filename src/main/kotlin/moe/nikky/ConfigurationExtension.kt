package moe.nikky

import dev.kordex.core.checks.guildFor
import dev.kordex.core.checks.hasPermission
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.storage.Data
import dev.kordex.core.storage.StorageType
import dev.kordex.core.storage.StorageUnit
import dev.kordex.core.utils.getLocale
import dev.kordex.core.utils.translate
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.Role
import dev.kord.core.event.Event
import dev.kord.core.event.interaction.InteractionCreateEvent
import io.github.xn32.json5k.SerialComment
import io.klogging.Klogging
import kotlinx.serialization.Serializable
import moe.nikky.checks.anyCheck
import moe.nikky.checks.hasRoleNullable
import net.peanuuutz.tomlkt.TomlComment
import org.koin.dsl.module
import java.util.Locale

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

    private val guildConfig = StorageUnit(
        StorageType.Config,
        name,
        "guild-config",
        GuildConfig::class
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
//            description = "${self.username} related commands"
            description = "self.username related commands"
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
                        val config = configUnit.get() ?: GuildConfig()
                        configUnit.save(config.setAdminRole(arguments.role))

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
                        val config = configUnit.get() ?: GuildConfig()
                        configUnit.save(config.setAdminRole(null))
                        respond {
                            content = "config saved"
                        }
                    }
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
        val adminRole = guildConfig?.adminRole(guild)

        anyCheck(
            {
                hasPermission(Permission.Administrator)
            },
            {
                hasRoleNullable { event ->
                    adminRole
                }
            }
        )
        if (!passed) {
            fail(
                "must have permission: **${Permission.Administrator.translate(locale)}**"
                        + (adminRole?.let { "\nor role: ** ${it.mention}**" }
                    ?: "\nand no adminrole is configured")
            )
        }
    }

    suspend fun loadConfig(guild: GuildBehavior): GuildConfig? {
        return guild.config().get()
    }
}

@Serializable
@Suppress("DataClassShouldBeImmutable", "MagicNumber")
data class GuildConfig(
    @TomlComment(
        "role that should be treated as administrator for bot control"
    )
    @SerialComment(
        "role that should be treated as administrator for bot control"
    )
    val adminRole: Snowflake? = null,
    @TomlComment(
        "human readable name of the admin role"
    )
    @SerialComment(
        "human-readable name of the admin role"
    )
    val adminRoleName: String? = null,
) : Data {
    suspend fun adminRole(guildBehavior: GuildBehavior): Role? {
        return adminRole?.let { guildBehavior.getRoleOrNull(it) }
    }

    fun setAdminRole(role: Role?): GuildConfig {
        return copy(
            adminRole = role?.id,
            adminRoleName = role?.name,
        )
    }
}
