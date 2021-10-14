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
import dev.kord.common.entity.Permissions
import dev.kord.core.event.guild.GuildCreateEvent
import mu.KotlinLogging
import org.koin.core.component.inject

class BotInfoExtension : Extension() {
    override val name: String = "Bot Info Extension"

    private val config: ConfigurationService by inject()

    private val logger = KotlinLogging.logger {}
    private lateinit var inviteUrl: String

    inner class SetAdminRoleArgs : Arguments() {
        val role by role("role", "admin role")
    }

    override suspend fun setup() {
        val botName = kord.getSelf().username
        ephemeralSlashCommand {
            name = "bot"
            description = "$botName related commands"
            ephemeralSubCommand() {
                name = "info"
                description = "some info about the bot ($botName)"

                check {
                    hasPermission(Permission.Administrator)
                }

                action {
//                    val ack = interaction.acknowledgeEphemeral()

//                    delay(4_000)
                    val guild = guild?.asGuild() ?: errorMessage("cannot load guild")
                    val state = config[guild] ?: errorMessage("error fetching data")

                    respond {
                        val choosableRoles = state.roleChooser.entries
                            .joinToString("\n\n") { (section, rolePickerMessageState) ->
                                "**$section**:\n" + rolePickerMessageState.roleMapping.entries.joinToString("\n") { (reaction, role) ->
                                    "  ${reaction.mention}: ${role.mention}"
                                }
                            }

                        content = """
                        |guild: ${guild.name}
                        |name: ${state.botname}
                        |editable roles: 
                        ${choosableRoles.indent("|  ")}
                    """.trimMargin()
                    }
                }
            }

            ephemeralSubCommand {
                name = "invite"
                description = "get invite url"

                action {
                    respond {
                        content = inviteUrl
                    }
                }
            }

        }

        event<GuildCreateEvent> {
            action {
                logger.info { "guild create event" }

                val permission = Permissions(
                    Permission.ViewChannel,
//                    Permission.ManageChannels,
                    Permission.ManageRoles,
                    Permission.ManageWebhooks,
                    Permission.ManageMessages,
//                    Permission.ReadMessageHistory,
                    Permission.AddReactions,
                )
                val scopes = listOf(
                    "bot",
                    "applications.commands"
                )
                inviteUrl = "https://discord.com/api/oauth2/authorize" +
                        "?client_id=${kord.resources.applicationId.asString}" +
                        "&permissions=${permission.code.value}" +
                        "&scope=${scopes.joinToString("%20")}"

                logger.info { "invite: $inviteUrl" }

            }
        }
    }
}
