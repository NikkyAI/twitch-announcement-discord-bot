package moe.nikky

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.chatGroupCommand
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.*
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import com.kotlindiscord.kord.extensions.utils.scheduling.Task
import dev.kord.common.entity.*
import dev.kord.common.entity.optional.optional
import dev.kord.common.toMessageFormat
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.TopGuildMessageChannelBehavior
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
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import dev.kord.rest.request.KtorRequestException
import io.klogging.Klogging
import io.klogging.context.logContext
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import moe.nikky.TwitchApi.getChannelInfo
import moe.nikky.TwitchApi.getGames
import moe.nikky.TwitchApi.getLastVOD
import moe.nikky.TwitchApi.getSchedule
import moe.nikky.TwitchApi.getStreams
import moe.nikky.TwitchApi.getToken
import moe.nikky.TwitchApi.getUsers
import moe.nikky.checks.hasBotControl
import moe.nikky.db.DiscordbotDatabase
import moe.nikky.db.TwitchConfig
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TwitchNotificationExtension() : Extension(), Klogging {
    override val name = "Twitch Notifications"

    private val database: DiscordbotDatabase by inject()

    //    private var token: Token? = null
//    private var tokenExpiration: Instant = Instant.DISTANT_PAST
    private val webhooksCache = mutableMapOf<Snowflake, Webhook>()

    private val token by lazy {
        runBlocking {
            httpClient.getToken()
        }
    }

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

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
//        install(Logging) {
//            logger = Logger.DEFAULT
//            level = LogLevel.ALL
//        }
    }
    private val httpClientVerbose = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    private val scheduler = Scheduler()
    private val task: Task =
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
                    logger.traceF { "checking streams" }
                    val token = httpClient.getToken()
                    if (token != null) {
                        checkStreams(
                            guilds = kord.guilds.toList(),
                            token = token
                        )
                    } else {
                        logger.errorF { "failed to acquire token" }
                        delay(5.seconds)
                    }
                }
            } catch (e: Exception) {
                logger.errorF(e) { "failed in twitch loop" }
            } finally {
                delay(15.seconds)
            }
        }

    inner class TwitchAddArgs : Arguments() {
        val role by role {
            name = "role"
            description = "notification ping"
        }
        val twitchUserName by string {
            name = "twitch"
            description = "Twitch username"
        }
        val channel by optionalChannel {
            name = "channel"
            description = "notification channel, defaults to current channel"
            requireChannelType(ChannelType.GuildText)
        }
    }

    inner class TwitchRemoveArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch"
            description = "Twitch username"
        }
        val channel by optionalChannel {
            name = "channel"
            description = "notification channel, defaults to current channel"
            requireChannelType(ChannelType.GuildText)
        }
    }

    inner class TwitchScheduleSyncArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch"
            description = "Twitch username"
        }
        val amount by defaultingInt {
            val validRange = 1..100
            name = "max"
            defaultValue = 30
            description =
                "amount of schedule entries to list, value between ${validRange.first} and ${validRange.last}, default: $defaultValue"
            validate {
                failIfNot("$value is not in range $validRange") { value in validRange }
            }
        }
    }

    inner class TwitchScheduleDeleteArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch"
            description = "Twitch username"
        }
    }

    inner class TwitchScheduleListArgs : Arguments() {
        val twitchUserName by string {
            name = "twitch"
            description = "Twitch username"
        }
        val amount by defaultingInt {
            val validRange = 1..100
            name = "max"
            defaultValue = 30
            description =
                "amount of schedule entries to list, value between ${validRange.first} and ${validRange.last}, default: $defaultValue"
            validate {
                failIfNot("$value is not in range $validRange") { value in validRange }
            }
        }
        val includeCancelled by defaultingBoolean {
            name = "includeCancelled"
            description = "also list cancelled events"
            defaultValue = false
        }
    }

    override suspend fun setup() {
        chatGroupCommand {
            name = "twitch"
            description = "be notified about more streamers"

            chatCommand(::TwitchAddArgs) {
                name = "add"
                description = "be notified about more streamers"
                locking = true

                check {
                    hasBotControl(database, event.getLocale())
                }

                requireBotPermissions(
                    *requiredPermissions
                )
                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = add(
                            guild,
                            arguments,
                            event.message.channel,
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
            chatCommand(::TwitchRemoveArgs) {
                name = "remove"
                description = "removes a streamer from notifications"

                check {
                    hasBotControl(database, event.getLocale())
                }

                requireBotPermissions(
                    Permission.ManageMessages
                )

                action {
                    withLogContext(event, guild) { guild ->
                        val responseMessage = remove(
                            guild,
                            arguments,
                            event.message.channel,
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
            name = "twitch"
            description = "twitch notifications"

            ephemeralSubCommand(::TwitchAddArgs) {
                name = "add"
                description = "be notified about more streamers"
                locking = true

                check {
                    hasBotControl(database)
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
                name = "remove"
                description = "removes a streamer from notifications"

                check {
                    hasBotControl(database)
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
                name = "list"
                description = "lists all streamers in config"

                check {
                    hasBotControl(database)
                }

                action {
                    withLogContext(event, guild) { guild ->
                        val messages =
                            database.twitchConfigQueries.getAll(guildId = guild.id).executeAsList().map { entry ->
                                val message = entry.message?.let { channel.getMessageOrNull(it) }
                                """
                            <https://twitch.tv/${entry.twitchUserName}>
                            ${entry.role(guild).mention}
                            ${entry.channel(guild).mention}
                            ${message?.getJumpUrl()}
                        """.trimIndent()
                            }

                        val response = if (messages.isNotEmpty()) {
                            "registered twitch notifications: \n\n" + messages.joinToString("\n\n")
                        } else {
                            "no twitch notifications registered, get started with /twitch add "
                        }

                        respond {
                            content = response
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
                }

                action {
                    respond {
                        content = "OK"
                    }
                }
            }

            ephemeralSubCommand {
                name = "status"
                description = "check status of twitch background loop"

                check {
                    hasBotControl(database)
                }

                action {
                    respond {
                        content = "running: ${task.running}"
                    }
                }
            }

            ephemeralSubCommand(::TwitchScheduleSyncArgs) {
                name = "schedule-sync"
                description = "synchronize schedule"

                requireBotPermissions(
                    Permission.ManageEvents,
                )
                check {
                    hasBotControl(database)
                }

                action {
                    withLogContext(event, guild) { guild ->
                        val token = httpClient.getToken() ?: relayError("cannot get twitch token")
                        val userData = httpClient.getUsers(
                            logins = listOf(arguments.twitchUserName),
                            token = token,
                        )[arguments.twitchUserName]
                            ?: relayError("cannot get user data for ${arguments.twitchUserName}")

                        val segments = httpClient.getSchedule(
                            token = token,
                            broadcaster_id = userData.id,
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
                                        this@TwitchNotificationExtension.logger.warnF { "found ${it.size} events for segment $segment" }
                                    }
                                }
                        }

                        val notMatchingEvents = existingEvents
                            .filter { event -> event.creatorId == this@TwitchNotificationExtension.kord.selfId }
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
                            this@TwitchNotificationExtension.kord.coroutineContext
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
                                    this@TwitchNotificationExtension.logger.infoF { "created event ${event.id} for segment $segment" }
                                }
                            }

                            this@TwitchNotificationExtension.logger.infoF { "done creating events" }
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
                                        this@TwitchNotificationExtension.logger.infoF { "message: \n$it" }
                                    }
                            }
                            this@TwitchNotificationExtension.logger.infoF { "followup edited" }
                        }

                    }
                }
            }

            ephemeralSubCommand(::TwitchScheduleDeleteArgs) {
                name = "schedule-delete"
                description = "delete all events for a twitch user"

                requireBotPermissions(
                    Permission.ManageEvents,
                )
                check {
                    hasBotControl(database)
                }

                action {
                    withLogContext(event, guild) { guild ->
                        val token = httpClient.getToken() ?: relayError("cannot get twitch token")
                        val userData = httpClient.getUsers(
                            logins = listOf(arguments.twitchUserName),
                            token = token,
                        )[arguments.twitchUserName]
                            ?: relayError("cannot get user data for ${arguments.twitchUserName}")

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
                name = "schedule-list"
                description = "list streamer schedule"

                requireBotPermissions(
                    Permission.ManageEvents,
                )
                action {
                    withLogContext(event, guild) { guild ->
                        val token = httpClient.getToken() ?: relayError("cannot get twitch token")
                        val userData = httpClient.getUsers(
                            logins = listOf(arguments.twitchUserName),
                            token = token,
                        )[arguments.twitchUserName]
                            ?: relayError("cannot get user data for ${arguments.twitchUserName}")

                        val segments = httpClient.getSchedule(
                            token = token,
                            broadcaster_id = userData.id,
                        )
                            .filter { arguments.includeCancelled || (it.canceledUntil == null && it.vacationCancelledUntil == null) }
                            .take(arguments.amount)
                            .toList().distinct()
                            ?: relayError("cannot lookup twitch schedule")

                        segments.forEach {
                            this@TwitchNotificationExtension.logger.debugF { it }
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

//        event<ReadyEvent> {
//            action {
//                withContext(
//                    logContext(
//                        "event" to event::class.simpleName,
//                        "extension" to name
//                    )
//                ) {
//                    logger.infoF { "launching twitch background job" }
//                    backgroundJob = kord.launch(
//                        logContext(
//                            "event" to "TwitchLoop"
//                        ) + CoroutineName("TwitchLoop")
//                    ) {
//                        while (true) {
//                            delay(15_000)
//                            val token = httpClient.getToken()
//                            if (token != null) {
//                                checkStreams(kord.guilds.toList(), token)
//                            } else {
//                                logger.errorF { "failed to acquire token" }
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }

    private suspend fun add(
        guild: Guild,
        arguments: TwitchAddArgs,
        currentChannel: ChannelBehavior,
    ): String {
        val channelInput = arguments.channel ?: currentChannel.asChannel()
        val channel = guild.getChannelOfOrNull<TextChannel>(channelInput.id)
            ?: guild.getChannelOfOrNull<NewsChannel>(channelInput.id)
            ?: relayError("must be a TextChannel or NewsChannel, was: ${channelInput.type}")

        val user = try {
            val token = httpClient.getToken() ?: relayError("cannot get twitch token")
            val userData = httpClient.getUsers(
                token = token,
                logins = listOf(arguments.twitchUserName)
            )
                ?: relayError("cannot fetch user data for <https://twitch.tv/${arguments.twitchUserName}>")
            userData[arguments.twitchUserName.lowercase()]
                ?: relayError("cannot fetch user data: $userData for <https://twitch.tv/${arguments.twitchUserName}>")
        } catch (e: IllegalStateException) {
            relayError(
                e.message
                    ?: "unknown error fetching user data for <https://twitch.tv/${arguments.twitchUserName}>"
            )
        }

        database.twitchConfigQueries.upsert(
            guildId = guild.id,
            channel = channel.id,
            twitchUserName = user.login,
            role = arguments.role.id,
            message = null
        )

        return "added `${user.display_name}` <https://twitch.tv/${user.login}> to ${channelInput.mention} to notify ${arguments.role.mention}"
    }

    private suspend fun remove(guild: Guild, arguments: TwitchRemoveArgs, currentChannel: ChannelBehavior): String {
        val channelInput = arguments.channel ?: currentChannel.asChannel()
        val channel = guild.getChannelOfOrNull<TextChannel>(channelInput.id)
            ?: guild.getChannelOfOrNull<NewsChannel>(channelInput.id)
            ?: relayError("must be a TextChannel or NewsChannel, was: ${channelInput.type}")

        val toRemove = database.twitchConfigQueries.get(
            guildId = guild.id,
            channel = channel.id,
            twitchUserName = arguments.twitchUserName
        ).executeAsOneOrNull()

        toRemove?.message?.let {
            channel.deleteMessage(it)
        }

        database.twitchConfigQueries.delete(
            guildId = guild.id,
            channel = channel.id,
            twitchUserName = arguments.twitchUserName
        )

        return "removed ${arguments.twitchUserName} from ${channel.mention}"
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun getWebhook(channel: TopGuildMessageChannelBehavior): Webhook? {
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
                        data = TwitchNotificationExtension::class.java
                            .getResourceAsStream("/twitch/TwitchGlitchPurple.png")
                            ?.readBytes() ?: error("failed to read bytes"),
                        format = Image.Format.PNG,
                    )
                }.also { webhook ->
                    logger.infoF { "created webhook $webhook" }
                    webhooksCache[channel.id] = webhook
                }
            }

//            webhooksCache[channel.id]?.also {
//                logger.traceF { "reusing webhook" }
//            } ?: channel.webhooks.firstOrNull {
//                it.name == WEBHOOK_NAME
//            }?.also { webhook ->
//                logger.traceF { "found webhook" }
//                webhooksCache[channel.id] = webhook
//            } ?: channel.createWebhook(name = WEBHOOK_NAME) {
//                @Suppress("BlockingMethodInNonBlockingContext")
//                avatar = Image.raw(
//                    data = TwitchNotificationExtension::class.java.getResourceAsStream("/twitch/TwitchGlitchPurple.png")
//                        ?.readAllBytes() ?: error("failed to read bytes"),
//                    format = Image.Format.PNG,
//                )
//            }.also { webhook ->
//                logger.infoF { "created webhook $webhook" }
//                webhooksCache[channel.id] = webhook
//            }
        } catch (e: KtorRequestException) {
            logger.errorF { e.message }
            null
        }
    }


    private suspend fun updateTwitchNotificationMessage(
        guild: Guild,
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
            database.twitchConfigQueries.updateMessage(
                message = message.id,
                guildId = guild.id,
                channel = channel.id,
                twitchUserName = twitchConfig.twitchUserName
            )
        }

        val oldMessage = twitchConfig.message?.let {
            channel.getMessageOrNull(it)
        } ?: findMessage(channel, userData, webhook)?.also { foundMessage ->
            updateMessageId(foundMessage, publish = false)
        }
        logger.traceF { "old message is ${oldMessage}" }
        logger.traceF { "stream title: ${streamData?.title}" }
        if (streamData != null) {
            // live
            if (oldMessage != null) {
                val containsMention = oldMessage.content.contains("""<@&\d+>""".toRegex())
                if (containsMention) {
                    // was online, editing message

                    // TODO: check title, timestamp and game_name, only edit if different
                    val oldEmbed = oldMessage.embeds.firstOrNull()
                    val messageContent =
                        "<https://twitch.tv/${userData.login}> \n ${twitchConfig.role(guild).mention}"
                    val editMessage = when {
                        oldEmbed == null -> true
                        oldMessage.content != messageContent -> true
                        oldEmbed.title != streamData.title -> true
                        oldEmbed.footer?.text != streamData.game_name -> true
                        oldEmbed.timestamp != streamData.started_at -> true
                        else -> true // false
                    }
                    if (editMessage) {
                        logger.traceF { "editing message ${oldMessage.id}" }
                        val updatedMessage = kord.rest.webhook.editWebhookMessage(
                            webhook.id,
                            webhook.token!!,
                            oldMessage.id
                        ) {
                            content = messageContent
                            embed {
                                buildEmbed(userData, streamData, gameData)
                            }
                        }
                        val message = channel.getMessage(updatedMessage.id)
                        updateMessageId(message, publish = true)
                    }
                    return
                }
            }
            // was offline, creating new message and deleting old message
            val webhookToken = webhook.token ?: error("failed to load token from webhook")
            val message = webhook.execute(webhookToken) {
                username = userData.display_name
                avatarUrl = userData.profile_image_url
                content =
                    "<https://twitch.tv/${userData.login}> \n ${twitchConfig.role(guild).mention}"
                embed {
                    buildEmbed(userData, streamData, gameData)
                }
            }
            updateMessageId(message, publish = true)
            oldMessage?.delete()
        } else {
            // offline

            // TODO: delete message if VOD is disabled ?

            // check if it was online before
            val updateMessage = if (oldMessage != null) {
                oldMessage.content.contains("""<@&\d+>""".toRegex())
            } else {
                true
            }

            if (updateMessage) {
                val vod = httpClient.getLastVOD(
                    userId = userData.id,
                    token = token,
                )
                val message = "<https://twitch.tv/${userData.login}>\n" +
                        if (vod != null) {
                            """
                            <${vod.url}>
                            **${vod.title}**
                            ${channelInfo.game_name}
                            """.trimIndent()
                        } else {
                            """
                            _VOD url not available_
                            **${channelInfo.title}**
                            ${channelInfo.game_name}
                            """.trimIndent()
                        }
                val messageId = if (oldMessage != null) {
                    val messageId = kord.rest.webhook.editWebhookMessage(webhook.id, webhook.token!!, oldMessage.id) {
                        content = message
                        embeds = mutableListOf()
                    }.id
                    channel.getMessage(messageId)
                } else {
                    webhook.execute(webhook.token!!) {
                        username = userData.display_name
                        avatarUrl = userData.profile_image_url
                        content = message
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
        logger.debugF { "searching for message with author '${userData.display_name}' in '${channel.name}' webhook: ${webhook.id}" }
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
                author.id == webhook.id && author.username == userData.display_name
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
            icon = usersData.profile_image_url
        }
        url = "https://twitch.tv/${usersData.login}"
        title = streamData.title
        timestamp = streamData.started_at
        if (gameData != null) {
            footer {
                text = gameData.name
                icon = gameData.box_art_url
                    .replace("{height}", "16")
                    .replace("{width}", "16")
            }
        } else {
            footer {
                text = streamData.game_name
            }
        }
    }

    private suspend fun checkStreams(guilds: List<Guild>, token: Token) = coroutineScope {
        val mappedTwitchConfigs = guilds.associateWith { guild ->
            database.getTwitchConfigs(guild)
        }

        logger.traceF { "checking twitch status for ${guilds.map { it.name }}" }

        val channels = mappedTwitchConfigs.keys.flatMap { guild ->
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

        val webhooks = channels.mapNotNull { channel ->
            withContext(logContext("channel" to channel.asChannel().name)) {
                getWebhook(channel)
            }
        }.associateBy { it.channel.id }

        val streamDataMap = httpClient.getStreams(
            user_logins = mappedTwitchConfigs.values.flatMap {
                it.map(TwitchConfig::twitchUserName)
            }.distinct(),
            token = token,
        )
        val userDataMap = httpClient.getUsers(
            logins = mappedTwitchConfigs.values.flatMap {
                it.map(TwitchConfig::twitchUserName)
            }.distinct(),
            token = token,
        )
        val gameDataMap = httpClient.getGames(
            gameNames = streamDataMap.values.map { it.game_name },
            token = token,
        )
        val channelInfoMap = httpClient.getChannelInfo(
            broadcasterIds = userDataMap.values.map { it.id },
            token = token,
        )

        if (streamDataMap.isNotEmpty()) {
            val userName = streamDataMap.values.random().user_name
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

        mappedTwitchConfigs.entries.chunked(10).forEach { chunk ->
            coroutineScope {
                chunk.forEach chunkLoop@{ (guild, twitchConfigs) ->
                    launch(Dispatchers.IO) {
                        twitchConfigs.forEach configLoop@{ twitchConfig ->
                            val userData = userDataMap[twitchConfig.twitchUserName.lowercase()] ?: return@configLoop
                            val channelInfo = channelInfoMap[userData.login.lowercase()] ?: return@configLoop
                            val webhook = webhooks[twitchConfig.channel] ?: return@configLoop
                            val streamData = streamDataMap[userData.login.lowercase()]
                            val gameData = gameDataMap[streamData?.game_name?.lowercase()]

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
}
