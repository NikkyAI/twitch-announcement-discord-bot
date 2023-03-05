package moe.nikky.twitch

import com.kotlindiscord.kord.extensions.utils.envOrNull
import io.klogging.Klogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import moe.nikky.debugF
import moe.nikky.errorF
import moe.nikky.infoF
import moe.nikky.traceF
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object TwitchApi : Klogging {
    private const val twitchApi = "https://api.twitch.tv/helix"
    private val clientId = envOrNull("TWITCH_CLIENT_ID")
    private val clientSecret = envOrNull("TWITCH_CLIENT_SECRET")

    private var token: Token? = null

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun HttpClient.getToken(): Token {
        token?.let { token ->
            if ((Clock.System.now() + 10.minutes) < token.tokenExpiration) {
                logger.traceF { "reusing token" }
                return token
            }
        }
        if (clientId == null || clientSecret == null) error("clientId or clientSecret are unset")
        logger.infoF { "getting new token" }
        return post(urlString = "https://id.twitch.tv/oauth2/token") {
            parameter("client_id", clientId)
            parameter("client_secret", clientSecret)
            parameter("grant_type", "client_credentials")
        }.body<TwitchToken>().let {
            Token(
                clientId,
                Clock.System.now() + it.expiresIn.seconds,
                it.accessToken,
                it.expiresIn,
                it.tokenType,
            )
        }.also {
            token = it
            logger.infoF { "new token: $token" }
        }
    }

    suspend fun HttpClient.getStreams(
        userLogins: List<String>,
        token: Token? = null,
    ): Map<String, StreamData> {
        val chunkedList = userLogins.chunked(100)
        return chunkedList.flatMap { chunk ->
            get(urlString = "$twitchApi/streams") {
                chunk.forEach {
                    parameter("user_login", it)
                }
                authHeaders(token ?: getToken())
            }.body<JsonObject>().parseData(StreamData.serializer())
        }.associateBy { it.userName.lowercase() }
    }

    suspend fun HttpClient.getUsers(
        logins: List<String>,
        token: Token?,
    ): Map<String, TwitchUserData> {
        val chunkedList = logins.chunked(100)
        return chunkedList.flatMap { chunk ->
            get(urlString = "$twitchApi/users") {
                chunk.forEach {
                    parameter("login", it)
                }
                authHeaders(token ?: getToken())
            }.body<JsonObject>().parseData(TwitchUserData.serializer())
        }.associateBy { it.login.lowercase() }
    }

    suspend fun HttpClient.getLastVOD(
        userId: String,
        token: Token?,
    ): TwitchVideoData? {
        return try {
            get(urlString = "$twitchApi/videos") {
                parameter("user_id", userId)
                parameter("last", "1")
                authHeaders(token ?: getToken())
            }.body<JsonObject>().parseData(TwitchVideoData.serializer()).firstOrNull()
        } catch (e: ServerResponseException) {
            logger.errorF { e.message }
            null
        }
    }

    suspend fun HttpClient.getGames(
        gameNames: List<String>,
        token: Token?,
    ): Map<String, TwitchGameData> {
        val chunkedList = gameNames.chunked(100)
        return chunkedList.flatMap { chunk ->
            get(urlString = "$twitchApi/games") {
                chunk.forEach {
                    parameter("name", it)
                }
                authHeaders(token ?: getToken())
            }.body<JsonObject>().parseData(TwitchGameData.serializer())
        }.associateBy { it.name.lowercase() }
    }

    suspend fun HttpClient.getChannelInfo(
        broadcasterIds: List<String>,
        token: Token?,
    ): Map<String, TwitchChannelInfo> {
        val chunkedList = broadcasterIds.chunked(100)
        return chunkedList.flatMap { chunk ->
            get(urlString = "$twitchApi/channels") {
                chunk.forEach {
                    parameter("broadcaster_id", it)
                }
                authHeaders(token ?: getToken())
            }.body<JsonObject>()
                .parseData(TwitchChannelInfo.serializer())
        }.associateBy { it.broadcasterLogin.lowercase() }
    }

    @OptIn(FlowPreview::class)
    suspend fun HttpClient.getSchedule(
        broadcasterId: String,
        pageSize: Int = 20,
        token: Token?,
    ): Flow<TwitchScheduleSegment> {
        require(pageSize in 1..25) { "pageSize must be positive and not higher than 25" }
        val segments = requestPages { cursor ->
            try {
                get("$twitchApi/schedule") {
                    authHeaders(token ?: getToken())

                    parameter("broadcaster_id", broadcasterId)

                    parameter("first", pageSize)
                    parameter("after", cursor)
                }.body<PagedResponse>()
            } catch (e: ClientRequestException) {
                logger.errorF { e.message }
                null
            }
        }
            .map {
                it?.parseData(TwitchSchedule.serializer())
            }
            .distinctUntilChanged()
            .flatMapConcat { schedule ->
                schedule?.segments?.also {
                    logger.debugF { "${it.size} segments in response" }
                }?.map {
                    if (schedule.vacation != null) {
                        if (it.startTime > schedule.vacation.startTime && it.startTime < schedule.vacation.endTime) {
                            it.copy(vacationCancelledUntil = schedule.vacation.endTime)
                        } else if (it.endTime > schedule.vacation.startTime && it.endTime < schedule.vacation.endTime) {
                            it.copy(vacationCancelledUntil = schedule.vacation.endTime)
                        } else {
                            it
                        }
                    } else {
                        it
                    }
                }?.asFlow() ?: emptyFlow()
            }

        return segments
    }

    private suspend fun requestPages(
        doRequest: suspend (cursor: String?) -> PagedResponse?,
    ): Flow<PagedResponse?> {
        return flow {
            var cursor: String? = null
            do {
                val response = doRequest(cursor)
                val newCursor = response?.pagination?.cursor

                cursor = if (newCursor != null && newCursor != cursor) {
                    newCursor
                } else {
                    null
                }
                response?.let { r ->
                    logger.debugF { "getting pagedResponse" }
                    logger.debugF { "data: ${r.data}" }
                    logger.debugF { "pagination: ${r.pagination}" }
                }

                emit(response)

                delay(100)
            } while (cursor != null)
        }
    }

    private suspend fun <T> PagedResponse.parseData(serializer: KSerializer<T>): T? {
        if (error != null) {
            logger.errorF { "received: ${json.encodeToString(PagedResponse.serializer(), this@parseData)}" }
            error(error["message"]!!.jsonPrimitive.content)
        }
        val data = when (val value = data) {
            is JsonNull -> {
                logger.errorF { "data is null in the twitch response" }
                return null
            }
            is JsonArray -> value
            is JsonObject -> value
            else -> {
                logger.errorF { "twitch data was not a array" }
                logger.errorF { json.encodeToString(JsonElement.serializer(), value) }
                return null
            }
        }
        return try {
            json.decodeFromJsonElement(
                serializer,
                data
            )
        } catch (e: SerializationException) {
            logger.errorF(e) {
                "twitch data failed to parse: \n${
                    json.encodeToString(JsonElement.serializer(), data)
                }"
            }
            return null
        }
    }

    private suspend fun <T> JsonObject.parseData(serializer: KSerializer<T>, key: String = "data"): List<T> {
        if (this["error"] != null) {
            logger.errorF { "received: ${json.encodeToString(JsonObject.serializer(), this@parseData)}" }
            error(this["message"]!!.jsonPrimitive.content)
        }
        val array = when (val value = this[key]) {
            is JsonArray -> value
            null -> {
                logger.errorF { "key $key is missing from the twitch response" }
                return emptyList()
            }
            else -> {
                logger.errorF { "twitch data was not a array" }
                logger.errorF { json.encodeToString(JsonElement.serializer(), value) }
                return emptyList()
            }
        }
        return try {
            json.decodeFromJsonElement(
                ListSerializer(serializer),
                array
            )
        } catch (e: SerializationException) {
            logger.errorF(e) {
                "twitch data failed to parse key $key: \n${
                    json.encodeToString(
                        JsonElement.serializer(),
                        array
                    )
                }"
            }
            return emptyList()
        }
    }


}

fun HttpRequestBuilder.authHeaders(token: Token) {
    header("Client-ID", token.clientId)
    header("Authorization", "Bearer ${token.accessToken}")
}

data class Token(
    val clientId: String,
    val tokenExpiration: Instant,
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String,
)

@Serializable
data class TwitchToken(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String,
)

@Serializable
data class StreamData(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    @SerialName("game_id")
    val gameId: String,
    @SerialName("game_name")
    val gameName: String,
    val type: String,
    val title: String,
    @SerialName("viewer_count")
    val viewerCount: Int,
    @SerialName("started_at")
    val startedAt: Instant,
    val language: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String,
    @SerialName("tag_ids")
    val tagIds: List<String>?,
    @SerialName("is_mature")
    val isMature: Boolean,
)

@Serializable
data class TwitchUserData(
    val id: String,
    val login: String,
    @SerialName("display_name")
    val displayName: String,
    val description: String,
    @SerialName("profile_image_url")
    val profileImageUrl: String,
    @SerialName("offline_image_url")
    val offlineImageUrl: String,
    @SerialName("view_count")
    val viewCount: UInt,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class TwitchVideoData(
    val id: String,
    @SerialName("stream_id")
    val streamId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    val title: String,
    val description: String,
    @SerialName("created_at")
    val createdAt: Instant,
    @SerialName("published_at")
    val publishedAt: Instant,
    val url: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String,
    val viewable: String,
    @SerialName("view_count")
    val viewCount: UInt,
    val language: String,
    val type: String,
    val duration: String,
)

@Serializable
data class TwitchGameData(
    @SerialName("box_art_url")
    val boxArtUrl: String,
    val id: String,
    val name: String,
)

@Serializable
data class TwitchChannelInfo(
    @SerialName("broadcaster_id")
    val broadcasterId: String,
    @SerialName("broadcaster_login")
    val broadcasterLogin: String,
    @SerialName("broadcaster_name")
    val broadcasterName: String,
    @SerialName("broadcaster_language")
    val broadcasterLanguage: String,
    @SerialName("game_id")
    val gameId: String,
    @SerialName("game_name")
    val gameName: String,
    val title: String,
    val delay: UInt,
)

@Serializable
data class PagedResponse(
    val data: JsonElement,
    val error: JsonObject? = null,
    val pagination: TwitchPagination,
)

@Serializable
data class TwitchPagination(
    val cursor: String? = null,
)

@Serializable
data class TwitchSchedule(
    val segments: List<TwitchScheduleSegment>,
    @SerialName("broadcaster_id")
    val broadcasterId: String,
    @SerialName("broadcaster_name")
    val broadcasterName: String,
    @SerialName("broadcaster_login")
    val broadcasterLogin: String,
    val vacation: TwitchScheduleVacation? = null,
)

@Serializable
data class TwitchScheduleVacation(
    @SerialName("start_time")
    val startTime: Instant,
    @SerialName("end_time")
    val endTime: Instant,
)

@Serializable
data class TwitchScheduleSegment(
    val id: String,
    @SerialName("start_time")
    val startTime: Instant,
    @SerialName("end_time")
    val endTime: Instant,
    val title: String,
    @SerialName("canceled_until")
    val canceledUntil: Instant? = null,
    @Transient
    val vacationCancelledUntil: Instant? = null,
    val category: TwitchScheduleSegmentCategory?,
    @SerialName("is_recurring")
    val recurring: Boolean,
)

@Serializable
data class TwitchScheduleSegmentCategory(
    val id: String,
    val name: String,
)