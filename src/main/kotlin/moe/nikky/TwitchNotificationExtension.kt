package moe.nikky

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.TextChannelBehavior
import dev.kord.core.behavior.channel.createWebhook
import dev.kord.core.behavior.execute
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Webhook
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.nikky.checks.hasBotControl
import mu.KotlinLogging
import org.koin.core.component.inject
import java.lang.IllegalStateException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class TwitchNotificationExtension() : Extension() {
    override val name = "Twitch Notifications"
    private val logger = KotlinLogging.logger {}

    private val config: ConfigurationService by inject()
    private var token: TokenHolder? = null

    companion object {
        private const val WEBHOOK_NAME = "twitch-notifications"
        private const val twitchApi = "https://api.twitch.tv/helix"
        private val clientId = envOrNull("TWITCH_CLIENT_ID")
        private val clientSecret = envOrNull("TWITCH_CLIENT_SECRET")
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
//    private val httpClient = kord.resources.httpClient

    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
//        install(Logging) {
//            logger = Logger.DEFAULT
//            level = LogLevel.INFO
//        }
    }

    @OptIn(ExperimentalTime::class)
    private class TokenHolder(
        val token: Token,
        val epiration: Instant = Clock.System.now() + Duration.seconds(token.expires_in)
    ) {

    }

    init {
        runBlocking {
            val token = httpClient.getToken()
        }
    }

    inner class TwitchAddArgs : Arguments() {
        val role by role("role", "notification ping")
        val twitchUserName by string("twitch", "Twitch username")
        val channel by optionalChannel("channel", "notification channel, defaults to current channel")
    }

    inner class TwitchRemoveArgs : Arguments() {
        val twitchUserName by string("twitch", "Twitch username")
        val channel by optionalChannel("channel", "notification channel, defaults to current channel")
    }

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "twitch"
            description = "twitch notifications"

            ephemeralSubCommand(::TwitchAddArgs) {
                name = "add"
                description = "be notified about more streamers"
                locking = true

                requireBotPermissions(
                    Permission.ManageWebhooks,
                    Permission.ManageMessages
                )

                check {
                    hasBotControl(config)
                }

                action {
                    val guild = guild!!.asGuild()
                    val state = config[guild] ?: errorMessage("could not lookup state")

                    val channelInput = arguments.channel ?: event.interaction.channel
                    val channel = guild.getChannelOfOrNull<TextChannel>(channelInput.id)
                        ?: errorMessage("must be a TextChannel, was: ${channelInput::class.simpleName}")
                    val token = httpClient.getToken() ?: errorMessage("cannot get twitch token")
                    this@TwitchNotificationExtension.logger.debug { "token: $token" }
                    val user = try {
                        val userData = httpClient.getUsers(token, listOf(arguments.twitchUserName))
                            ?: errorMessage("cannot fetch user data for <https://twitch.tv/${arguments.twitchUserName}>")
                        userData[arguments.twitchUserName.lowercase()]
                            ?: errorMessage("cannot fetch user data: $userData for <https://twitch.tv/${arguments.twitchUserName}>")
                    } catch (e: IllegalStateException) {
                        errorMessage(e.message ?: "unknown error fetching user data for <https://twitch.tv/${arguments.twitchUserName}>")
                    }

                    config[guild] = state.copy(
                        twitchNotifications = (
                                state.twitchNotifications +
                                        Pair(
                                            "${user.login}_${channel.id.asString}",
                                            TwitchNotificationState(
                                                channel = channel,
                                                twitchUserName = user.login,
                                                role = arguments.role,
                                            )
                                        )
                                ).also {
                                this@TwitchNotificationExtension.logger.info { "twith notif: $it" }
                            }
                    )
                    config.save()

                    respond {
                        content =
                            "added  ${user.display_name} <https://twitch.tv/${user.login}> to ${channelInput.mention} to notify ${arguments.role.mention}"
                    }
                }
            }

            ephemeralSubCommand(::TwitchRemoveArgs) {
                name = "remove"
                description = "removes a streamer from notifications"

                check {
                    hasBotControl(config)
                }

                requireBotPermissions(
                    Permission.ManageMessages
                )

                action {
                    val guild = guild!!.asGuild()
                    val state = config[guild] ?: errorMessage("could not lookup state")

                    val channelInput = arguments.channel ?: event.interaction.channel
                    val channel = guild.getChannelOfOrNull<TextChannel>(channelInput.id)
                        ?: errorMessage("must be a TextChannel, was: ${channelInput::class.simpleName}")

                    val toRemoveKey = "${arguments.twitchUserName.lowercase()}_${channel.id.asString}"
                    val toRemove = state.twitchNotifications[toRemoveKey]

                    config[guild] = state.copy(
                        twitchNotifications = state.twitchNotifications.filterKeys { it != toRemoveKey }
                    )
                    config.save()

                    toRemove?.oldMessage?.delete()

                    respond {
                        content = ""
                    }
                }

            }

            ephemeralSubCommand {
                name = "list"
                description = "lists all streamers in config"

                check {
                    hasBotControl(config)
                }

                action {
                    val guild = guild!!.asGuild()
                    val state = config[guild] ?: errorMessage("could not lookup state")

                    val messages = state.twitchNotifications.map { (key, entry) ->
                        entry.role
                        """
                            <https://twitch.tv/${entry.twitchUserName}>
                            ${entry.role.mention}
                            ${entry.channel.mention}
                            ${entry.oldMessage?.asMessage()?.getJumpUrl()}
                        """.trimIndent()
                    }

                    val response = if(messages.isNotEmpty()) {
                        "registered twitch notifications: \n\n" + messages.joinToString("\n\n")
                    } else {
                        "no twitch notifications registered, get started with /twitch add "
                    }

                    respond {
                        content= response
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

                val job = startBackgroundJob(guild)

                config[guild] = state.copy(
                    twitchBackgroundJob = job
                )
            }
        }


    }

    private suspend fun getWebhook(channel: TextChannelBehavior): Webhook {
        return channel.webhooks.firstOrNull {
            it.name == WEBHOOK_NAME
        } ?: channel.createWebhook(name = WEBHOOK_NAME) {
            @Suppress("BlockingMethodInNonBlockingContext")
            avatar = Image.raw(
                data = TwitchNotificationState::class.java.getResourceAsStream("/TwitchGlitchPurple.png")
                    ?.readAllBytes() ?: error("failed to read bytes"),
                format = Image.Format.PNG,
            )
        }
    }

    private suspend fun updateTwitchNotificationMessage(
        guildBehavior: GuildBehavior,
        twitchNotifSetting: TwitchNotificationState,
        token: Token,
        usersData: TwitchUserData,
        channelInfo: TwitchChannelInfo,
        streamData: StreamData?,
        gameData: TwitchGameData?,
    ) {
        val webhook = getWebhook(twitchNotifSetting.channel)
//        logger.debug { "webhook: $webhook" }

        suspend fun updateMessageId(messageId: Snowflake) {
            config[guildBehavior] = config[guildBehavior]!!.let { state ->
                val key = twitchNotifSetting.twitchUserName + "_" + twitchNotifSetting.channel.id.asString
                val twitchNotificationState = state.twitchNotifications[key]!!

                state.copy(
                    twitchNotifications = state.twitchNotifications + (
                            key to twitchNotificationState.copy(oldMessage = twitchNotificationState.channel.getMessage(
                                messageId))
                            )
                )
            }
            config.save()
        }

        val oldMessage = twitchNotifSetting.oldMessage?.asMessage()
        if (streamData != null) {
            logger.debug { "stream data : $streamData" }
            // live
            if (oldMessage != null) {
                val containsMention = oldMessage.content.contains("""<@&\d+>""".toRegex())
                if (containsMention) {
                    // was online, editing message

                    // TODO: check title, timestamp and game_name, only edit if different
                    val oldEmbed = oldMessage.embeds.firstOrNull()
                    val messageContent = "<https://twitch.tv/${usersData.login}> \n ${twitchNotifSetting.role.mention}"
                    val editMessage = when {
                        oldEmbed == null -> true
                        oldMessage.content != messageContent -> true
                        oldEmbed.title != streamData.title -> true
                        oldEmbed.footer?.text != streamData.game_name -> true
                        oldEmbed.timestamp != streamData.started_at -> true
                        else -> false
                    }
                    if (editMessage) {
                        val messageId = kord.rest.webhook.editWebhookMessage(
                            webhook.id,
                            webhook.token!!,
                            oldMessage.id
                        ) {
                            content = messageContent
                            embed {
                                buildEmbed(usersData, streamData, gameData)
                            }
                        }.id
                        updateMessageId(messageId)
                    }
                    return
                }
            }
            // was offline, creating new message and deleting old message
            val messageId = webhook.execute(webhook.token!!) {
                username = usersData.display_name
                avatarUrl = usersData.profile_image_url
                content = "<https://twitch.tv/${usersData.login}> \n ${twitchNotifSetting.role.mention}"
                embed {
                    buildEmbed(usersData, streamData, gameData)
                }
            }.id
            updateMessageId(messageId)
            oldMessage?.delete()
        } else {
            // offline

            // check if it was online before
            val updateMessage = if (oldMessage != null) {
                oldMessage.content.contains("""<@&\d+>""".toRegex())
            } else {
                true
            }

            if (updateMessage) {
                val vod = httpClient.getLastVOD(token, usersData.id)
                val message = "<https://twitch.tv/${usersData.login}>\n" +
                        if (vod != null) {
                            """
                            <${vod.url}>
                            **${vod.title}**
                            ${channelInfo.game_name}
                            """.trimIndent()
                        } else ""
                val messageId = if (oldMessage != null) {
                    kord.rest.webhook.editWebhookMessage(webhook.id, webhook.token!!, oldMessage.id) {
                        content = message
                        embeds = mutableListOf()
                    }.id
                } else {
                    webhook.execute(webhook.token!!) {
                        username = usersData.display_name
                        avatarUrl = usersData.profile_image_url
                        content = message
                    }.id
                }
                updateMessageId(messageId)
            }

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
                icon =
                    gameData.box_art_url.replace("{height}", "16").replace("{width}", "16")
            }
        } else {
            footer {
                text = streamData.game_name
            }
        }
    }

    private suspend fun checkTwitchStreamers(
        guildBehavior: GuildBehavior,
        state: BotState,
        token: Token,
        userDataMap: Map<String, TwitchUserData>,
        streamDataMap: Map<String, StreamData>,
        gameDataMap: Map<String, TwitchGameData>,
        channelInfoMap: Map<String, TwitchChannelInfo>,
    ) {
        state.twitchNotifications.forEach { (_, twitchNotifSetting) ->
            val userData = userDataMap[twitchNotifSetting.twitchUserName.lowercase()] ?: return@forEach
            val channelInfo = channelInfoMap[userData.login.lowercase()] ?: return@forEach
            val streamData = streamDataMap[userData.login.lowercase()]
            val gameData = gameDataMap[streamData?.game_name?.lowercase()]
            updateTwitchNotificationMessage(guildBehavior,
                twitchNotifSetting,
                token,
                userData,
                channelInfo,
                streamData,
                gameData)
        }
    }

    private suspend fun checkStreams(guild: Guild) = coroutineScope {
        logger.info { "checking twitch status for '${guild.name}'" }
        val state = config[guild] ?: error("failed to load state")

        val configStates = listOf(state)

        val token = httpClient.getToken() ?: return@coroutineScope
        val streamDataMap = httpClient.getStreams(
            token,
            configStates.flatMap {
                state.twitchNotifications.values.map(TwitchNotificationState::twitchUserName)
            }.distinct()
        ) ?: return@coroutineScope
        val userDataMap = httpClient.getUsers(
            token,
            configStates.flatMap {
                state.twitchNotifications.values.map(TwitchNotificationState::twitchUserName)
            }.distinct()
        ) ?: return@coroutineScope
        val gameDataMap = httpClient.getGames(
            token,
            streamDataMap.values.map { it.game_name }
        ) ?: return@coroutineScope
        val channelInfoMap = httpClient.getChannelInfo(
            token,
            userDataMap.values.map { it.id }
        ) ?: return@coroutineScope
        configStates.forEach { state ->
            launch {
                checkTwitchStreamers(state.guildBehavior,
                    state,
                    token,
                    userDataMap,
                    streamDataMap,
                    gameDataMap,
                    channelInfoMap)
            }
        }
    }

    private fun startBackgroundJob(guild: Guild): Job = kord.launch {
        while (true) {
            delay(15_000)

            checkStreams(guild)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun HttpClient.getToken(): Token? {
        token?.let { token ->
            if((Clock.System.now() + Duration.minutes(1)) < token.epiration) {
                logger.debug { "reusing token" }
                return token.token
            }
        }
        if (clientId == null || clientSecret == null) return null
        logger.debug { "getting new token" }
        return post<Token>(urlString = "https://id.twitch.tv/oauth2/token") {
            parameter("client_id", clientId)
            parameter("client_secret", clientSecret)
            parameter("grant_type", "client_credentials")
        }.also {
            token = TokenHolder(it).also {
                logger.info { "new token: ${it.token}" }
                logger.info { "expiration: ${it.epiration}" }
            }
        }
    }

    private suspend fun HttpClient.getStreams(token: Token, user_logins: List<String>): Map<String, StreamData>? {
//        val token = getToken()
        if (clientId == null || clientSecret == null) return null
        val chunkedList = user_logins.chunked(100)
        return chunkedList.flatMap { chunk ->
            get<JsonObject>(urlString = "$twitchApi/streams") {
                chunk.forEach {
                    parameter("user_login", it)
                }
                header("Client-ID", clientId)
                header("Authorization", "Bearer ${token.access_token}")
            }.parseData(StreamData.serializer())
        }.associateBy { it.user_name.lowercase() }
    }


    private suspend fun HttpClient.getUsers(token: Token, logins: List<String>): Map<String, TwitchUserData>? {
//        val token = getToken()
        if (clientId == null || clientSecret == null) return null
        val chunkedList = logins.chunked(100)
        return chunkedList.flatMap { chunk ->
            get<JsonObject>(urlString = "$twitchApi/users") {
                chunk.forEach {
                    parameter("login", it)
                }
                header("Client-ID", clientId)
                header("Authorization", "Bearer ${token.access_token}")
            }.parseData(TwitchUserData.serializer())
        }.associateBy { it.login.lowercase() }

    }

    private suspend fun HttpClient.getLastVOD(token: Token, userId: String): TwitchVideoData? {
//        val token = getToken()
        if (clientId == null || clientSecret == null) return null
        return get<JsonObject>(urlString = "$twitchApi/videos") {
            parameter("user_id", userId)
            parameter("last", "1")
            header("Client-ID", clientId)
            header("Authorization", "Bearer ${token.access_token}")
        }.parseData(TwitchVideoData.serializer()).firstOrNull()
    }

    private suspend fun HttpClient.getGames(token: Token, gameNames: List<String>): Map<String, TwitchGameData>? {
//        val token = getToken()
        if (clientId == null || clientSecret == null) return null
        val chunkedList = gameNames.chunked(100)
        return chunkedList.flatMap { chunk ->
            get<JsonObject>(urlString = "$twitchApi/games") {
                chunk.forEach {
                    parameter("name", it)
                }
                header("Client-ID", clientId)
                header("Authorization", "Bearer ${token.access_token}")
            }.parseData(TwitchGameData.serializer())
        }.associateBy { it.name.lowercase() }
    }

    private suspend fun HttpClient.getChannelInfo(
        token: Token,
        broadcasterIds: List<String>,
    ): Map<String, TwitchChannelInfo>? {
//        val token = getToken()
        if (clientId == null || clientSecret == null) return null
        val chunkedList = broadcasterIds.chunked(100)
        return chunkedList.flatMap { chunk ->
            get<JsonObject>(urlString = "$twitchApi/channels") {
                chunk.forEach {
                    parameter("broadcaster_id", it)
                }
                header("Client-ID", clientId)
                header("Authorization", "Bearer ${token.access_token}")
            }.parseData(TwitchChannelInfo.serializer())
        }.associateBy { it.broadcaster_login.lowercase() }
    }

    private fun <T> JsonObject.parseData(serializer: KSerializer<T>, key: String = "data"): List<T> {
        if(this["error"] != null) {
            logger.trace { "received: ${json.encodeToString(JsonObject.serializer(), this)}" }
            error(this["message"]!!.jsonPrimitive.content)
        }
        return json.decodeFromJsonElement(
            ListSerializer(serializer),
            this[key] as? JsonArray ?: return emptyList()
        )
    }

}

@Serializable
private data class Token(
    val access_token: String,
    val expires_in: Long,
    val token_type: String,
)

@Serializable
private data class StreamData(
    val id: String,
    val user_id: String,
    val user_name: String,
    val game_id: String,
    val game_name: String,
    val type: String,
    val title: String,
    val viewer_count: Int,
    val started_at: Instant,
    val language: String,
    val thumbnail_url: String,
    val tag_ids: List<String>,
    val is_mature: Boolean,
)

@Serializable
private data class TwitchUserData(
    val id: String,
    val login: String,
    val display_name: String,
    val description: String,
    val profile_image_url: String,
    val offline_image_url: String,
    val view_count: UInt,
    val created_at: String,
)

@Serializable
private data class TwitchVideoData(
    val id: String,
    val stream_id: String,
    val user_id: String,
    val user_name: String,
    val title: String,
    val description: String,
    val created_at: Instant,
    val published_at: Instant,
    val url: String,
    val thumbnail_url: String,
    val viewable: String,
    val view_count: UInt,
    val language: String,
    val type: String,
    val duration: String,
)

@Serializable
private data class TwitchGameData(
    val box_art_url: String,
    val id: String,
    val name: String,
)

@Serializable
private data class TwitchChannelInfo(
    val broadcaster_id: String,
    val broadcaster_login: String,
    val broadcaster_name: String,
    val broadcaster_language: String,
    val game_id: String,
    val game_name: String,
    val title: String,
    val delay: UInt,
)
