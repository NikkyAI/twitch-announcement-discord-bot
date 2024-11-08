package moe.nikky.twitch

import dev.kord.common.Color
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.GuildScheduledEventEntityMetadata
import dev.kord.common.entity.GuildScheduledEventPrivacyLevel
import dev.kord.common.entity.GuildScheduledEventStatus
import dev.kord.common.entity.Permission
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.ScheduledEntityType
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.optional
import dev.kord.common.exception.RequestException
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.TopGuildMessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createWebhook
import dev.kord.core.behavior.createScheduledEvent
import dev.kord.core.behavior.execute
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.Webhook
import dev.kord.core.entity.channel.NewsChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.rest.Image
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.request.KtorRequestException
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.commands.converters.impl.defaultingInt
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.storage.StorageType
import dev.kordex.core.storage.StorageUnit
import dev.kordex.core.time.TimestampType
import dev.kordex.core.utils.getJumpUrl
import dev.kordex.core.utils.isPublished
import dev.kordex.core.utils.scheduling.Scheduler
import dev.kordex.core.utils.scheduling.Task
import io.klogging.Klogging
import io.klogging.context.logContext
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import moe.nikky.ConfigurationExtension
import moe.nikky.debugF
import moe.nikky.errorF
import moe.nikky.infoF
import moe.nikky.linesChunkedByMaxLength
import moe.nikky.location
import moe.nikky.relayError
import moe.nikky.traceF
import moe.nikky.twitch.TwitchApi.getChannelInfo
import moe.nikky.twitch.TwitchApi.getGames
import moe.nikky.twitch.TwitchApi.getLastVOD
import moe.nikky.twitch.TwitchApi.getSchedule
import moe.nikky.twitch.TwitchApi.getStreams
import moe.nikky.twitch.TwitchApi.getToken
import moe.nikky.twitch.TwitchApi.getUsers
import moe.nikky.warnF
import moe.nikky.withLogContext
import org.koin.core.component.inject
import org.koin.dsl.module
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class TwitchExtension() : Extension(), Klogging {
    override val name = "twitch-notifications"
    val color = Color(0x6441a5)

    private val guildConfig = StorageUnit(
        storageType = StorageType.Config,
        namespace = name,
        identifier = "twitch",
        dataType = TwitchGuildConfig::class
    )

    private fun GuildBehavior.config() =
        guildConfig
            .withGuild(id)

    init {
        bot.getKoin().loadModules(
            listOf(
                module {
                    single { this@TwitchExtension }
                }
            )
        )
    }

    private val configurationExtension: ConfigurationExtension by inject()

    //    private var token: Token? = null
//    private var tokenExpiration: Instant = Instant.DISTANT_PAST
    private val webhooksCache = mutableMapOf<Snowflake, Webhook>()

    companion object {
        private const val WEBHOOK_NAME_PREFIX = "twitch-notifications"
//        private const val twitchApi = "https://api.twitch.tv/helix"
//        private val clientId = envOrNull("TWITCH_CLIENT_ID")
//        private val clientSecret = envOrNull("TWITCH_CLIENT_SECRET")

        private val requiredPermissions = arrayOf(
            Permission.ViewChannel,
            Permission.ManageWebhooks,
            Permission.SendMessages,
            Permission.ManageMessages,
            Permission.ReadMessageHistory,
            Permission.ManageEvents,
        )
    }

//    private val json = Json {
//        prettyPrint = true
//        ignoreUnknownKeys = true
//    }
//    private val httpClient = kord.resources.httpClient

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            config {
                readTimeout(15.seconds.toJavaDuration())
                connectTimeout(5.seconds.toJavaDuration())
                retryOnConnectionFailure(true)
            }
//            requestTimeout = 15_000
//            endpoint {
//                connectTimeout = 5_000
//                connectAttempts = 5
//            }
        }
//        install(Logging) {
//            logger = Logger.DEFAULT
//            level = LogLevel.ALL
//        }
    }

    private val scheduler = Scheduler()
    private val task: Task = runBlocking {
        scheduler.schedule(
            delay = 15.seconds,
            startNow = true,
            pollingSeconds = 1,
            name = "Twitch Loop",
            repeat = true,
        ) {
            try {
                withContext(
                    logContext(
                        "event" to "TwitchLoop"
                    )
                ) {
                    val guilds = kord.guilds.toList()

                    logger.traceF { "checking streams" }
                    val token = httpClient.getToken()
                    checkStreams(
                        guilds = guilds,
                        token = token
                    )
                }
            } catch (e: Exception) {
                logger.errorF(e) { "failed in twitch loop" }
            } finally {
                delay(15.seconds)
            }
        }
    }

    inner class TwitchAddArgs : Arguments() {
        val role by role {
            name = "role".toKey()
            description = "notification ping".toKey()
        }
        val twitchUserName by string {
            name = "twitch".toKey()
            description = "Twitch username".toKey()
        }
        val channel by optionalChannel {
            name = "channel".toKey()
            description = "notification channel, defaults to current channel".toKey()
            requireChannelType(ChannelType.GuildText)
        }
    }

    inner class TwitchRemoveArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch".toKey()
            description = "Twitch username".toKey()
        }
        val channel by optionalChannel {
            name = "channel".toKey()
            description = "notification channel, defaults to current channel".toKey()
            requireChannelType(ChannelType.GuildText)
        }
    }

    inner class TwitchScheduleSyncArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch".toKey()
            description = "Twitch username".toKey()
        }
        val amount by defaultingInt {
            val validRange = 1..100
            name = "max".toKey()
            defaultValue = 30
            description =
                "amount of schedule entries to list, value between ${validRange.first} and ${validRange.last}, default: $defaultValue".toKey()
            validate {
                failIfNot("$value is not in range $validRange") { value in validRange }
            }
        }
    }

    inner class TwitchScheduleDeleteArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch".toKey()
            description = "Twitch username".toKey()
        }
    }

    inner class TwitchScheduleListArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch".toKey()
            description = "Twitch username".toKey()
        }
        val amount by defaultingInt {
            val validRange = 1..100
            name = "max".toKey()
            defaultValue = 30
            description =
                "amount of schedule entries to list, value between ${validRange.first} and ${validRange.last}, default: $defaultValue".toKey()
            validate {
                failIfNot("$value is not in range $validRange") { value in validRange }
            }
        }
        val includeCancelled by defaultingBoolean {
            name = "include_cancelled".toKey()
            description = "also list cancelled events".toKey()
            defaultValue = false
        }
    }

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "twitch".toKey()
            description = "twitch notifications".toKey()
            allowInDms = false

            ephemeralSubCommand(::TwitchAddArgs) {
                name = "add".toKey()
                description = "be notified about more streamers".toKey()
                locking = true

                check {
                    with(configurationExtension) { requiresBotControl() }
                }

                requireBotPermissions(
                    *requiredPermissions
                )

                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = add(
                            guild,
                            arguments,
                            event.interaction.channel,
                        )

                        respond {
                            content = responseMessage
                        }
                    }
                }
            }

            ephemeralSubCommand(::TwitchRemoveArgs) {
                name = "remove".toKey()
                description = "removes a streamer from notifications".toKey()

                check {
                    with(configurationExtension) { requiresBotControl() }
                }

                requireBotPermissions(
                    Permission.ManageMessages
                )

                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = remove(
                            guild,
                            arguments,
                            event.interaction.channel,
                        )

                        respond {
                            content = responseMessage
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = "list".toKey()
                description = "lists all streamers in config".toKey()

                check {
                    with(configurationExtension) { requiresBotControl() }
                }

                action {
                    withLogContext(event, guild) { guild ->
                        val twitchGuildConfig = guild.config().get() ?: TwitchGuildConfig()

                        val configEntries = twitchGuildConfig.configs.values
                            .sortedBy { it.roleId }
                            .sortedBy { it.channelId }
                        val messages = configEntries.map { entry ->
                            val channel = entry.channel(guild)
                            val message = entry.messageId?.let { channel.getMessageOrNull(it) }
                            val jumpUrl = message?.getJumpUrl()
                            val role = entry.role(guild)

                            if (jumpUrl != null) {
                                "$jumpUrl ${role.mention} <${entry.twitchUrl}>"
                            } else {
                                "${channel.mention} ${role.mention} <${entry.twitchUrl}>"
                            }
                        }


                        if (messages.isEmpty()) {
                            respond {
                                content = "no twitch notifications registered, get started with /twitch add "
                            }
                            return@withLogContext
                        }

                        val response =
                            "registered twitch notifications: \n\n${messages.joinToString("\n")}"

                        if (response.length >= 2000) {
                            respond {
                                addFile(
                                    "response.txt",
                                    ChannelProvider {
                                        ByteReadChannel(response)
                                    }
                                )
                            }
                        } else {
                            respond {
                                content = response
                            }
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = "check".toKey()
                description = "check permissions in channel".toKey()

                requireBotPermissions(
                    *requiredPermissions
                )
                check {
                    with(configurationExtension) { requiresBotControl() }
                }

                action {
                    respond {
                        content = "OK"
                    }
                }
            }

            ephemeralSubCommand {
                name = "status".toKey()
                description = "check status of twitch background loop".toKey()

                check {
                    with(configurationExtension) { requiresBotControl() }
                }

                action {
                    respond {
                        content = "running: ${task.running}"
                    }
                }
            }

            ephemeralSubCommand() {
                name = "cleanup".toKey()
                description = "deletes old messages sent by the bot".toKey()
//                requireBotPermissions(
//                    Permission.ManageMessages,
//                )
                check {
                    with(configurationExtension) { requiresBotControl() }
                }
                action {
                    withLogContext(event, guild) { guild ->
                        val channel = event.interaction.channel.asChannelOf<TopGuildMessageChannel>()
                        val before = Clock.System.now() - 7.days

                        val webhooks = findWebhooks(channel = channel)
                            ?: relayError("could not find assosciated webhook".toKey())


                        val messagesToDelete = webhooks.flatMap { webhook ->
                            val token = webhook.token ?: return@flatMap emptyList()
                            val messagesToDelete = channel.getMessagesBefore(
                                messageId = channel.lastMessageId ?: relayError("empty channel".toKey()),
                                limit = 1000,
                            )
                                .filter {
                                    it.webhookId == webhook.id
                                }
                                .filter {
                                    val timestamp = it.editedTimestamp ?: it.timestamp
                                    timestamp < before
                                }
                                .toList()
                                .sortedBy { it.editedTimestamp ?: it.timestamp }

                            messagesToDelete.map { message -> message to webhook }
                        }

                        val followUp = respond {
                            content = """
                                deleting ${messagesToDelete.size} messages ...
                            """.trimIndent()
                        }

                        val deleted = messagesToDelete.map { (message, webhook) ->
                            delay(1)
                            val token = webhook.token ?: return@map false
//                            logger.debugF { "deleting message ${message.getJumpUrl()}" }
                            try {
                                webhook.deleteMessage(token, message.id)
//                                channel.deleteMessage(message.id)
                                true
                            } catch (e: Exception) {
                                logger.errorF(e) { "failed to delete message ${message.getJumpUrl()}" }
                                false
                            }
                        }.count { it }

                        followUp.edit {
                            content = """
                                deleted $deleted messages ✅
                            """.trimIndent()
                        }
                    }
                }
            }
            group("schedule".toKey()) {
                description = "twitch schedule sync".toKey()
                ephemeralSubCommand(::TwitchScheduleSyncArgs) {
                    name = "sync".toKey()
                    description = "synchronize schedule".toKey()
                    requireBotPermissions(
                        Permission.ManageEvents,
                    )
                    check {
                        with(configurationExtension) { requiresBotControl() }
                    }
                    action {
                        withLogContext(event, guild) { guild ->
                            val token = httpClient.getToken()
                            val userData = httpClient.getUsers(
                                logins = listOf(arguments.twitchUserName),
                                token = token,
                            )[arguments.twitchUserName]
                                ?: relayError("cannot get user data for ${arguments.twitchUserName}".toKey())
                            val segments = httpClient.getSchedule(
                                token = token,
                                broadcasterId = userData.id,
                            ).filter { it.canceledUntil == null && it.vacationCancelledUntil == null }
                                .take(arguments.amount).toList().distinct()
                            val existingEvents = guild.scheduledEvents
                                .filter { event -> event.entityType == ScheduledEntityType.External }
                                .filter { event ->
                                    event.location?.contains("https://twitch.tv/${userData.login}") ?: false
                                }
                                .toList()
                            val segmentsWithEvents = segments.associateWith { segment ->
                                val now = Clock.System.now()
                                val title = segment.title + (segment.category?.name?.let { " - $it" } ?: "")
                                existingEvents
                                    .filter { event -> event.name == title }
                                    .filter { event ->
                                        (event.scheduledStartTime < now && event.status == GuildScheduledEventStatus.Active)
                                                || (event.scheduledStartTime == segment.startTime && event.status == GuildScheduledEventStatus.Scheduled)
                                    }
                                    .filter { event -> event.scheduledEndTime == segment.endTime }
                                    .filter { event -> event.description?.contains(segment.id) ?: false }
                                    .also {
                                        if (it.size > 1) {
                                            this@TwitchExtension.logger.warnF { "found ${it.size} events for segment $segment" }
                                        }
                                    }
                            }
                            val notMatchingEvents = existingEvents
                                .filter { event -> event.creatorId == this@TwitchExtension.kord.selfId }
                                .toSet() - segmentsWithEvents.values.flatten().toSet()
                            var followUp = respond {
                                content = listOfNotNull(
                                    "found ${segments.size} segments",
                                    notMatchingEvents.takeIf { it.isNotEmpty() }?.let {
                                        "deleting ${it.size} events"
                                    },
                                ).joinToString("\n")
                            }
                            notMatchingEvents.forEach {
                                it.delete()
                            }
                            val now = Clock.System.now()
                            val segmentsToCreateEventsFor = segments.filter { segment ->
                                val existingEventsForSegment = segmentsWithEvents[segment] ?: emptyList()
                                existingEventsForSegment.isEmpty() && segment.endTime > now
                            }
                            followUp = followUp.edit {
                                content = listOfNotNull(
                                    "found ${segments.size} segments",
                                    notMatchingEvents.takeIf { it.isNotEmpty() }?.let {
                                        "deleted ${it.size} events ✅"
                                    },
                                    segmentsWithEvents.takeIf { it.isNotEmpty() }?.let {
                                        "creating ${it.size} events ... (this may take about ${((it.size - 5) / 5).minutes}"
                                    },
                                ).joinToString("\n")
                            }
                            launch(
                                this@TwitchExtension.kord.coroutineContext
                                        + CoroutineName("events-create-${userData.login}")
                            ) {
                                val createdEvents = segmentsToCreateEventsFor.map { segment ->
                                    val startTime = segment.startTime.takeIf { it > now } ?: (now + 5.seconds)
                                    val title = segment.title + (segment.category?.name?.let { " - $it" } ?: "")
                                    guild.createScheduledEvent(
                                        name = title,
                                        privacyLevel = GuildScheduledEventPrivacyLevel.GuildOnly,
                                        scheduledStartTime = startTime,
                                        entityType = ScheduledEntityType.External,
                                    ) {
                                        description = """
                                automatically created by ${event.interaction.user.mention} at ${now.toMessageFormat()}
                                id: ${segment.id}
                            """.trimIndent()
                                        scheduledEndTime = segment.endTime
                                        entityMetadata = GuildScheduledEventEntityMetadata(
                                            location = "https://twitch.tv/${userData.login}".optional()
                                        )
                                    }.also { event ->
                                        this@TwitchExtension.logger.infoF { "created event ${event.id} for segment $segment" }
                                    }
                                }
                                this@TwitchExtension.logger.infoF { "done creating events" }
                                followUp.edit {
                                    content = listOfNotNull(
                                        "found ${segments.size} segments",
                                        notMatchingEvents.takeIf { it.isNotEmpty() }?.let {
                                            "deleted ${it.size} events ✅"
                                        },
                                        "created ${createdEvents.size}/${segmentsToCreateEventsFor.size} events ✅",
                                        (segmentsToCreateEventsFor.size - createdEvents.size).takeIf { it > 0 }?.let {
                                            "failed creating $it events ❌"
                                        },
                                    )
                                        .joinToString("\n")
                                        .also {
                                            this@TwitchExtension.logger.infoF { "message: \n$it" }
                                        }
                                }
                                this@TwitchExtension.logger.infoF { "followup edited" }
                            }
                        }
                    }
                }
                ephemeralSubCommand(::TwitchScheduleDeleteArgs) {
                    name = "delete".toKey()
                    description = "delete all events for a twitch user".toKey()
                    requireBotPermissions(
                        Permission.ManageEvents,
                    )
                    check {
                        with(configurationExtension) { requiresBotControl() }
                    }
                    action {
                        withLogContext(event, guild) { guild ->
                            val token = httpClient.getToken()
                            val userData = httpClient.getUsers(
                                logins = listOf(arguments.twitchUserName),
                                token = token,
                            )[arguments.twitchUserName]
                                ?: relayError("cannot get user data for ${arguments.twitchUserName}".toKey())
                            val existingEvents = guild.scheduledEvents
                                .filter { event -> event.entityType == ScheduledEntityType.External }
                                .filter { event ->
                                    event.location?.contains("https://twitch.tv/${userData.login}") ?: false
                                }
                                .toList()
                            val followUp = respond {
                                content = """
                    deleting ${existingEvents.size} events ...
                """.trimIndent()
                            }
                            existingEvents.forEach { it.delete() }
                            followUp.edit {
                                content = """
                    deleted ${existingEvents.size} events ✅
                """.trimIndent()
                            }
                        }
                    }
                }
                ephemeralSubCommand(::TwitchScheduleListArgs) {
                    name = "list".toKey()
                    description = "list streamer schedule".toKey()
                    requireBotPermissions(
                        Permission.ManageEvents,
                    )
                    action {
                        withLogContext(event, guild) { guild ->
                            val token = httpClient.getToken()
                            val userData = httpClient.getUsers(
                                logins = listOf(arguments.twitchUserName),
                                token = token,
                            )[arguments.twitchUserName]
                                ?: relayError("cannot get user data for ${arguments.twitchUserName}".toKey())
                            val segments = httpClient.getSchedule(
                                token = token,
                                broadcasterId = userData.id,
                            )
                                .filter { arguments.includeCancelled || (it.canceledUntil == null && it.vacationCancelledUntil == null) }
                                .take(arguments.amount)
                                .toList().distinct()
                            segments.forEach {
                                this@TwitchExtension.logger.debugF { it }
                            }
                            if (segments.isNotEmpty()) {
                                segments.joinToString("\n") { segment ->
                                    val title = segment.title.let {
                                        if (segment.canceledUntil != null || segment.vacationCancelledUntil != null) {
                                            "~~$it~~"
                                        } else {
                                            it
                                        }
                                    }
                                    "${segment.startTime.toMessageFormat()}-${segment.endTime.toMessageFormat()} ${title}" +
                                            (segment.category?.name?.let { " - $it" } ?: "")
                                }.linesChunkedByMaxLength(2000)
                                    .forEach {
                                        respond { content = it }
                                    }
                            } else {
                                respond {
                                    content = "no schedule segments found"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun add(
        guild: Guild,
        arguments: TwitchAddArgs,
        currentChannel: ChannelBehavior,
    ): String {
        val channelInput = arguments.channel ?: currentChannel.asChannel()
        val channel = guild.getChannelOfOrNull<TextChannel>(channelInput.id)
            ?: guild.getChannelOfOrNull<NewsChannel>(channelInput.id)
            ?: relayError("must be a TextChannel or NewsChannel, was: ${channelInput.type}".toKey())

        val user = try {
            val token = httpClient.getToken()
            val userData = httpClient.getUsers(
                token = token,
                logins = listOf(arguments.twitchUserName)
            )
            userData[arguments.twitchUserName.lowercase()]
                ?: relayError("cannot fetch user data: $userData for <https://twitch.tv/${arguments.twitchUserName}>".toKey())
        } catch (e: IllegalStateException) {
            relayError(
                e.message?.toKey()
                    ?: "unknown error fetching user data for <https://twitch.tv/${arguments.twitchUserName}>".toKey()
            )
        }

        val configUnit = guild.config()
        val config = configUnit.get() ?: TwitchGuildConfig()

        val newEntry = TwitchConfig(
            channelId = channel.id,
            twitchUserName = user.login,
            roleId = arguments.role.id,
            messageId = null
        )

        configUnit.save(
            config.update(
                newEntry.key(channel),
                newEntry
            )
        )

        return "added `${user.displayName}` <https://twitch.tv/${user.login}> to notify ${arguments.role.mention} in ${channelInput.mention}"
    }

    private suspend fun remove(guild: Guild, arguments: TwitchRemoveArgs, currentChannel: ChannelBehavior): String {
        val channelInput = arguments.channel ?: currentChannel.asChannel()
        val channel = guild.getChannelOfOrNull<TextChannel>(channelInput.id)
            ?: guild.getChannelOfOrNull<NewsChannel>(channelInput.id)
            ?: relayError("must be a TextChannel or NewsChannel, was: ${channelInput.type}".toKey())

        val configUnit = guild.config()
        val twitchGuildConfig = configUnit.get() ?: TwitchGuildConfig()
        val (key, toRemove) = twitchGuildConfig.find(
            channelId = channel.id,
            twitchUserName = arguments.twitchUserName
        ) ?: relayError("twitch config entry not found".toKey())

        try {
            toRemove.messageId?.let {
                channel.deleteMessage(it)
            }
        } catch (e: KtorRequestException) {
            logger.warnF { "failed to delete message" }
        }

        configUnit.save(
            configUnit.get()?.remove(key) ?: relayError("failed to save config".toKey())
        )

        return "removed ${arguments.twitchUserName} from ${channel.mention}"
    }

    private suspend fun getWebhook(
        channel: TopGuildMessageChannelBehavior,
    ): Webhook? {
        return try {
            webhooksCache.getOrPut(channel.id) {
                val webhookName = WEBHOOK_NAME_PREFIX + "-" + kord.getSelf().username
                channel.webhooks.firstOrNull {
                    it.name == webhookName && it.token != null
                }?.also { webhook ->
                    logger.traceF { "found webhook" }
                    webhooksCache[channel.id] = webhook
                } ?: channel.createWebhook(name = webhookName) {
                        avatar = Image.raw(
                            data = TwitchExtension::class.java
                                .getResourceAsStream("/twitch/TwitchGlitchPurple.png")
                                ?.readBytes() ?: error("failed to read bytes"),
                            format = Image.Format.PNG,
                        )
                    }.also { webhook ->
                        logger.infoF { "created webhook $webhook" }
                        webhooksCache[channel.id] = webhook
                    }
            }
        } catch (e: KtorRequestException) {
            logger.errorF { e.message }
            null
        }
    }


    private suspend fun findWebhooks(
        channel: TopGuildMessageChannelBehavior,
    ): List<Webhook>? {
        return try {
            val webhookName = WEBHOOK_NAME_PREFIX + "-" + kord.getSelf().username
            channel.webhooks
                .filter {
                    it.name == webhookName && it.token != null
                }
                .toList()
        } catch (e: KtorRequestException) {
            logger.errorF { e.message }
            null
        }
    }

    private suspend fun updateTwitchNotificationMessage(
        guild: Guild,
        key: String,
        twitchConfig: TwitchConfig,
        token: Token,
        userData: TwitchUserData,
        channelInfo: TwitchChannelInfo,
        streamData: StreamData?,
        gameData: TwitchGameData?,
        webhook: Webhook,
    ) {
        val channel = twitchConfig.channel(guild)
        logger.traceF { "updating ${twitchConfig.twitchUserName} in ${channel.name}" }
        when (channel) {
            is TextChannel, is NewsChannel -> {
            }

            else -> {
                logger.errorF { "channel: ${channel.name} is not a Text or News channel" }
            }
        }

        suspend fun updateMessageId(message: Message, publish: Boolean = true) {
            if (publish && channel is NewsChannel && !message.isPublished) {
                try {
                    logger.infoF { "publishing in ${channel.name}" }
                    message.publish()
                } catch (e: KtorRequestException) {
                    logger.errorF(e) { "failed to publish" }
                }
            }
            val configUnit = guild.config()

            configUnit.save(
                configUnit.get()?.update(
                    key,
                    twitchConfig.copy(
                        messageId = message.id
                    )
                ) ?: relayError("failed to save config".toKey())
            )
        }

        val oldMessage = twitchConfig.messageId?.let {
            channel.getMessageOrNull(it)
        } ?: findMessage(channel, userData, webhook)?.also { foundMessage ->
            updateMessageId(foundMessage, publish = false)
        }
        if (streamData != null) {
            // live
            val messageContent =
                "${twitchConfig.role(guild).mention} [twitch.tv/${userData.displayName}](https://twitch.tv/${userData.login})"
            if (oldMessage != null) {
                val containsMention = oldMessage.content.contains("""<@&\d+>""".toRegex())
                if (containsMention) {
                    // was online, editing message

                    // TODO: check title, timestamp and game_name, only edit if different
                    val oldEmbed = oldMessage.embeds.firstOrNull()
                    val editMessage = when {
                        oldEmbed == null -> true
                        oldMessage.content != messageContent -> true
                        oldEmbed.title != streamData.title -> true
                        oldEmbed.footer?.text != streamData.gameName -> true
                        oldEmbed.timestamp != streamData.startedAt -> true
                        else -> true // false
                    }
                    try {
                        if (editMessage) {
                            val updatedMessage = kord.rest.webhook.editWebhookMessage(
                                webhook.id,
                                webhook.token!!,
                                oldMessage.id
                            ) {
                                content = messageContent
                                embeds = mutableListOf()
                                embed {
                                    buildEmbed(userData, streamData, gameData)
                                }
                            }
                            val message = channel.getMessage(updatedMessage.id)
                            updateMessageId(message, publish = false)
                        }
                        return
                    } catch (e: RestRequestException) {
                        logger.errorF { "failed to edit webhook message" }

                    }
                }
            }
            // was offline, creating new message and deleting old message
            val webhookToken = webhook.token ?: error("failed to load token from webhook")
            val message = webhook.execute(webhookToken) {
                username = userData.displayName
                avatarUrl = userData.profileImageUrl
                content = messageContent
                embed {
                    buildEmbed(userData, streamData, gameData)
                }
            }
            updateMessageId(message, publish = true)
            logger.debugF { "deleting old message" }
            oldMessage?.delete()
        } else {
            // offline

            // TODO: delete message if VOD is disabled ?

            // check if it was online before
            val updateMessage = if (oldMessage != null && oldMessage.mentionedRoles.toList().isNotEmpty()) {
                oldMessage.mentionedRoles.toList().also { logger.infoF { "mentions: ${it.map { it.name }}" } }
                    .isNotEmpty()
                //oldMessage.content.contains("""<@&\d+>""".toRegex())
            } else {
                true
            }

            if (updateMessage) {
                val vod = httpClient.getLastVOD(
                    userId = userData.id,
                    token = token,
                )?.takeIf {
                    it.createdAt > (Clock.System.now() - 90.days)
                }
                val twitchUrl = "[twitch.tv/${userData.displayName}](https://twitch.tv/${userData.login})"
                val message = if (vod != null) {
                    val timestamp = TimestampType.RelativeTime.format(vod.createdAt.epochSeconds)
                    val cleanedVodTitle = vod.title
                        .replace("\\|\\|+".toRegex(), "|")
                    val vodUrl = "[${cleanedVodTitle}](${vod.url})"
                    """
                        $twitchUrl streamed **${channelInfo.gameName}**
                        > $vodUrl
                        $timestamp for `${vod.duration}`
                        """.trimIndent()
                } else {
                    """
                        $twitchUrl streamed **${channelInfo.gameName}**
                        > ${channelInfo.title}
                    """.trimIndent()
                }
                val messageId = oldMessage?.let {
                    try {
                        val messageId =
                            kord.rest.webhook.editWebhookMessage(webhook.id, webhook.token!!, oldMessage.id) {
                                content = message
                                embeds = mutableListOf()
                                suppressEmbeds = true
                            }.id
                        channel.getMessage(messageId)
                    } catch (e: RestRequestException) {
                        logger.errorF(e) { "failed to edit webhook message" }
                        null
                    }
                } ?: run {
                    logger.infoF { "executing webhook" }
                    webhook.execute(webhook.token!!) {
                        username = userData.displayName
                        avatarUrl = userData.profileImageUrl
                        content = message
                        suppressEmbeds = true
                    }
                }
                updateMessageId(messageId, publish = false)
            }
        }
    }

    private suspend fun findMessage(
        channel: TopGuildMessageChannel,
        userData: TwitchUserData,
        webhook: Webhook,
    ): Message? {
        logger.debugF { "searching for message with author '${userData.displayName}' in '${channel.name}' webhook: ${webhook.id}" }
        return try {
            channel.getMessagesBefore(
                channel.lastMessageId ?: run {
                    logger.warn { "last message id was null, channel might be empty" }
                    return null
                },
                100
            ).filter { message ->
//                val author = message.data.author
                val author = message.author ?: run {
                    logger.warnF { "author was null, messageId: ${message.id}" }
                    return@filter false
                }
                logger.traceF { "author: $author" }
                author.id == webhook.id && author.username == userData.displayName
            }.firstOrNull()
        } catch (e: NullPointerException) {
            logger.errorF(e) { "error while trying to find old message" }
            null
        } catch (e: IndexOutOfBoundsException) {
            logger.errorF(e) { "how the fuck.. ?" }
            null
        }
    }

    private fun EmbedBuilder.buildEmbed(
        usersData: TwitchUserData,
        streamData: StreamData,
        gameData: TwitchGameData?,
    ) {
        author {
            name = usersData.login
            url = "https://twitch.tv/${usersData.login}"
            icon = usersData.profileImageUrl
        }
        url = "https://twitch.tv/${usersData.login}"
        title = streamData.title
        timestamp = streamData.startedAt
        if (gameData != null) {
            footer {
                text = gameData.name
                icon = gameData.boxArtUrl
                    .replace("{height}", "16")
                    .replace("{width}", "16")
            }
        } else {
            footer {
                text = streamData.gameName
            }
        }
    }

    private suspend fun checkStreams(guilds: List<Guild>, token: Token) = coroutineScope {

        val mappedTwitchConfigs = guilds.associateWith { guild ->
            guild.config().reload()?.configs?.values.orEmpty()
        }
        val mappedTwitchConfigPairs = guilds.associateWith { guild ->
            guild.config().reload()?.configs.orEmpty()
        }

        logger.traceF { "checking twitch status for ${guilds.map { it.name }}" }


        val channels = try {
            mappedTwitchConfigs.keys.flatMap { guild ->
                val twitchConfig = mappedTwitchConfigs.getValue(guild)
                twitchConfig.mapNotNull {
                    try {
                        it.channel(guild)
                    } catch (e: DiscordRelayedException) {
                        logger.errorF(e) { "failed to resolve channel by id" }
                        null
                    }
                }.distinct()
            }
        } catch (e: CancellationException) {
            logger.errorF(e) { "cancellation exception while fetching channels" }
            throw CancellationException("filed fetching channels", e)
        }

        val webhooks = try {
            channels.mapNotNull { channel ->
                withContext(logContext("channel" to channel.asChannel().name)) {
                    getWebhook(channel)
                }
            }.associateBy { it.channel.id }
        } catch (e: CancellationException) {
            logger.errorF(e) { "cancellation exception while fetching webhooks" }
            throw CancellationException("failed fetching webhooks", e)
        }

        val streamDataMap = httpClient.getStreams(
            userLogins = mappedTwitchConfigs.values.flatMap {
                it.map { it.twitchUserName }
            }.distinct(),
            token = token,
        )
        val userDataMap = httpClient.getUsers(
            logins = mappedTwitchConfigs.values.flatMap {
                it.map { it.twitchUserName }
            }.distinct(),
            token = token,
        )
        val gameDataMap = httpClient.getGames(
            gameNames = streamDataMap.values.map { it.gameName },
            token = token,
        )
        val channelInfoMap = httpClient.getChannelInfo(
            broadcasterIds = userDataMap.values.map { it.id },
            token = token,
        )

        try {
            if (streamDataMap.isNotEmpty()) {
                val userName = streamDataMap.values.random().userName
                val message = when (streamDataMap.size) {
                    1 -> userName
                    else -> "$userName and ${streamDataMap.size - 1} More"
                }
                kord.editPresence {
                    status = PresenceStatus.Online
                    watching(message)
                }
            } else {
                kord.editPresence {
                    status = PresenceStatus.Idle
                    afk = true
                }
            }
        } catch (e: RequestException) {
            logger.errorF(e) { "failed to update presence" }
        } catch (e: CancellationException) {
            logger.errorF(e) { "failed to update presence" }
        }

        mappedTwitchConfigPairs.entries.chunked(5).forEach { chunk ->
            coroutineScope {
                chunk.forEach chunkLoop@{ (guild, twitchConfigs) ->
                    launch(Dispatchers.IO) {
                        twitchConfigs.forEach configLoop@{ (key, twitchConfig) ->
                            val userData = userDataMap[twitchConfig.twitchUserName.lowercase()] ?: return@configLoop
                            val channelInfo = channelInfoMap[userData.login.lowercase()] ?: return@configLoop
                            val webhook = webhooks[twitchConfig.channelId] ?: return@configLoop
                            val streamData = streamDataMap[userData.login.lowercase()]
                            val gameData = gameDataMap[streamData?.gameName?.lowercase()]

                            withContext(
                                logContext(
                                    "twitch" to userData.login,
                                    "guild" to guild.name,
                                )
                            ) {
                                try {
                                    withTimeout(15.seconds) {
                                        updateTwitchNotificationMessage(
                                            guild = guild,
                                            key = key,
                                            twitchConfig = twitchConfig,
                                            token = token,
                                            userData = userData,
                                            channelInfo = channelInfo,
                                            streamData = streamData,
                                            gameData = gameData,
                                            webhook = webhook
                                        )
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    logger.errorF(e) { "timed out updating ${twitchConfig.twitchUserName} $twitchConfig" }
                                } catch (e: CancellationException) {
                                    logger.errorF(e) { "cancellation while updating ${twitchConfig.twitchUserName} $twitchConfig" }
                                } catch (e: DiscordRelayedException) {
                                    logger.errorF(e) { e.reason }
                                } catch (e: KtorRequestException) {
                                    logger.errorF(e) { "request exception when updating $twitchConfig" }
                                } catch (e: Exception) {
                                    logger.errorF(e) { e.message }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun loadConfig(guild: Guild): TwitchGuildConfig? {
        return guild.config().get()
    }
}
