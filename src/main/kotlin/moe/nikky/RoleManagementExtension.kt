package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
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
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Guild
import dev.kord.core.entity.GuildEmoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.firstOrNull
import dev.kord.core.live.channel.live
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import dev.kord.core.live.onReactionRemove
import io.klogging.Klogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import moe.nikky.checks.hasBotControl
import org.koin.core.component.inject
import kotlin.time.ExperimentalTime

class RoleManagementExtension : Extension(), Klogging {
    override val name: String = "Role management"
    private val config: ConfigurationService by inject()

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
        val reaction by string("emoji", "Reaction Emoji, single emoji") { arguments, input ->
            val guild = getGuild()?.asGuildOrNull() ?: relayError("cannot load guild")
            val reactionEmoji = findEmoji(guild, input, this)
                ?.let { ReactionEmoji.from(it) }
                ?: ReactionEmoji.Unicode(input)
            if(reactionEmoji is ReactionEmoji.Unicode) {
                if(input.length > 1) {
                    relayError("invalid input, please only input a single emoji")
                }
            }
        }
        val role by role("role", "Role")
        val channel by optionalChannel("channel", "channel in which to add the role picker")
    }

    inner class RemoveRoleArg : Arguments() {
        val section by string("section", "Section Title")
        val reaction by string("emoji", "Reaction Emoji") { arguments, input ->
            val guild = getGuild()?.asGuildOrNull() ?: relayError("cannot load guild")
            val reactionEmoji = findEmoji(guild, input, this)
                ?.let { ReactionEmoji.from(it) }
                ?: ReactionEmoji.Unicode(input)
            if(reactionEmoji is ReactionEmoji.Unicode) {
                if(input.length > 1) {
                    relayError("invalid input, please only input a single emoji")
                }
            }
        }
        val channel by optionalChannel("channel", "channel in which to remove the role picker")
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
                    hasBotControl(config, event.getLocale())
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
                    hasBotControl(config, event.getLocale())
                    guildFor(event)?.asGuild()?.botHasPermissions(
                        Permission.ManageRoles
                    )
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
                    hasBotControl(config)
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
                    hasBotControl(config)
                    guildFor(event)?.asGuild()?.botHasPermissions(
                        Permission.ManageRoles
                    )
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
                    val state = config.loadConfig(event.guild) ?: run {
                        logger.fatalF { "failed to load state for '${event.guild.name}'" }
                        return@withLogContext
                    }

                    state.roleChooser.map { it.value.channel }.distinct().forEach {
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

                    state.roleChooser.forEach { (section, rolePickerMessageState) ->
//                    if(rolePickerMessageState.channel !in validChannels) return@forEach
                        try {
                            val message = rolePickerMessageState.getMessageOrRelayError()
                                ?: rolePickerMessageState.channel.createMessage("placeholder for section ${section}")
                            message.edit {
                                content = buildMessage(
                                    section,
                                    rolePickerMessageState
                                )
                            }
                            rolePickerMessageState.roleMapping.forEach { (emoji, role) ->
                                val reactors = message.getReactors(emoji)
                                reactors.map { it.asMemberOrNull(guild.id) }
                                    .filterNotNull()
                                    .filter { member ->
                                        role.id !in member.roleIds
                                    }.collect { member ->
                                        member.addRole(role.id)
                                    }
                            }
                            startOnReaction(
                                message,
                                section,
                                rolePickerMessageState
                            )
                        } catch (e: DiscordRelayedException) {
                            logger.errorF(e) { e.reason }
                        }
                    }
                }

            }
        }
    }

    private suspend fun CommandContext.add(guild: Guild, arguments: AddRoleArg, currentChannel: ChannelBehavior): String {
        val state = config[guild]
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }

        val oldRolePickerMessageState = state.roleChooser[arguments.section]

        logger.info { "reaction: '${arguments.reaction}'" }
        val reactionEmoji = findEmoji(guild, arguments.reaction, this)
            ?.let { ReactionEmoji.from(it) }
            ?: ReactionEmoji.Unicode(arguments.reaction)

        logger.info { "reaction emoji: $reactionEmoji" }

        val message = oldRolePickerMessageState?.getMessageOrRelayError()
            ?: channel.createMessage("placeholder for section ${arguments.section}")

        val newRolePickerMessageState = RolePickerMessageState(
            channel = channel as GuildMessageChannelBehavior,
            messageId = message.id,
            roleMapping = (oldRolePickerMessageState?.roleMapping
                ?: emptyMap()) + (reactionEmoji to arguments.role),
        )
        config[guild] = state.copy(
            roleChooser = state.roleChooser + Pair(
                arguments.section,
                newRolePickerMessageState,
            )
        )
        config.save()

        message.edit {
            content = buildMessage(
                arguments.section,
                newRolePickerMessageState
            )
        }
        newRolePickerMessageState.roleMapping.forEach { (reactionEmoji, _) ->
            message.addReaction(reactionEmoji)
        }

        oldRolePickerMessageState?.liveMessageJob.let { job ->
            startOnReaction(
                message,
                arguments.section,
                newRolePickerMessageState
            )
            job?.cancel()
        }

        return "added new role mapping ${reactionEmoji.mention} -> ${arguments.role.mention} to ${arguments.section} in ${channel.mention}"
    }


    private suspend fun CommandContext.remove(guild: Guild, arguments: RemoveRoleArg, currentChannel: ChannelBehavior): String {
        val kord = this@RoleManagementExtension.kord
        val state = config[guild]
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }

        val oldRolePickerMessageState = state.roleChooser[arguments.section]
            ?: relayError("no roleselection section ${arguments.section}")

        val reactionEmoji = findEmoji(guild, arguments.reaction, this)
            ?.let { ReactionEmoji.from(it) }
            ?: ReactionEmoji.Unicode(arguments.reaction)

        val removedRole = oldRolePickerMessageState.roleMapping[reactionEmoji] ?: relayError(
            "no role exists for ${reactionEmoji.mention}"
        )

        val message = oldRolePickerMessageState.getMessageOrRelayError()
            ?: channel.createMessage("placeholder for section ${arguments.section}")

        val newRolePickerMessageState = oldRolePickerMessageState.copy(
            roleMapping = oldRolePickerMessageState.roleMapping - reactionEmoji,
        )
        message.edit {
            content = buildMessage(
                arguments.section,
                newRolePickerMessageState
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
        config[guild] = state.copy(
            roleChooser = state.roleChooser + Pair(
                arguments.section,
                newRolePickerMessageState,
            )
        )
        config.save()

        return "removed role"
    }

    private suspend fun findEmoji(guild: Guild, arg: String, context: CommandContext): GuildEmoji? =
        if (arg.startsWith("<a:") || arg.startsWith("<:") && arg.endsWith('>')) { // Emoji mention
            val id: String = arg.substring(0, arg.length - 1).split(":").last()

            try {
                guild.getEmojiOrNull(Snowflake(name))
            } catch (e: NumberFormatException) {
                throw DiscordRelayedException(
                    context.translate("converters.emoji.error.invalid", replacements = arrayOf(id))
                )
            }
        } else { // ID or name
            val name = if (arg.startsWith(":") && arg.endsWith(":")) arg.substring(1, arg.length - 1) else arg

            try {
                guild.getEmojiOrNull(Snowflake(name))
            } catch (e: NumberFormatException) {  // Not an ID, let's check names
                guild.emojis.firstOrNull { emojiObj -> emojiObj.name?.lowercase().equals(name, true) }
            }
        }

    private fun buildMessage(
        section: String,
        newRolePickerMessageState: RolePickerMessageState,
    ) = "**${section}**: \n" + newRolePickerMessageState.roleMapping.entries.joinToString(
        "\n") { (reactionEmoji, role) ->
        "${reactionEmoji.mention} `${role.name}`"
    }

    @OptIn(KordPreview::class)
    private suspend fun startOnReaction(
        message: MessageBehavior,
        section: String,
        rolePickerMessageState: RolePickerMessageState,
    ) {
        message.asMessage().live(
            CoroutineScope(
                rolePickerMessageState.liveMessageJob
                        + CoroutineName("live-message-${section}")
            )
        ) {
            onReactionAdd { event ->
                if (event.userId == kord.selfId) return@onReactionAdd
                val role = rolePickerMessageState.roleMapping[event.emoji] ?: return@onReactionAdd
                event.userAsMember?.addRole(role.id)
            }
            onReactionRemove { event ->
                if (event.userId == kord.selfId) return@onReactionRemove
                val role = rolePickerMessageState.roleMapping[event.emoji] ?: return@onReactionRemove
                event.userAsMember?.removeRole(role.id)
            }
        }
    }


}