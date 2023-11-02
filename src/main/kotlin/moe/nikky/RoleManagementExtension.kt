package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.ColorConverter
import com.kotlindiscord.kord.extensions.commands.converters.impl.boolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.color
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalColor
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.parsers.ColorParser
import com.kotlindiscord.kord.extensions.storage.Data
import com.kotlindiscord.kord.extensions.storage.StorageType
import com.kotlindiscord.kord.extensions.storage.StorageUnit
import com.kotlindiscord.kord.extensions.utils.any
import com.kotlindiscord.kord.extensions.utils.botHasPermissions
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import com.kotlindiscord.kord.extensions.utils.translate
import dev.kord.common.Color
import dev.kord.common.annotation.KordPreview
import dev.kord.common.asJavaLocale
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.editMemberPermission
import dev.kord.core.behavior.channel.editRolePermission
import dev.kord.core.behavior.createRole
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.live.live
import dev.kord.core.live.onReactionAdd
import dev.kord.core.live.onReactionRemove
import dev.kord.rest.request.KtorRequestException
import io.github.xn32.json5k.Json5
import io.klogging.Klogging
import io.klogging.context.logContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import moe.nikky.converter.reactionEmoji
import org.koin.core.component.inject
import org.koin.dsl.module
import java.lang.Exception
import java.util.*

class RoleManagementExtension : Extension(), Klogging {
    override val name: String = "role-management"
    private val config: ConfigurationExtension by inject()
    private val liveMessageJobs = mutableMapOf<String, Job>()

    private val guildConfig = StorageUnit(
        storageType = StorageType.Config,
        namespace = name,
        identifier = "role-management",
        dataType = RoleManagementConfig::class
    )

    private fun GuildBehavior.config() =
        guildConfig
            .withGuild(id)

    init {
        bot.getKoin().loadModules(
            listOf(
                module {
                    single { this@RoleManagementExtension }
                }
            )
        )
    }

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

    inner class CreateRoleArg : Arguments() {
        val name by string {
            name = "name"
            description = "role name"
        }
        val color by optionalColor {
            name = "color"
            description = "role color"
        }
        val mentionable by defaultingBoolean {
            name = "mentionable"
            description = "pingable"
            defaultValue = false
        }
    }

    inner class ImportRoleArg : Arguments() {
        val data by string {
            name = "data"
            description = "json5 encoded role data"
        }
    }

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "reactionRole"
            description = "manage reaction roles"
            allowInDms = false
            requireBotPermissions(Permission.ManageRoles)

            ephemeralSubCommand(::AddRoleArg) {
                name = "add"
                description = "adds a new reaction to role mapping"

                check {
                    with(config) { requiresBotControl() }
                }

                requireBotPermissions(
                    *requiredPermissions,
                    Permission.ManageRoles
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
                description = "lists all configured reaction roles"

                check {
                    with(config) { requiresBotControl() }
                }

                requireBotPermissions(
                    *requiredPermissions,
                    Permission.ManageRoles
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
                    with(config) { requiresBotControl() }
                }

                requireBotPermissions(
                    *requiredPermissions,
                    Permission.ManageRoles
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
                    with(config) { requiresBotControl() }
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
                    *requiredPermissions,
                    Permission.ManageRoles
                )
                check {
                    with(config) { requiresBotControl() }
                }

                action {
                    withLogContext(event, guild) { guild ->
                        respond {

//                            val globalPerms = guild.getMember(this@RoleManagementExtension.kord.selfId).getPermissions()
//
//                            val perms = channel
//                                .asChannelOf<GuildChannel>()
//                                .permissionsForMember(this@RoleManagementExtension.kord.selfId)
//
//                            val globalPermsString = globalPerms.values
//                                .joinToString("\n")
//                            val permsString = perms.values
//                                .joinToString("\n")
//
//
//                            val textChannel = channel
//                                .asChannelOf<TextChannel>()


//                            val permsOverrides = channel
//                                .asChannelOf<GuildChannel>()
//                                .data.permissionOverwrites
//                                .value
//                                ?.map { overwrite ->
//                                    val target = when(overwrite.type) {
//                                        OverwriteType.Role -> guild.getRoleOrNull(overwrite.id)?.mention
//                                        OverwriteType.Member -> guild.getMemberOrNull(overwrite.id)?.mention
//                                        else -> null
//                                    } ?: "`${overwrite.id}` (`${overwrite.type}`)"
//                                    val allows = overwrite.allow.values.joinToString("\n") { "+ $it ${it.shift}" }
//                                    val denies = overwrite.deny.values.joinToString("\n") { "- $it ${it.shift}" }
//
//                                    """
//                                        |$target `$target`
//                                        |```diff
//                                        |$allows
//                                        |$denies
//                                        |```
//                                    """.trimMargin()
//                                }?.joinToString("\n")

//                            content = """
//                                $permsString
//
//                                $globalPermsString
//
//                                $permsOverrides
//                                """.trimIndent()
//                            content = permsOverrides
                            content = "OK"
                        }
                    }
                }
            }
        }

        ephemeralSlashCommand {
            name = "role"
            description = "create a new role"
            allowInDms = false
            requireBotPermissions(Permission.ManageRoles)

            ephemeralSubCommand(::CreateRoleArg) {
                name = "create"
                description = "creates a new role"

                requireBotPermissions(
                    Permission.ManageRoles,
                )
                check {
                    with(config) { requiresBotControl() }
                }

                action {
                    withLogContext(event, guild) { guild ->
                        if(guild.roles.any { it.name == arguments.name }) {
                            relayError("a role with that name already exists")
                        }
                        val role = guild.createRole {
                            name = arguments.name
                            color = arguments.color
                            mentionable = arguments.mentionable
                            hoist = false
                        }

                        respond {
                            content = "created ${role.mention}"
                        }
                    }
                }
            }


            ephemeralSubCommand(::ImportRoleArg) {
                name = "import"
                description = "creates many roles"

                requireBotPermissions(
                    Permission.ManageRoles,
                )
                check {
                    with(config) { requiresBotControl() }
                }

                action {
                    withLogContext(event, guild) { guild ->
                        val json5 = Json5 {

                        }
                        val data = json5.decodeFromString(MapSerializer(String.serializer(), String.serializer()), arguments.data)
                        val duplicates = guild.roles.filter { it.name in data.keys }.toList()
                        if(duplicates.isNotEmpty()) {
                            relayError(
                                "roles with the following roles already exist: ${duplicates.joinToString(" ") { it.mention }}"
                            )
                        }
                        val roles = data.map { (roleName, roleColor) ->
                            val parsedColor = parseColor(roleColor, this@action)
                            guild.createRole {
                                name = roleName
                                color = parsedColor
                                mentionable = false
                                hoist = false
                            }
                        }

                        respond {
                            content = "created ${roles.joinToString(" ") { it.mention }}"
                        }
                    }
                }
            }
        }

        event<GuildCreateEvent> {
            action {
                withLogContext(event, event.guild) { guild ->
                    migrateConfig(guild)

//                    val textChannels = guild.channels.filter { it is TextChannel }

                    val config = guild.config().get() ?: return@withLogContext

                    val roleChoosers =
                        config.roleChoosers // database.roleChooserQueries.getAll(guildId = guild.id).executeAsList()

                    try {
                        roleChoosers
                            .values
                            .map { it.channel(guild) }
                            .distinctBy { it.id }
                            .forEach { channel ->
                                val missingPermissions = requiredPermissions.filterNot { permission ->
                                    channel.botHasPermissions(permission)
                                }

                                if (missingPermissions.isNotEmpty()) {
                                    val locale: Locale = guild.preferredLocale.asJavaLocale()
                                    logger.errorF {
                                        "missing permissions in ${guild.name} #${channel.name} ${
                                            missingPermissions.joinToString(", ") { it.translate(locale) }
                                        }"
                                    }
                                }
                            }

                    } catch (e: DiscordRelayedException) {
                        logger.errorF(e) { e.reason }
                        return@withLogContext
                    }

                    roleChoosers.forEach { (key, roleChooserConfig) ->
                        logger.infoF { "processing role chooser: $roleChooserConfig" }
//                    if(rolePickerMessageState.channel !in validChannels) return@forEach
                        try {
                            val message = getOrCreateMessage(key, roleChooserConfig, guild)
                            val roleMapping = roleChooserConfig.roleMapping
                            message.edit {
                                content = buildMessage(
                                    guild,
                                    roleChooserConfig,
                                    roleMapping,
                                    message.flags,
                                )
                                logger.infoF { "new message content: \n$content\n" }
                            }

                            val allReactionEmojis = message.reactions.map { it.emoji }.toSet()
                            val emojisToRemove = allReactionEmojis - roleMapping.map { it.reactionEmoji(guild) }.toSet()
                            emojisToRemove.forEach { reactionEmoji ->
                                message.deleteReaction(reactionEmoji)
                            }
                            try {
                                roleMapping.forEach { entry ->
                                    logger.traceF { "parse emoji" }
                                    val reactionEmoji: ReactionEmoji = entry.reactionEmoji(guild)
                                    logger.traceF { "get role ${entry.role}" }
                                    val role = entry.getRole(guild)
                                    logger.traceF { "adding reaction $reactionEmoji for role ${role.name}" }
                                    message.addReaction(reactionEmoji)
                                    val reactors = message.getReactors(reactionEmoji)
                                    reactors.map { it.asMemberOrNull(guild.id) }
                                        .filterNotNull()
                                        .filter { it.id != kord.selfId }
                                        .filter { member ->
                                            role.id !in member.roleIds
                                        }.collect { member ->
                                            logger.infoF { "adding '${role.name}' to '${member.effectiveName}'" }
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

        val configUnit = guild.config()
        val (key, roleChooserConfig) = configUnit.get()?.find(arguments.section, channel.id)
            ?: run {
                RoleChooserConfig(
                    section = arguments.section,
                    channelId = channel.id,
                    messageId = channel.createMessage {
                        content = "placeholder"
                        suppressNotifications = true
                    }.id,
                    roleMapping = emptyList()
                ).let {
                    it.key(channel) to it
                }.also {
                    configUnit.save(
                        configUnit.get()?.update(it.first, it.second) ?: relayError("failed to save config")
                    )
                }
            }

        logger.infoF { "reaction: '${arguments.reaction}'" }

        val message = getOrCreateMessage(key, roleChooserConfig, guild)

        configUnit.save(
            configUnit.get()?.updateRoleMapping(key, arguments.reaction, arguments.role)
                ?: relayError("failed to save config")
        )

        val (_, newRoleChooserConfig) = configUnit.get()?.find(arguments.section, channel.id)
            ?: relayError("could not find role chooser section")

        val newRoleMapping = newRoleChooserConfig.roleMapping
        message.edit {
            content = buildMessage(
                guild,
                newRoleChooserConfig,
                newRoleMapping,
                message.flags,
            )
        }
        newRoleMapping.forEach { entry ->
            val reactionEmoji = entry.reactionEmoji(guild)
            message.addReaction(reactionEmoji)
        }
        liveMessageJobs[roleChooserConfig.key(channel)]?.cancel()
        startOnReaction(
            guild,
            message,
            roleChooserConfig,
            newRoleMapping
        )

        return "added new role mapping ${arguments.reaction.mention} -> ${arguments.role.mention} to `${arguments.section}` in ${channel.mention}"
    }

    private suspend fun list(
        guild: Guild,
        arguments: ListRoleArg,
        currentChannel: ChannelBehavior,
    ): String {
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }

        val configUnit = guild.config()

        val roleChoosers = configUnit.get()?.list(channelId = channel.id)
            ?: relayError("failed to load config")

        return roleChoosers.map { roleChooserConfig ->
            val message = roleChooserConfig.getMessageOrRelayError(guild)


            val newRoleMapping = roleChooserConfig.roleMapping

            val mappings = newRoleMapping.map { entry ->
                val reactionEmoji = entry.reactionEmoji(guild)
                val role = entry.getRole(guild)
                "\n  ${reactionEmoji.mention} => ${role.mention}"
            }.joinToString("")

            listOf(
                "section: `${roleChooserConfig.section}`",
                "mapping: $mappings",
                "message: ${message?.getJumpUrl()}",
            ).joinToString("\n")
        }.joinToString("\n\n")
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

        val configUnit = guild.config()

        val (key, roleChooserConfig) = configUnit.get()?.find(arguments.section, channel.id)
            ?: relayError("no role selection section ${arguments.section}")

        logger.infoF { "reaction: '${arguments.reaction}'" }
        val reactionEmoji = arguments.reaction

        val roleMapping = roleChooserConfig.roleMapping // database.getRoleMapping(guild, roleChooserConfig)
        if (roleMapping.isEmpty()) {
            configUnit.save(
                configUnit.get()?.delete(key) ?: relayError("failed to save config")
            )
            val message = roleChooserConfig.getMessageOrRelayError(guild)
            message?.delete()

            return "removed role section"
        }
        val removedRoleId =
            roleMapping.getByEmoji(reactionEmoji) ?: relayError("no role exists for ${reactionEmoji.mention}")

        val message = getOrCreateMessage(key, roleChooserConfig, guild)

        val newConfig = configUnit.save(
            configUnit.get()?.deleteRoleMapping(key, reactionEmoji) ?: relayError("failed to save config")
        )
        val (newKey, newRoleChooser) = newConfig.find(arguments.section, channel.id)
            ?: relayError("no role selection section ${arguments.section}")
        message.edit {
            content = buildMessage(
                guild,
                newRoleChooser,
                newRoleChooser.roleMapping,
                message.flags,
            )
        }
        message.getReactors(reactionEmoji)
            .filter { user ->
                user.id != kord.selfId
            }.map { user ->
                user.asMember(guild.id)
            }.filter { member ->
                removedRoleId in member.roleIds
            }.collect { member ->
                member.removeRole(removedRoleId)
            }
        message.asMessage().deleteReaction(reactionEmoji)

        val newRoleMapping = configUnit.get()!!.roleChoosers[key]!!.roleMapping
        if (newRoleMapping.isEmpty()) {
            configUnit.save(
                configUnit.get()!!.delete(key)
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
        val channel = (arguments.channel ?: currentChannel).asChannel().let { channel ->
            channel as? TextChannel ?: relayError("${channel.mention} is not a Text Channel")
        }

        val configUnit = guild.config()
        val roleManagementConfig = configUnit.get() ?: RoleManagementConfig()

        run {
            val shouldNotExist = roleManagementConfig.roleChoosers.entries.firstOrNull { (_, entry) ->
                entry.section == arguments.newSection && entry.channelId == channel.id
            }

            if (shouldNotExist != null) {
                relayError("section ${arguments.newSection} already exists")
            }
        }

        val (key, roleChooserConfig) = configUnit.get()!!.find(
            section = arguments.newSection,
            channelId = channel.id
        ) ?: relayError("no roleselection section ${arguments.oldSection}")

        val message = getOrCreateMessage(key, roleChooserConfig, guild)

        configUnit.save(
            configUnit.get()?.updateSection(
                key = key,
                section = arguments.newSection,
            ) ?: relayError("failed to update config")
        )

        val (_, newRoleChooserConfig) = configUnit.get()!!.find(
            section = arguments.newSection,
            channelId = channel.id
        ) ?: relayError("no roleselection section ${arguments.newSection}")

        val newRoleMapping = newRoleChooserConfig.roleMapping

        message.edit {
            content = buildMessage(
                guild,
                newRoleChooserConfig,
                newRoleMapping,
                message.flags,
            )
            logger.infoF { "new message content: \n$content\n" }
        }

        return "renamed section"
    }

    private suspend fun getOrCreateMessage(
        key: String,
        roleChooserConfig: RoleChooserConfig,
        guild: Guild,
        sectionName: String = roleChooserConfig.section,
    ): Message {
        val message = roleChooserConfig
            .getMessageOrRelayError(guild)
            ?: run {
                logger.debugF { "creating new message" }
                roleChooserConfig.channel(guild)
                    .createMessage {
                        content = "placeholder for section $sectionName"
                        suppressNotifications = true
                    }
                    .also {
                        val config = guild.config()

                        config.save(
                            config.get()?.updateMessage(key, it.id) ?: relayError("failed to save config")
                        )
                    }
            }
        return message
    }

    private suspend fun buildMessage(
        guild: Guild,
        roleChooserConfig: RoleChooserConfig,
        roleMapping: List<RoleMappingConfig>,
        flags: MessageFlags?,
    ): String {
        return if (flags?.contains(MessageFlag.SuppressNotifications) == true) {
            "**${roleChooserConfig.section}** : \n" + roleMapping
                .map { entry ->
                    val role = guild.getRole(entry.role)
                    entry to role
                }
                .sortedByDescending { (_, role) ->
                    role.rawPosition
                }
                .map {(entry, role) ->
                    val emoji = entry.reactionEmoji(guild)
                    "${emoji.mention} ${role.mention}"
                }
                .joinToString("\n")
        } else {
            "**${roleChooserConfig.section}** : \n" + roleMapping
                .map { entry ->
                    val role = guild.getRole(entry.role)
                    entry to role
                }
                .sortedByDescending { (_, role) ->
                    role.rawPosition
                }
                .map {(entry, role) ->
                    val emoji = entry.reactionEmoji(guild)
                    "${emoji.mention} `${role.name}`"
                }
                .joinToString("\n")
        }
    }

    @OptIn(KordPreview::class)
    private suspend fun startOnReaction(
        guildBehavior: GuildBehavior,
        message: MessageBehavior,
        rolePickerMessageState: RoleChooserConfig,
        roleMapping: List<RoleMappingConfig>,
    ) {
        val job = Job()
        val channel = message.channel.asChannelOf<TextChannel>()
        liveMessageJobs[rolePickerMessageState.key(channel)] = job
        message.asMessage().live(
            CoroutineScope(
                job
                        + CoroutineName("live-message-${rolePickerMessageState.section}")
            )
        ) {
            onReactionAdd { event ->
                if (event.userId == kord.selfId) return@onReactionAdd
                val role = roleMapping.getByEmoji(event.emoji) ?: return@onReactionAdd
                event.userAsMember?.addRole(role)
            }
            onReactionRemove { event ->
                if (event.userId == kord.selfId) return@onReactionRemove
                val role = roleMapping.getByEmoji(event.emoji) ?: return@onReactionRemove
                event.userAsMember?.removeRole(role)
            }
        }
    }

    suspend fun loadConfig(guild: GuildBehavior): RoleManagementConfig? {
        return guild.config().get()
    }

    suspend fun migrateConfig(guild: GuildBehavior) {
        try {
            val oldConfig = StorageUnit(
                storageType = StorageType.Config,
                namespace = name,
                identifier = "role-management",
                dataType = RoleManagementConfigOld::class
            ).withGuild(guild).get() ?: return

            val newData = RoleManagementConfig(
                roleChoosers = oldConfig.roleChoosers.mapValues { (_, roleChooserConfig) ->
                    RoleChooserConfig(
                        section = roleChooserConfig.section,
                        channelId = roleChooserConfig.channelId,
                        messageId = roleChooserConfig.messageId,
                        roleMapping = roleChooserConfig.roleMapping.map { mapping ->
                            val emoji =
                                guild.emojis.firstOrNull { it.mention == mapping.reaction }
                            if(emoji != null) {
                                RoleMappingConfig(
                                    emoji = emoji.id.toString(),
                                    emojiName = emoji.name,
                                    role = mapping.role,
                                    roleName = mapping.roleName
                                )
                            } else {
                                RoleMappingConfig(
                                    emoji = mapping.reaction,
                                    emojiName = mapping.reaction,
                                    role = mapping.role,
                                    roleName = mapping.roleName
                                )
                            }
                        }
                    )
                }
            )
            guild.config().save(newData)
            return
        } catch (e: Exception) {

        }

        try {
            val oldConfig = StorageUnit(
                storageType = StorageType.Config,
                namespace = name,
                identifier = "role-management",
                dataType = RoleManagementConfig::class
            ).withGuild(guild).get() ?: return

            val newData = RoleManagementConfig(
                roleChoosers = oldConfig.roleChoosers.mapValues { (_, roleChooserConfig) ->
                    roleChooserConfig.copy(
                        roleMapping = roleChooserConfig.roleMapping.map { mapping ->

                            val emoji = if(mapping.emoji.startsWith("<") && mapping.emoji.endsWith(">")) {
                                val id = mapping.emoji.substringAfterLast(":").substringBefore(">")
                                guild.emojis.firstOrNull { it.id.toString() == id }
                            } else {
                                guild.emojis.firstOrNull { it.name == mapping.emoji || it.id.toString() == mapping.emoji }
                            }
                            if(emoji != null) {
                                mapping.copy(
                                    emojiName = emoji.name,
                                    emoji = emoji.id.toString(),
                                )
                            } else {
                                mapping.copy(
                                    emojiName = mapping.emoji
                                )
                            }
                        }
                    )
                }
            )
            guild.config().save(newData)
            return
        } catch (e: Exception) {

        }

    }
}

@Serializable
data class RoleManagementConfigOld(
    val roleChoosers: Map<String, RoleChooserConfigOld> = emptyMap(),
) : Data
{

}

@Serializable
data class RoleManagementConfig(
    val roleChoosers: Map<String, RoleChooserConfig> = emptyMap(),
) : Data {
    fun update(key: String, newValue: RoleChooserConfig): RoleManagementConfig {
        return copy(roleChoosers = roleChoosers + (key to newValue))
    }
    fun updateMessage(key: String, messageId: Snowflake): RoleManagementConfig? {
        val newValue = roleChoosers[key]?.copy(
            messageId = messageId
        ) ?: run {
            return null
        }

        return copy(roleChoosers = roleChoosers + (key to newValue))
    }
    fun updateSection(key: String, section: String): RoleManagementConfig? {
        val newValue = roleChoosers[key]?.copy(
            section = section
        ) ?: run {
            return null
        }

        return copy(roleChoosers = roleChoosers + (key to newValue))
    }

    fun find(section: String, channelId: Snowflake): Pair<String, RoleChooserConfig>? {
        return roleChoosers.entries.firstOrNull { (key, value) ->
            value.section == section && value.channelId == channelId
        }?.let {
            (key, value) -> key to value
        }
    }

    fun delete(key: String): RoleManagementConfig {
        return copy(roleChoosers = roleChoosers - key)
    }

    fun deleteRoleMapping(key: String, emoji: ReactionEmoji): RoleManagementConfig? {
        val oldRoleChooser = roleChoosers[key]?: run {
            return null
        }

        val newValue = oldRoleChooser.copy(
            roleMapping = oldRoleChooser.roleMapping.filter { it.emoji != emoji.name }
        )

        return copy(roleChoosers = roleChoosers + (key to newValue))
    }
    fun updateRoleMapping(key: String, emoji: ReactionEmoji, role: Role): RoleManagementConfig? {
        val oldRoleChooser = deleteRoleMapping(key, emoji)?.roleChoosers?.get(key) ?: run {
            return null
        }

        val newValue = oldRoleChooser.copy(
            roleMapping = oldRoleChooser.roleMapping + RoleMappingConfig(emoji.idOrUnicode(), emoji.name, role.id, role.name)
        )

        return copy(roleChoosers = roleChoosers + (key to newValue))
    }

    fun list(channelId: Snowflake): List<RoleChooserConfig> {
        return roleChoosers.values.filter { it.channelId == channelId }
    }
}

private suspend fun parseColor(input: String, context: CommandContext): Color? {
    return when {
        input.startsWith("#") -> Color(input.substring(1).toInt(16))
        input.startsWith("0x") -> Color(input.substring(2).toInt(16))
        input.all { it.isDigit() } -> Color(input.toInt())

        else -> ColorParser.parse(input, context.getLocale()) ?: throw DiscordRelayedException(
            context.translate("converters.color.error.unknown", replacements = arrayOf(input))
        )
    }
}

private fun ReactionEmoji.idOrUnicode(): String {
    return when(this) {
        is ReactionEmoji.Custom -> id.toString()
        is ReactionEmoji.Unicode -> name
    }
}


private fun List<RoleMappingConfig>.getByEmoji(emoji: ReactionEmoji): Snowflake? =
    firstOrNull { it.emoji == emoji.idOrUnicode() }?.role

private fun List<Pair<ReactionEmoji, Role>>.getByEmoji(emoji: ReactionEmoji): Role? =
    firstOrNull { it.first == emoji }?.second