package moe.nikky

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import moe.nikky.TwitchApi.getSchedule
import moe.nikky.TwitchApi.getToken

private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }
    CurlUserAgent()
}
suspend fun main() {
    setupLogging()

    val token = httpClient.getToken()
    val segments = httpClient.getSchedule(
        token = token,
        broadcaster_id = "23507712"
    ).take(1000).toList()

    println(segments.size)
    println(segments.distinct().size)
}