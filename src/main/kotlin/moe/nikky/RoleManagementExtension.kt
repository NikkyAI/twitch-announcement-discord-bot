package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.*
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.ChannelType
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
        val section by string {
            name = "section"
            description = "Section Title"
        }
        val reaction by reactionEmoji {
            name = "emoji"
            description = "Reaction Emoji"
        }
        val role by role {
            name = "role"
            description = "Role"
        }
        val channel by optionalChannel {
            name = "channel"
            description = "channel"
            requireChannelType(ChannelType.GuildText)
        }
    }

    inner class ListRoleArg : Arguments() {
        val channel by optionalChannel {
            name = "channel"
            description = "channel"
            requireChannelType(ChannelType.GuildText)
        }
    }

    inner class RemoveRoleArg : Arguments() {
        val section by string {
            name = "section"
            description = "Section Title"
        }
        val reaction by reactionEmoji {
            name = "emoji"
            description = "Reaction Emoji"
        }
        val channel by optionalChannel {
            name = "channel"
            description = "channel"
            requireChannelType(ChannelType.GuildText)
        }
    }

    inner class RenameSectionArg : Arguments() {
        val oldSection by string {
            name = "old"
            description = "OLD Section Title"
        }
        val newSection by string {
            name = "section"
            description = "NEW Section Title"
        }
        val channel by optionalChannel {
            name = "channel"
            description = "channel"
            requireChannelType(ChannelType.GuildText)
        }
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

            ephemeralSubCommand(::ListRoleArg) {
                name = "list"
                description = "lists all "

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
                        val responseMessage = list(
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

            ephemeralSubCommand(::RenameSectionArg) {
                name = "update-section"
                description = "to fix a mistyped section name or such"

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
                        val responseMessage = renameSection(
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
                        logger.infoF { "processing role chooser: $roleChooserConfig" }
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
                                logger.infoF { "new message content: \n$content\n" }
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
        val roleChooserConfig = database.roleChooserQueries.find(
            guildId = guild.id,
            section = arguments.section,
            channel = channel.id
        ).executeAsOneOrNull()
            ?: run {
                database.roleChooserQueries.upsert(
                    guildId = guild.id,
                    section = arguments.section,
                    description = null,
                    channel = channel.id,
                    message = channel.createMessage("placeholder").id
                )
                database.roleChooserQueries.find(
                    guildId = guild.id,
                    section = arguments.section,
                    channel = channel.id
                ).executeAsOneOrNull()
                    ?: relayError("failed to create database entry")
            }

        logger.infoF { "reaction: '${arguments.reaction}'" }
        val reactionEmoji = arguments.reaction

        val message = roleChooserConfig.getMessageOrRelayError(guild)
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

    private suspend fun list(
        guild: Guild,
        arguments: ListRoleArg,
        currentChannel: ChannelBehavior,
    ): String {
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }

        val roleChoosers = database.roleChooserQueries.getAllFromChannel(
            guildId = guild.id,
            channel = channel.id,
        ).executeAsList()

        return roleChoosers.map { roleChooserConfig ->
            val message = roleChooserConfig.getMessageOrRelayError(guild)

            val newRoleMapping = database.getRoleMapping(guild, roleChooser = roleChooserConfig)

            val mappings = newRoleMapping.map { (k, v) ->
                "\n  ${k.mention} => ${v.mention}"
            }.joinToString("")

            listOf(
                "section: `${roleChooserConfig.section}`",
                "description: ${roleChooserConfig.description}",
                "mapping: $mappings",
                "message: ${message?.getJumpUrl()}",
            ).joinToString("\n")
        }.joinToString("\n\n")

//        return "added new role mapping ${reactionEmoji.mention} -> ${arguments.role.mention} to ${arguments.section} in ${channel.mention}"
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

        val roleChooserConfig = database.roleChooserQueries.find(
            guildId = guild.id,
            section = arguments.section,
            channel = channel.id
        ).executeAsOneOrNull()
            ?: relayError("no roleselection section ${arguments.section}")

        logger.infoF { "reaction: '${arguments.reaction}'" }
        val reactionEmoji = arguments.reaction

        val roleMapping = database.getRoleMapping(guild, roleChooserConfig)
        if (roleMapping.isEmpty()) {
            database.roleChooserQueries.delete(
                guildId = guild.id,
                section = arguments.section,
                channel = channel.id
            )
            val message = roleChooserConfig.getMessageOrRelayError(guild)
            message?.delete()

            return "removed role section"
        }
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

        val newRoleMapping = database.getRoleMapping(guild, roleChooserConfig)
        if (newRoleMapping.isEmpty()) {
            database.roleChooserQueries.delete(
                guildId = guild.id,
                section = arguments.section,
                channel = channel.id
            )
            message.delete()

            return "removed role section"
        }
        return "removed role"
    }

    private suspend fun renameSection(
        guild: Guild,
        arguments: RenameSectionArg,
        currentChannel: ChannelBehavior,
    ): String {
        val kord = this@RoleManagementExtension.kord
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }

        run {
            val shouldNotExist = database.roleChooserQueries.find(
                guildId = guild.id,
                section = arguments.newSection,
                channel = channel.id,
            ).executeAsOneOrNull()

            if (shouldNotExist != null) {
                relayError("section ${arguments.newSection} already exists")
            }
        }

        val roleChooserConfig = database.roleChooserQueries.find(
            guildId = guild.id,
            section = arguments.oldSection,
            channel = channel.id
        ).executeAsOneOrNull()
            ?: relayError("no roleselection section ${arguments.oldSection}")

        val message = roleChooserConfig.getMessageOrRelayError(guild)
            ?: channel.createMessage("placeholder for section ${arguments.newSection}")

        database.roleChooserQueries.updateSection(
            section = arguments.newSection,
            roleChooserId = roleChooserConfig.roleChooserId,
        )
        val newRoleChooserConfig = database.roleChooserQueries.find(
            guildId = guild.id,
            section = arguments.newSection,
            channel = channel.id
        ).executeAsOneOrNull()
            ?: relayError("no roleselection section ${arguments.newSection}")

        val newRoleMapping = database.getRoleMapping(
            guild,
            roleChooser = newRoleChooserConfig
        )

        message.edit {
            content = buildMessage(
                guild,
                newRoleChooserConfig,
                newRoleMapping,
            )
            logger.infoF { "new message content: \n$content\n" }
        }

        return "renamed section"
    }

    private suspend fun buildMessage(
        guildBehavior: GuildBehavior,
        roleChooserConfig: RoleChooserConfig,
        roleMapping: Map<ReactionEmoji, Role>,
    ) = "**${roleChooserConfig.section}** : \n" + roleMapping.entries
        .joinToString("\n") { (reactionEmoji, role) ->
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