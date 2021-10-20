package moe.nikky

import io.klogging.Klogger
import io.klogging.Level
import io.klogging.context.logContext
import kotlinx.coroutines.withContext

private val scanStacktrace = true

fun getLocation(debug: Boolean = false): String? {
    if (!scanStacktrace) return null
    return try {
        throw Exception()
    } catch (e: Exception) {
        val firstElement = e.stackTrace.first()
        e.stackTrace.firstOrNull { element ->
            element.fileName != firstElement.fileName
                    && element.lineNumber > 0
                    && element.className.startsWith("moe.nikky.")
        }?.let { element ->
            if (debug) {
                System.err.println(element)
                System.err.println(element.fileName)
                System.err.println(element.lineNumber)
            }
            "${element.fileName}:${element.lineNumber}"
        }.also {
            if (debug || it == null) e.printStackTrace()
        }
    }
}

suspend inline fun Klogger.infoF(template: String, vararg values: Any?) {
    logF(Level.INFO, template, values)
}

suspend inline fun Klogger.debugF(template: String, vararg values: Any?) {
    logF(Level.DEBUG, template, values)
}

suspend inline fun Klogger.traceF(template: String, vararg values: Any?) {
    logF(Level.TRACE, template, values)
}

suspend fun Klogger.logF(level: Level, template: String, vararg values: Any?) {
    if (!isLevelEnabled(level)) return
    val location = getLocation()
    if (location != null) {
        withContext(logContext("file" to location)) {
            log(level, template, values)
        }
    } else {
        log(level, template, values)
    }
}

suspend fun Klogger.fatalF(event: suspend Klogger.() -> Any?) {
    logF(Level.FATAL, event)
}

suspend fun Klogger.errorF(event: suspend Klogger.() -> Any?) {
    logF(Level.ERROR, event)
}

suspend fun Klogger.warnF(event: suspend Klogger.() -> Any?) {
    logF(Level.WARN, event)
}

suspend fun Klogger.infoF(event: suspend Klogger.() -> Any?) {
    logF(Level.INFO, event)
}

suspend fun Klogger.debugF(event: suspend Klogger.() -> Any?) {
    logF(Level.DEBUG, event)
}

suspend fun Klogger.traceF(event: suspend Klogger.() -> Any?) {
    logF(Level.TRACE, event)
}

suspend fun Klogger.logF(level: Level, event: suspend Klogger.() -> Any?) {
    if (!isLevelEnabled(level)) return
    val location = getLocation()
    if (location != null) {
        withContext(logContext("file" to location)) {
            log(level, event)
        }
    } else {
        log(level, event)
    }
}

suspend fun Klogger.errorF(e: Throwable, event: suspend Klogger.() -> Any?) {
    logF(Level.ERROR, e, event)
}

suspend fun Klogger.logF(level: Level, e: Throwable, event: suspend Klogger.() -> Any?) {
    if (!isLevelEnabled(level)) return
    val location = getLocation()
    if (location != null) {
        withContext(logContext("file" to location)) {
            log(level, e, event)
        }
    } else {
        log(level, e, event)
    }
}