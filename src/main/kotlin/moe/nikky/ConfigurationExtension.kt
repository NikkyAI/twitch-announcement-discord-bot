package moe.nikky

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.*
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.profileLink
import dev.kord.cache.api.data.description
import dev.kord.common.entity.Permission
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.rest.Image
import io.klogging.Klogging
import kotlinx.atomicfu.locks.ReentrantLock
import moe.nikky.db.DiscordbotDatabase
import org.koin.core.component.inject
import java.awt.image.ImagingOpException

class ConfigurationExtension : Extension(), Klogging {
    override val name: String = "Configuration Extension"

    private val database: DiscordbotDatabase by inject()

    inner class SetAdminRoleArgs : Arguments() {
        val role by role("role", "admin role")
    }

    override suspend fun setup() {
        val botName = kord.getSelf().username
        ephemeralSlashCommand {
            name = "config"
            description = "$botName related commands"

//            ephemeralSubCommand {
//                name = "reload"
//                description = "reload config of $botName from disk"
//
//                check {
//                    hasPermission(Permission.Administrator)
//                }
//
//                action {
//                    withLogContext(event, guild) { guild ->
//                        val kord = this@ConfigurationExtension.kord
//                        val logger = this@ConfigurationExtension.logger
//                        logger.info { "reloading configurations for ${guild.name}" }
//                        config.initializeGuild(kord, guild)
//
//                        respond {
//                            content = "reloaded configuration via command"
//                        }
//                    }
//                }
//            }

            ephemeralSubCommand(::SetAdminRoleArgs) {
                name = "adminset"
                description = "sets the admin role"

                check {
                    hasPermission(Permission.Administrator)
                }

                action {
                    withLogContext(event, guild) { guild ->
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
                this@ConfigurationExtension.name
                withLogContext(event, event.guild) { guild ->
                    database.guildConfigQueries.upsert(
                        guildId = guild.id,
                        name = guild.name,
                    )
                }
            }
        }
    }
}
