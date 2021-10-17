package moe.nikky

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
import dev.kord.core.event.message.MessageCreateEvent
import io.klogging.Klogging
import io.klogging.context.logContext
import io.klogging.logger
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.nikky.checks.hasBotControl
import org.koin.core.component.inject

class BotInfoExtension : Extension(), Klogging {
    override val name: String = "Bot Info Extension"

    private val config: ConfigurationService by inject()

    private val inviteUrl: String = runBlocking {
        val permission = Permissions(
            Permission.ViewChannel,
            Permission.ManageRoles,
        )
        val scopes = listOf(
            "bot",
            "applications.commands"
        )
        URLBuilder("https://discord.com/api/oauth2/authorize").apply {
            parameters.append("client_id", kord.resources.applicationId.asString)
            parameters.append("permissions", permission.code.value)
            parameters.append("scope", scopes.joinToString(" "))
        }.build().toString().also { inviteUrl ->
            logger.infoF { "invite: $inviteUrl" }
        }
    }

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
                    hasBotControl(config)
                }

                action {
                    withLogContext(event, guild?.asGuild() ?: relayError("cannot load guild")) { guild ->
                        val state = config[guild]

                        respond {
                            val choosableRoles = state.roleChooser.entries
                                .joinToString("\n\n") { (section, rolePickerMessageState) ->
                                    "**$section**:\n" + rolePickerMessageState.roleMapping.entries.joinToString("\n") { (reaction, role) ->
                                        "  ${reaction.mention}: ${role.mention}"
                                    }
                                }

                            val twitch = state.twitchNotifications.entries.joinToString("\n") {(_, twitchNotif) ->
                                "<${twitchNotif.twitchUrl}> ${twitchNotif.role.mention} in ${twitchNotif.channel.mention}"
                            }
                            content = """
                                |adminRole: ${state.adminRole?.mention}
                                |role pickers: 
                                ${choosableRoles.indent("|  ")}
                                |twitch notifications:
                                ${twitch.indent("|  ")}
                            """.trimMargin()
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = "invite"
                description = "get invite url"

                action {
                    withLogContext(event, guild?.asGuild() ?: relayError("cannot load guild")) { guild ->
                        this@BotInfoExtension.logger.infoF { "executed invite" }
                        respond {
                            content = inviteUrl
                        }
                    }
                }
            }

        }

        event<MessageCreateEvent> {
            check {
                failIf("only process DMs") { event.guildId != null }
                failIf("do not respond to self") { event.message.author?.id == kord.selfId }
            }
            action {
                if (event.message.content.startsWith("invite")) {
                    event.message.channel.createMessage(inviteUrl)
                }
            }
        }

//        event<GuildCreateEvent> {
//            action {
//                val guild = event.guild
//                withContext(
//                    logContext(
//                        "guild" to "'${guild.name}'",
//                        "guildId" to guild.id.asString,
//                        "event" to event::class.simpleName,
//                    )
//                ) {
//
//                }
//            }
//        }
    }
}
