package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import com.kotlindiscord.kord.extensions.utils.getLocale
import com.kotlindiscord.kord.extensions.utils.respond
import com.kotlindiscord.kord.extensions.utils.translate
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Guild
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import dev.kord.core.live.onReactionRemove
import dev.kord.rest.request.KtorRequestException
import io.klogging.Klogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import moe.nikky.checks.hasBotControl
import moe.nikky.converter.reactionEmoji
import moe.nikky.db.DiscordbotDatabase
import moe.nikky.db.RoleChooserConfig
import org.koin.core.component.inject
import kotlin.time.ExperimentalTime

class RoleManagementExtension : Extension(), Klogging {
    override val name: String = "Role management"
    private val database: DiscordbotDatabase by inject()
    private val liveMessageJobs = mutableMapOf<Long, Job>()

    companion object {
        private val requiredPermissions = arrayOf(
            Permission.ViewChannel,
            Permission.SendMessages,
            Permission.AddReactions,
            Permission.ManageMessages,
            Permission.ReadMessageHistory,
        )
    }

    inner class AddRoleArg : Arguments() {
        val section by string("section", "Section Title")
        val reaction by reactionEmoji("emoji", "Reaction Emoji")
        val role by role("role", "Role")
        val channel by optionalChannel("channel", "channel")
    }

    inner class RemoveRoleArg : Arguments() {
        val section by string("section", "Section Title")
        val reaction by reactionEmoji("emoji", "Reaction Emoji")
        val channel by optionalChannel("channel", "channel")
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun setup() {
        chatGroupCommand {
            name = "role"
            description = "Add or remove roles"
            requireBotPermissions(Permission.ManageRoles)

            chatCommand(::AddRoleArg) {
                name = "add"
                description = "adds a new reaction to role mapping"

                check {
                    hasBotControl(database, event.getLocale())
                    guildFor(event)?.asGuild()?.botHasPermissions(
                        Permission.ManageRoles
                    )
                }

                requireBotPermissions(
                    *requiredPermissions
                )

                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = add(
                            guild,
                            arguments,
                            event.message.channel
                        )

                        val response = event.message.respond {
                            content = responseMessage
                        }
                        event.message.delete()
                        launch {
                            delay(30_000)
                            response.delete()
                        }
                    }
                }
            }
            chatCommand(::RemoveRoleArg) {
                name = "remove"
                description = "removes a role mapping"

                check {
                    hasBotControl(database, event.getLocale())
                }

                requireBotPermissions(
                    *requiredPermissions
                )

                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = remove(
                            guild,
                            arguments,
                            event.message.channel
                        )

                        val response = event.message.respond {
                            content = responseMessage
                        }
                        event.message.delete()
                        launch {
                            delay(30_000)
                            response.delete()
                        }
                    }
                }
            }
        }
        ephemeralSlashCommand {
            name = "role"
            description = "Add or remove roles"
            requireBotPermissions(Permission.ManageRoles)

            ephemeralSubCommand(::AddRoleArg) {
                name = "add"
                description = "adds a new reaction to role mapping"

                check {
                    hasBotControl(database)
                    guildFor(event)?.asGuild()?.botHasPermissions(
                        Permission.ManageRoles
                    )
                }

                requireBotPermissions(
                    *requiredPermissions
                )

                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = add(
                            guild,
                            arguments,
                            event.interaction.channel
                        )

                        respond {
                            content = responseMessage
                        }
                    }
                }
            }

            ephemeralSubCommand(::RemoveRoleArg) {
                name = "remove"
                description = "removes a role mapping"

                check {
                    hasBotControl(database)
                }

                requireBotPermissions(
                    *requiredPermissions
                )

                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = remove(
                            guild,
                            arguments,
                            event.interaction.channel
                        )

                        respond {
                            content = responseMessage
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = "check"
                description = "check permissions in channel"

                requireBotPermissions(
                    *requiredPermissions
                )
                check {
                    hasBotControl(database)
                    guildFor(event)?.asGuild()?.botHasPermissions(
                        Permission.ManageRoles
                    )
                }

                action {
                    withLogContext(event, guild) { guild ->
                        respond {
                            content = "OK"
                        }
                    }
                }
            }
        }

        event<GuildCreateEvent> {
            action {
                withLogContext(event, event.guild) { guild ->
                    val roleChoosers = database.roleChooserQueries.getAll(guildId = guild.id).executeAsList()

                    roleChoosers.map { it.channel(guild) }.distinct().forEach {
                        val channel = it.asChannel()
                        val missingPermissions = requiredPermissions.filterNot { permission ->
                            channel.botHasPermissions(permission)
                        }

                        if (missingPermissions.isNotEmpty()) {
                            val locale = guild.preferredLocale
                            logger.errorF {
                                "missing permissions in ${guild.name} #${channel.name} ${
                                    missingPermissions.joinToString(", ") { it.translate(locale) }
                                }"
                            }
                        }
                    }

                    roleChoosers.forEach { roleChooserConfig ->
//                    if(rolePickerMessageState.channel !in validChannels) return@forEach
                        try {
                            val message = roleChooserConfig.getMessageOrRelayError(guild)
                                ?: roleChooserConfig.channel(guild)
                                    .createMessage("placeholder for section ${roleChooserConfig.section}")
                            val roleMapping = database.getRoleMapping(guild, roleChooser = roleChooserConfig)
                            message.edit {
                                content = buildMessage(
                                    guild,
                                    roleChooserConfig,
                                    roleMapping
                                )
                            }

                            try {
                                roleMapping.forEach { (emoji, role) ->
                                    val reactors = message.getReactors(emoji)
                                    reactors.map { it.asMemberOrNull(guild.id) }
                                        .filterNotNull()
                                        .filter { member ->
                                            role.id !in member.roleIds
                                        }.collect { member ->
                                            logger.info { "adding '${role.name}' to '${member.displayName}'" }
                                            member.addRole(role.id)
                                        }
                                }
                            } catch (e: KtorRequestException) {
                                logger.errorF(e) { "failed to apply missing roles" }
                            }

                            startOnReaction(
                                guild,
                                message,
                                roleChooserConfig,
                                roleMapping
                            )
                        } catch (e: DiscordRelayedException) {
                            logger.errorF(e) { e.reason }
                        }
                    }
                }
            }
        }
    }

    private suspend fun add(
        guild: Guild,
        arguments: AddRoleArg,
        currentChannel: ChannelBehavior,
    ): String {
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }
        val roleChooserConfig = database.roleChooserQueries.find(guildId = guild.id,
            section = arguments.section,
            channel = channel.id).executeAsOneOrNull()
            ?: run {
                database.roleChooserQueries.upsert(
                    guildId = guild.id,
                    section = arguments.section,
                    description = null,
                    channel = channel.id,
                    message = channel.createMessage("placeholder").id
                )
                database.roleChooserQueries.find(guildId = guild.id,
                    section = arguments.section,
                    channel = channel.id).executeAsOneOrNull()
                    ?: relayError("failed to create database entry")
            }

        logger.infoF { "reaction: '${arguments.reaction}'" }
        val reactionEmoji = arguments.reaction

        val message = roleChooserConfig?.getMessageOrRelayError(guild)
            ?: channel.createMessage("placeholder for section ${arguments.section}")

        database.roleMappingQueries.upsert(
            roleChooserId = roleChooserConfig.roleChooserId,
            reaction = reactionEmoji.mention,
            role = arguments.role.id
        )

        val newRoleMapping = database.getRoleMapping(guild, roleChooser = roleChooserConfig)
        message.edit {
            content = buildMessage(
                guild,
                roleChooserConfig,
                newRoleMapping
            )
        }
        newRoleMapping.forEach { (reactionEmoji, _) ->
            message.addReaction(reactionEmoji)
        }
        liveMessageJobs[roleChooserConfig.roleChooserId]?.cancel()
        startOnReaction(
            guild,
            message,
            roleChooserConfig,
            newRoleMapping
        )

        return "added new role mapping ${reactionEmoji.mention} -> ${arguments.role.mention} to ${arguments.section} in ${channel.mention}"
    }

    private suspend fun remove(
        guild: Guild,
        arguments: RemoveRoleArg,
        currentChannel: ChannelBehavior,
    ): String {
        val kord = this@RoleManagementExtension.kord
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }

        val roleChooserConfig = database.roleChooserQueries.find(guildId = guild.id,
            section = arguments.section,
            channel = channel.id).executeAsOneOrNull()
            ?: relayError("no roleselection section ${arguments.section}")

        logger.infoF { "reaction: '${arguments.reaction}'" }
        val reactionEmoji = arguments.reaction

        val roleMapping = database.getRoleMapping(guild, roleChooserConfig)
        val removedRole = roleMapping[reactionEmoji] ?: relayError("no role exists for ${reactionEmoji.mention}")

        val message = roleChooserConfig.getMessageOrRelayError(guild)
            ?: channel.createMessage("placeholder for section ${arguments.section}")

        database.roleMappingQueries.delete(
            roleChooserId = roleChooserConfig.roleChooserId,
            reaction = reactionEmoji.mention
        )
        message.edit {
            content = buildMessage(
                guild,
                roleChooserConfig,
                database.getRoleMapping(guild, roleChooserConfig),
            )
        }
        message.getReactors(reactionEmoji)
            .filter { user ->
                user.id != kord.selfId
            }.map { user ->
                user.asMember(guild.id)
            }.filter { member ->
                removedRole.id in member.roleIds
            }.collect { member ->
                member.removeRole(removedRole.id)
            }
        message.asMessage().deleteReaction(reactionEmoji)
        return "removed role"
    }

    private suspend fun buildMessage(
        guildBehavior: GuildBehavior,
        roleChooserConfig: RoleChooserConfig,
        roleMapping: Map<ReactionEmoji, Role>,
    ) = "**${roleChooserConfig.section}**: \n" + roleMapping.entries.joinToString(
        "\n") { (reactionEmoji, role) ->
        "${reactionEmoji.mention} `${role.name}`"
    }

    @OptIn(KordPreview::class)
    private suspend fun startOnReaction(
        guildBehavior: GuildBehavior,
        message: MessageBehavior,
        rolePickerMessageState: RoleChooserConfig,
        roleMapping: Map<ReactionEmoji, Role>,
    ) {
        val job = Job()
        liveMessageJobs[rolePickerMessageState.roleChooserId] = job
        message.asMessage().live(
            CoroutineScope(
                job
                        + CoroutineName("live-message-${rolePickerMessageState.section}")
            )
        ) {
            onReactionAdd { event ->
                if (event.userId == kord.selfId) return@onReactionAdd
                val role = roleMapping[event.emoji] ?: return@onReactionAdd
                event.userAsMember?.addRole(role.id)
            }
            onReactionRemove { event ->
                if (event.userId == kord.selfId) return@onReactionRemove
                val role = roleMapping[event.emoji] ?: return@onReactionRemove
                event.userAsMember?.removeRole(role.id)
            }
        }
    }


}