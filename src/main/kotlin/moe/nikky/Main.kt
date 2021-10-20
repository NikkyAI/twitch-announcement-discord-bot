package moe.nikky

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.i18n.SupportedLocales
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.kord.common.entity.PresenceStatus
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import io.klogging.Level
import io.klogging.config.DEFAULT_CONSOLE
import io.klogging.config.LevelRange
import io.klogging.config.loggingConfiguration
import io.klogging.config.seq
import io.klogging.logger
import io.klogging.rendering.RENDER_ANSI
import io.klogging.rendering.RENDER_SIMPLE
import io.klogging.sending.STDOUT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.koin.dsl.module
import java.io.File
import java.security.Security
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private val logger = logger("moe.nikky.Main")
val TEST_GUILD_ID = envOrNull("TEST_GUILD")?.let { Snowflake(it) }

@Deprecated("remove")
val commandPrefix: String = if(TEST_GUILD_ID != null) "" else ""

val dockerLogging = envOrNull("DOCKER_LOGGING") == "true"

@OptIn(ExperimentalTime::class)
@ExperimentalCoroutinesApi
@PrivilegedIntent
suspend fun main() {
    DebugProbes.install()
    DebugProbes.sanitizeStackTraces = true

    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")


    loggingConfiguration {
        if(dockerLogging) {
            sink("stdout", DOCKER_RENDERER, STDOUT)
        } else {
            sink("stdout", CUSTOM_RENDERER_ANSI, STDOUT)
        }
        sink("file_latest", CUSTOM_RENDERER, logFile(File("logs/latest.log")))
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(Date())
        sink("file", CUSTOM_RENDERER, logFile(File("logs/log-$timestamp.log")))
        val seqServer = envOrNull("SEQ_SERVER") //?: "http://localhost:5341"
        if(seqServer != null) {
            sink("seq", seq(seqServer))
        }
        fun LevelRange.applySinks() {
            toSink("stdout")
            if(seqServer != null) {
                toSink("seq")
            }
            toSink("file_latest")
            toSink("file")
        }
        logging {
            fromLoggerBase("moe.nikky", stopOnMatch = true)
            fromMinLevel(Level.DEBUG) {
                applySinks()
            }
        }
        logging {
            fromLoggerBase("dev.kord.rest", stopOnMatch = true)
            fromMinLevel(Level.INFO) {
                applySinks()
            }
        }
        logging {
            exactLogger("\\Q[R]:[KTOR]:[ExclusionRequestRateLimiter]\\E", stopOnMatch = true)
            fromMinLevel(Level.INFO) {
                applySinks()
            }
        }
        logging {
            fromLoggerBase("dev.kord", stopOnMatch = true)
            fromMinLevel(Level.INFO) {
                applySinks()
            }
        }
        logging {
            fromLoggerBase("com.kotlindiscord.kord.extensions", stopOnMatch = true)
            fromMinLevel(Level.INFO) {
                applySinks()
            }
        }
        logging {
            fromMinLevel(Level.DEBUG) {
                applySinks()
            }
        }
    }
    val token = env("BOT_TOKEN")

    if(!dockerLogging) {
        logger.warnF { "WARN" }
        logger.errorF { "ERROR" }
        logger.fatalF { "FATAL" }
    }

    val bot = ExtensibleBot(token) {
//        intents {
//            +Intent.GuildWebhooks
//        }

        chatCommands {
            defaultPrefix = envOrNull("COMMAND_PREFIX") ?: ";"
            logger.info { "chat command default prefix: $defaultPrefix" }
            enabled = true
        }
        i18n {
            defaultLocale = SupportedLocales.ENGLISH
        }
        applicationCommands {
            defaultGuild = TEST_GUILD_ID
            logger.infoF { "test guild: ${defaultGuild?.asString}" }
        }
        extensions {
            add(::ConfigurationExtension)
            add(::BotInfoExtension)
            add(::DiceExtension)
            add(::RoleManagementExtension)
            add(::TwitchNotificationExtension)
            if(TEST_GUILD_ID != null) {
                add(::TestExtension)
            }
            help {
                enableBundledExtension = true
                deleteInvocationOnPaginatorTimeout = true
                deletePaginatorOnTimeout = true
                pingInReply = true
            }
        }
        presence {
            status = PresenceStatus.Idle
            afk = true
        }
        hooks {
            kordShutdownHook = true
            afterKoinSetup {
                registerKoinModules()
            }
//            beforeExtensionsAdded {
//
//            }
        }
    }

    logger.infoF { "bot starting" }
    bot.start()
}

private fun registerKoinModules() {
    getKoin().loadModules(
        listOf(
            module {
                single { ConfigurationService() }
            }
        )
    )
}
