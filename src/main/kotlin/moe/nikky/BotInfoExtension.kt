package moe.nikky

import com.kotlindiscord.kord.extensions.DISCORD_FUCHSIA
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.rest.builder.message.create.embed
import io.klogging.Klogging
import io.ktor.http.*
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import moe.nikky.db.DiscordbotDatabase
import moe.nikky.twitch.TwitchNotificationExtension
import org.koin.core.component.inject

class BotInfoExtension : Extension(), Klogging {
    override val name: String = "bot-info-extension"

    private val database: DiscordbotDatabase by inject()

    private val config: ConfigurationExtension by inject()

    private val inviteUrl: String = runBlocking {
        val permission = Permissions(
            Permission.ViewChannel,
            Permission.SendMessages,
            Permission.ManageMessages,
            Permission.ManageRoles,
            Permission.ManageWebhooks,
            Permission.ReadMessageHistory,
            Permission.ManageEvents,
        )
        val scopes = listOf(
            "bot",
            "applications.commands"
        )
        URLBuilder("https://discord.com/api/oauth2/authorize").apply {
            parameters.append("client_id", kord.resources.applicationId.toString())
            parameters.append("permissions", permission.code.value)
            parameters.append("scope", scopes.joinToString(" "))
        }.build().toString().also { inviteUrl ->
            logger.infoF { "invite: $inviteUrl" }
        }
    }

    inner class SetAdminRoleArgs : Arguments() {
        val role by role {
            name = "role"
            description = "admin role"
        }
    }

    override suspend fun setup() {
        val self = kord.getSelf()

        ephemeralSlashCommand {
            name = "bot"
            description = "${self.username} related commands"

            ephemeralSubCommand() {
                name = "show-config"
                description = "shows the current configuration of (${self.username} ${self.mention})"
                allowInDms = false

                check {
                    with(config) {
                        requiresBotControl()
                    }
                }

                action {
                    withLogContext(event, guild) { guild ->
                        val guildConfig = database.guildConfigQueries.get(guild.id).executeAsOne()
                        val roleChoosers = database.roleChooserQueries.getAll(guildId = guild.id).executeAsList()

                        respond {
                            val choosableRoles =
                                roleChoosers.map { roleChooser ->
                                    val roleMapping = database.getRoleMapping(guild, roleChooser)
                                    "**${roleChooser.section}**:\n" + roleMapping.entries.joinToString("\n") { (reaction, role) ->
                                        "  ${reaction.mention}: ${role.mention}"
                                    }
                                }
                                    .joinToString("\n\n")

                            val twitch = database.getTwitchConfigs(guild).map { twitchNotif ->
                                "<${twitchNotif.twitchUrl}> ${twitchNotif.role(guild).mention} in ${
                                    twitchNotif.channel(guild).mention
                                }"
                            }.joinToString("\n")
                            content = """
                                |adminRole: ${guildConfig.adminRole(guild)?.mention}
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
                    withLogContextOptionalGuild(event, guild) { guild ->
                        this@BotInfoExtension.logger.infoF { "executed invite" }
                        respond {
                            content = inviteUrl
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = "stats"
                description = "shows some numbers about (${self.username} ${self.mention})"
                action {
                    val roleManagement: RoleManagementExtension? = getKoin().getOrNull()
                    val twitch: TwitchNotificationExtension? = getKoin().getOrNull()

                    withLogContextOptionalGuild(event, guild) { guild ->
                        respond {
                            val guildConfigs = database.guildConfigQueries.getAll().executeAsList()

                            run {
                                val guilds = this@BotInfoExtension.kord.guilds.count()

                                embed {
                                    color = DISCORD_GREEN
                                    title = "General"
                                    description = """
                                        |${self.mention} is in `$guilds` guilds
                                    """.trimMargin()
                                    field {
                                        inline = true
                                        name = "guilds"
                                        value = guilds.toString()
                                    }
                                }
                            }

                            roleManagement?.run {
                                    val roleChoosers = guildConfigs.flatMap { guildConfig ->
                                        database.roleChooserQueries.getAll(guildId = guildConfig.guildId).executeAsList()
                                    }
                                    val roleMappings = roleChoosers.flatMap { config ->
                                        database.roleMappingQueries.getAll(config.roleChooserId).executeAsList()
                                    }

                                    val distinctRoles = roleMappings.distinctBy() {
                                        it.role
                                    }.count()
                                    val distinctEmoji = roleMappings.distinctBy() {
                                        it.reaction
                                    }.count()
                                    val guilds = roleChoosers.distinctBy() {
                                        it.guildId
                                    }.count()

                                    val content = """
                                        |configured in `$guilds` guilds
                                        |`$distinctRoles` roles using `$distinctEmoji` distinct emojis
                                    """.trimMargin()
                                    val pairs = listOf(
                                        "roles" to distinctRoles,
                                        "emojis" to distinctEmoji,
                                    )

                                embed {
                                    color = DISCORD_FUCHSIA
                                    title = "Roles"
                                    description = content
                                    pairs.forEach { (key, v) ->
                                        field {
                                            inline = true
                                            name = key
                                            value = v.toString()
                                        }
                                    }
                                }
                            }

                            twitch?.run {

                                    val twitchConfigs = guildConfigs.flatMap { guildConfig ->
                                        database.twitchConfigQueries.getAll(guildConfig.guildId).executeAsList()
                                    }
                                    val roles = twitchConfigs.distinctBy {
                                        it.role
                                    }.count()
                                    val twitchUsers = twitchConfigs.distinctBy {
                                        it.twitchUserName
                                    }.count()
                                    val guilds = twitchConfigs.distinctBy {
                                        it.guildId
                                    }.count()

                                    val pairs =  listOf(
                                        "streams" to twitchUsers,
                                        "roles" to roles,
                                    )

                                embed {
                                    color = twitch.color
                                    title = "Twitch"
                                    description = """
                                        |configured in `$guilds` guilds
                                        |`$twitchUsers` streams are set up to ping `${roles}` unique roles
                                    """.trimMargin()
                                    pairs.forEach { (key, v) ->
                                        field {
                                            inline = true
                                            name = key
                                            value = v.toString()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

//        event<MessageCreateEvent> {
//            check {
//                failIf("only process DMs") { event.guildId != null }
//                failIf("do not respond to self") { event.message.author?.id == kord.selfId }
//            }
//            action {
//                if (event.message.content.startsWith("invite")) {
//                    event.message.channel.createMessage(inviteUrl)
//                }
//            }
//        }

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
