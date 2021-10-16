package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.firstOrNull
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import dev.kord.core.live.onReactionRemove
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import moe.nikky.checks.hasBotControl
import mu.KotlinLogging
import org.koin.core.component.inject
import kotlin.time.ExperimentalTime

class RoleManagementExtension : Extension() {
    override val name: String = "Role management"
    private val config: ConfigurationService by inject()
    private val logger = KotlinLogging.logger {}

    companion object {
        private val requiredPermissions = arrayOf(
            Permission.ViewChannel,
            Permission.ManageMessages,
            Permission.SendMessages,
            Permission.AddReactions,
            Permission.ReadMessageHistory,
        )
    }

    inner class AddRoleArg : Arguments() {
        val section by string("section", "Section Title")
        val reaction by string("emoji", "Reaction Emoji")
        val role by role("role", "Role")
    }

    inner class RemoveRoleArg : Arguments() {
        val section by string("section", "Section Title")
        val reaction by string("emoji", "Reaction Emoji")
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "role"
            description = "Add or remove roles"
            requireBotPermissions(Permission.ManageRoles)

            ephemeralSubCommand({
                object: Arguments() {
                    val section by string("section", "Section Title")
                    val reaction by string("emoji", "Reaction Emoji")
                    val role by role("role", "Role")
                }
            }) {
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
                    val kord = this@RoleManagementExtension.kord
                    val guild = guild ?: relayError("error looking up guild info")
                    val state = config[guild]
                    val channel = event.interaction.channel.asChannel() as TextChannel

                    val oldRolePickerMessageState = state.roleChooser[arguments.section]

                    this@RoleManagementExtension.logger.info { "reaction: '${arguments.reaction}'" }
                    val reactionEmoji = guild.emojis.firstOrNull { it.data.id.asString in arguments.reaction }
                        ?.let { ReactionEmoji.from(it) }
                        ?: ReactionEmoji.Unicode(arguments.reaction)

                    this@RoleManagementExtension.logger.info { "reaction emoji: $reactionEmoji" }

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

                    oldRolePickerMessageState?.liveMessageJob?.cancel()
                    startOnReaction(
                        message,
                        arguments.section,
                        newRolePickerMessageState
                    )

                    respond {
                        content = "added new role"
                    }
                }
            }

            ephemeralSubCommand(::RemoveRoleArg) {
                name = "remove"
                description = "removes a role mapping"

                check {
                    hasBotControl(config)
                }

                requireBotPermissions(
                    *requiredPermissions
                )

                action {
                    val kord = this@RoleManagementExtension.kord
                    val guild = guild ?: relayError("error looking up guild info")
                    val state = config[guild]
                    val channel = event.interaction.channel

                    val oldRolePickerMessageState = state.roleChooser[arguments.section]
                        ?: relayError("no roleselection section ${arguments.section}")

                    val reactionEmoji = guild.emojis.firstOrNull { it.data.id.asString in arguments.reaction }
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

                    respond {
                        content = "removed role"
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
                    respond {
                        content = "OK"
                    }
                }
            }
        }

        event<GuildCreateEvent> {
            action {
                val guild = event.guild
                val state = config.loadConfig(event.guild) ?: run {
                    logger.error { "failed to load state for '${event.guild.name}'" }
                    return@action
                }

                val validChannels = state.roleChooser.map { it.value.channel }.distinct().filter {
                    val channel = it.asChannel()
                    val hasPermissions = channel.botHasPermissions(*requiredPermissions)
                    if (!hasPermissions) {
                        logger.error { "missing permissions in channel ${channel.name}" }
                    }
                    hasPermissions
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
                        startOnReaction(
                            message,
                            section,
                            rolePickerMessageState
                        )
                    } catch (e: DiscordRelayedException) {
                        logger.error(e) { e.reason }
                    }
                }

            }
        }
    }

    private fun buildMessage(
        section: String,
        newRolePickerMessageState: RolePickerMessageState,
    ) = "**${section}**: \n\n" + newRolePickerMessageState.roleMapping.entries.joinToString(
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