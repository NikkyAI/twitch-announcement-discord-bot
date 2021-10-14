package moe.nikky

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.firstOrNull
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import dev.kord.core.live.onReactionRemove
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import moe.nikky.checks.hasBotControl
import mu.KotlinLogging
import org.koin.core.component.inject
import kotlin.time.ExperimentalTime

class RoleManagementExtension : Extension() {
    override val name: String = "Role management"
    private val config: ConfigurationService by inject()
    private val logger = KotlinLogging.logger {}


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

            ephemeralSubCommand(::AddRoleArg) {
                name = "add"
                description = "adds a new reaction to role mapping"

                check {
                    hasBotControl(config)
                }

                action {
                    val guild = guild ?: errorMessage("error looking up guild info")
                    val state = config[guild] ?: errorMessage("error fetching data")
                    val channel = event.interaction.channel

                    val oldRolePickerMessageState = state.roleChooser[arguments.section]

                    this@RoleManagementExtension.logger.info { "reaction: '${arguments.reaction}'" }
                    val reactionEmoji = guild.emojis.firstOrNull { it.data.id.asString in arguments.reaction }
                        ?.let { ReactionEmoji.from(it) }
                        ?: ReactionEmoji.Unicode(arguments.reaction)

                    this@RoleManagementExtension.logger.info { "reaction emoji: $reactionEmoji" }

                    val newRolePickerMessageState = RolePickerMessageState(
                        channel = channel as GuildMessageChannelBehavior,
                        message = oldRolePickerMessageState?.message ?: channel.createMessage("placeholder for section ${arguments.section}"),
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

                    newRolePickerMessageState.message.edit {
                        content = buildMessage(
                            arguments.section,
                            newRolePickerMessageState
                        )
                    }
                    newRolePickerMessageState.roleMapping.forEach { (reactionEmoji, _) ->
                        newRolePickerMessageState.message.addReaction(reactionEmoji)
                    }

                    oldRolePickerMessageState?.liveMessageJob?.cancel()
                    startOnReaction(
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

                action {
                    val guild = guild ?: errorMessage("error looking up guild info")
                    val state = config[guild] ?: errorMessage("error fetching data")
                    val channel = event.interaction.channel

                    val oldRolePickerMessageState = state.roleChooser[arguments.section] ?: errorMessage("no roleselection section ${arguments.section}")

                    val reactionEmoji = guild.emojis.firstOrNull { it.data.id.asString in arguments.reaction }
                        ?.let { ReactionEmoji.from(it) }
                        ?: ReactionEmoji.Unicode(arguments.reaction)

                    val newRolePickerMessageState = oldRolePickerMessageState.copy(
                        roleMapping = oldRolePickerMessageState.roleMapping - reactionEmoji,
                    )
                    newRolePickerMessageState.message.edit {
                        content = buildMessage(
                            arguments.section,
                            newRolePickerMessageState
                        )
                    }
                    oldRolePickerMessageState.message.asMessage().deleteReaction(reactionEmoji)
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
        }

        event<GuildCreateEvent> {
            action {
                val guild = event.guild
                var state: BotState? = null
                for (i in (0..100)) {
                    delay(100)
                    logger.info { "trying to load config" }
                    state = config[guild] ?: continue
                    logger.info { "loaded config" }
                    break
                }
                if (state == null) {
                    error("failed to load")
                }

                state.roleChooser.forEach { (section, rolePickerMessageState) ->
                    rolePickerMessageState.message.edit {
                        content = buildMessage(
                            section,
                            rolePickerMessageState
                        )
                    }
                    startOnReaction(section, rolePickerMessageState)
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
        section: String,
        rolePickerMessageState: RolePickerMessageState,
    ) {
//        rolePickerMessageState.liveMessageJob.cancel()
        rolePickerMessageState.message.asMessage().live(
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