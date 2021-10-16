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
import mu.KotlinLogging
import org.koin.core.component.inject
import kotlin.system.exitProcess

class ConfigurationExtension : Extension() {
    override val name: String = "Configuration Extension"

    private val config: ConfigurationService by inject()

    private val logger = KotlinLogging.logger {}

    inner class SetAdminRoleArgs : Arguments() {
        val role by role("role", "admin role")
    }

    override suspend fun setup() {
        val botName = kord.getSelf().username
        ephemeralSlashCommand {
            name = "config"
            description = "$botName related commands"

            ephemeralSubCommand {
                name = "reload"
                description = "reload config of $botName from disk"

                check {
                    hasPermission(Permission.Administrator)
                }

                action {
                    val kord = this@ConfigurationExtension.kord
                    val guild = guild?.asGuild() ?: relayError("cannot load guild")
                    this@ConfigurationExtension.logger.info { "reloading configurations for ${guild.name}" }
                    config.initializeGuild(kord, guild)

                    respond {
                        content = "reloaded configuration via command"
                    }
                }
            }

            ephemeralSubCommand(::SetAdminRoleArgs) {
                name = "adminset"
                description = "sets the admin role"

                check {
                    hasPermission(Permission.Administrator)
                }

                action {
                    val guild = guild?.asGuild() ?: relayError("cannot load guild")
                    val state = config[guild]

                    config[guild] = state.copy(
                        adminRole = arguments.role
                    )
                    config.save()

                    respond {
                        content = "config saved"
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
                    val guild = guild?.asGuild() ?: relayError("cannot load guild")
                    val state = config[guild]

                    config[guild] = state.copy(
                        adminRole = null
                    )
                    config.save()
                    respond {
                        content = "config saved"
                    }
                }
            }

        }

//        ephemeralSlashCommand() {
//            name = commandPrefix + "clearCommands"
//            description = "WARNING: this is intended to break commands"
//
//            check {
//                hasPermission(Permission.Administrator)
//            }
//            ephemeralSubCommand {
//                name = "guild"
//                description = "WARNING: this is intended to break commands - clears guild commands"
//
//                check {
//                    hasPermission(Permission.Administrator)
//                }
//                action {
//                    val names = guild!!.commands.toList().map { command ->
//                        this@ConfigurationExtension.logger.info { "deleting ${command.name} ${command}" }
//                        command.delete()
//                        command.name
//                    }
//                    respond {
//                        content = "deleted `${names.joinToString("`, `")}`"
//                    }
//                }
//            }
//
//            ephemeralSubCommand() {
//                name = "global"
//                description = "WARNING: this is intended to break commands - clears global commands"
//
//                check {
//                    hasPermission(Permission.Administrator)
//                }
//                action {
//                    val kord = this@ephemeralSlashCommand.kord
//                    val names = kord.globalCommands.toList().map { command ->
//                        this@ConfigurationExtension.logger.info { "deleting ${command.name} ${command}" }
//                        command.delete()
//                    }
//                    respond {
//                        content = "deleted `${names.joinToString("`, `")}`"
//                    }
//                }
//            }
//        }

        event<GuildCreateEvent> {
            action {
                logger.info { "guild create event" }

                try {
                    config.initializeGuild(kord, event.guild)
                } catch (e: Exception) {
                    logger.error { "failed loading config" }
                    e.printStackTrace()
                    exitProcess(-1)
                }

                config.save()
            }
        }
    }
}
