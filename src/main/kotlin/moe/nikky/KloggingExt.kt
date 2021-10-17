package moe.nikky

import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.Event
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.interaction.InteractionCreateEvent
import io.klogging.Klogger
import io.klogging.Level
import io.klogging.context.logContext
import io.klogging.events.LogEvent
import io.klogging.logger
import io.klogging.rendering.*
import io.klogging.sending.SendString
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.locks.ReentrantLock


val DOCKER_RENDERER: RenderString = { e: LogEvent ->
    val loggerOrFile = e.items["file"]?.let { ".($it)" } ?: e.logger
    val message = "${e.level.colour5} $loggerOrFile : ${e.evalTemplate()}"
    val cleanedItems = e.items - "file"
    val maybeItems = if (cleanedItems.isNotEmpty()) " : $cleanedItems" else ""
    val maybeStackTrace = if (e.stackTrace != null) "\n${e.stackTrace}" else ""
    message + maybeItems + maybeStackTrace
}

val CUSTOM_RENDERER: RenderString = { e: LogEvent ->
    val loggerOrFile = e.items["file"]?.let { ".($it)" } ?: e.logger
    val time = e.timestamp.localString.padEnd(29, '0')
    val message = "$time ${e.level} $loggerOrFile : ${e.evalTemplate()}"
    val cleanedItems = e.items - "file"
    val maybeItems = if (cleanedItems.isNotEmpty()) " : $cleanedItems" else ""
    val maybeStackTrace = if (e.stackTrace != null) "\n${e.stackTrace}" else ""
    message + maybeItems + maybeStackTrace
}
val CUSTOM_RENDERER_ANSI: RenderString = { e: LogEvent ->
    val loggerOrFile = e.items["file"]?.let { ".($it)" } ?: e.logger
    val time = e.timestamp.localString.padEnd(29, '0')
    val message = "$time ${e.level.colour5} $loggerOrFile : ${e.evalTemplate()}"
    val cleanedItems = e.items - "file"
    val maybeItems = if (cleanedItems.isNotEmpty()) " : $cleanedItems" else ""
    val maybeStackTrace = if (e.stackTrace != null) "\n${e.stackTrace}" else ""
    message + maybeItems + maybeStackTrace
}

fun logFile(file: File, append: Boolean = false): SendString {
    file.parentFile.mkdirs()
    if(!file.exists()) file.createNewFile()
    val sink = file.sink(append = append).buffer()

    return { line ->
        sink.writeUtf8(line + "\n")
        delay(1)
    }
}

//fun logFile(file: File): SendString {
//    file.parentFile.mkdirs()
//    file.delete()
//    file.createNewFile()
//    val writeChannel = file.writeChannel()
//    return { line ->
//        writeChannel.writeStringUtf8(line + "\n")
//        delay(1)
//    }
//}

private val logger = logger("moe.nikky.KloggingExt")
suspend fun <E : Event, T> Extension.withLogContext(
    event: E,
    guildBehavior: GuildBehavior,
    block: suspend CoroutineScope.(Guild) -> T,
): T {
    val guild = guildBehavior.asGuild()
    val items = mutableListOf<Pair<String, Any?>>()
    if(event is InteractionCreateEvent) {
        items += "channel" to (event.interaction.channel.asChannel() as? GuildChannel)?.name
    }
    items += listOf(
        "guild" to "'${guild.name}'",
        "event" to event::class.simpleName,
        "extension" to name,
    )
    return withContext(
        logContext(*items.toTypedArray())
    ) {
        logger.infoF { "triggered event ${event::class.simpleName}" }
        block(guild)
    }
}
