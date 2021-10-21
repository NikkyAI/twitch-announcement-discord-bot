package moe.nikky

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.core.event.guild.GuildCreateEvent
import io.klogging.Klogging
import kotlinx.atomicfu.locks.ReentrantLock
import org.koin.core.component.inject

class ConfigurationExtension : Extension(), Klogging {
    override val name: String = "Configuration Extension"

    private val config: ConfigurationService by inject()

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
                        val guildConfig = config[guild]

                        config[guild] = guildConfig.copy(
                            adminRole = arguments.role.id
                        )
                        config.save()

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
                        val guildConfig = config[guild]

                        config[guild] = guildConfig.copy(
                            adminRole = null
                        )
                        config.save()
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
                    val guildConfig = config[guild]
                    if(guildConfig.name.isEmpty()) {
                        config[guild] = guildConfig
                        config.save()
                    }
//                    try {
//                        config.initializeGuild(kord, guild)
//                    } catch (e: DiscordRelayedException) {
//                        logger.errorF(e) { "failed loading config" }
//                        error(e.reason)
//                    } catch (e: Exception) {
//                        logger.errorF(e) { "failed loading config" }
//                        error(e.message ?: "unknown error message")
//                    }

//                    config.save()
                }
            }
        }
    }
}
