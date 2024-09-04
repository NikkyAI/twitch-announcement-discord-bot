package moe.nikky

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.i18n.SupportedLocales
import com.kotlindiscord.kord.extensions.modules.extra.phishing.extPhishing
import com.kotlindiscord.kord.extensions.storage.DataAdapter
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.PrivilegedIntent
import io.klogging.Level
import io.klogging.config.LoggingConfig
import io.klogging.config.loggingConfiguration
import io.klogging.config.seq
import io.klogging.logger
import io.klogging.sending.STDOUT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import moe.nikky.twitch.TwitchExtension
import org.koin.dsl.module
import java.io.File
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Date

private val logger = logger("moe.nikky.Main")
val TEST_GUILD_ID = envOrNull("TEST_GUILD")?.let { Snowflake(it) }

val dockerLogging = envOrNull("DOCKER_LOGGING") == "true"

@ExperimentalCoroutinesApi
@PrivilegedIntent
suspend fun main() {
    DebugProbes.install()
    DebugProbes.sanitizeStackTraces = true

    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")

    setupLogging()

    val token = env("BOT_TOKEN")
    val fileLogger = logger

    if (!dockerLogging) {
        logger.warnF { "WARN" }
        logger.errorF { "ERROR" }
        logger.fatalF { "FATAL" }
    }

    val bot = ExtensibleBot(token) {
        i18n {
            defaultLocale = SupportedLocales.ENGLISH
        }
        applicationCommands {
            defaultGuild = TEST_GUILD_ID
            logger.infoF { "test guild: ${defaultGuild}" }
        }
        extensions {
            add(::ConfigurationExtension)
            add(::BotInfoExtension)
            add(::DiceExtension)
            add(::RoleManagementExtension)
            add(::TwitchExtension)
            add(::LocalTimeExtension)
//            add(::SchedulingExtension)
            if (TEST_GUILD_ID != null) {
                add(::TestExtension)
            }
            help {
                enableBundledExtension = true
                deleteInvocationOnPaginatorTimeout = true
                deletePaginatorOnTimeout = true
                pingInReply = true
            }
            extPhishing {
//                appName = "Yuno"
            }
//            extPluralKit()
        }
        presence {
            status = PresenceStatus.Idle
            afk = true
            state = "booting"
        }
        hooks {
            kordShutdownHook = true
            afterKoinSetup {
                registerKoinModules()
            }
        }
    }

    logger.infoF { "bot starting" }
    bot.start()
}

suspend fun setupLogging() {
    val latestFile = LogFile(File("logs/latest.log"))
    val latestTrace = LogFile(File("logs/latest-trace.log"))
    val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(Date())
    val timestamped = LogFile(File("logs/log-$timestamp.log"))
    loggingConfiguration {
        if (dockerLogging) {
            sink("stdout", DOCKER_RENDERER, STDOUT)
        } else {
            sink("stdout", CUSTOM_RENDERER_ANSI, STDOUT)
        }
        sink("file_latest", CUSTOM_RENDERER, latestFile)
        sink("file_latest_trace", CUSTOM_RENDERER, latestTrace)
        sink("file", CUSTOM_RENDERER, timestamped)
        val seqServer = envOrNull("SEQ_SERVER") //?: "http://localhost:5341"
        if (seqServer != null) {
            sink("seq", seq(seqServer))
        }
        fun LoggingConfig.applyFromMinLevel(level: Level) {
            fromMinLevel(level) {
                toSink("stdout")
                if (seqServer != null) {
                    toSink("seq")
                }
                toSink("file_latest")
                toSink("file")
            }
        }
        logging {
            fromLoggerBase("moe.nikky", stopOnMatch = true)
            applyFromMinLevel(Level.DEBUG)
            fromMinLevel(Level.TRACE) {
                toSink("file_latest_trace")
            }
        }
        logging {
            fromLoggerBase("dev.kord.rest", stopOnMatch = true)
            applyFromMinLevel(Level.INFO)
        }
        logging {
            //TODO: fix logger matcher
            exactLogger("\\Q[R]:[KTOR]:[ExclusionRequestRateLimiter]\\E", stopOnMatch = true)
            applyFromMinLevel(Level.INFO)
        }
        logging {
            fromLoggerBase("dev.kord", stopOnMatch = true)
            applyFromMinLevel(Level.INFO)
        }
        logging {
            fromLoggerBase("com.kotlindiscord.kord.extensions.modules.extra.phishing", stopOnMatch = true)
            applyFromMinLevel(Level.WARN)
        }
        logging {
            fromLoggerBase("com.kotlindiscord.kord.extensions.checks", stopOnMatch = true)
            applyFromMinLevel(Level.WARN)
        }
        logging {
            fromLoggerBase("com.kotlindiscord.kord.extensions", stopOnMatch = true)
            applyFromMinLevel(Level.TRACE)
        }
        logging {
            applyFromMinLevel(Level.INFO)
        }
    }
}

private fun registerKoinModules() {
    getKoin().loadModules(
        listOf(
            module {
                single<DataAdapter<*>> { Json5DataAdapter() }
            }
        )
    )
}
