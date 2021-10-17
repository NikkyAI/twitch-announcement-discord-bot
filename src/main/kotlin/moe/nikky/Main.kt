package moe.nikky

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.i18n.SupportedLocales
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import io.klogging.Level
import io.klogging.config.DEFAULT_CONSOLE
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

private val logger = logger("Main")
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
        kloggingMinLevel(Level.DEBUG)
        if(dockerLogging) {
            sink("stdout", DOCKER_RENDERER, STDOUT)
        } else {
//            sink("stdout", CUSTOM_RENDERER, STDOUT)
            sink("stdout", CUSTOM_RENDERER_ANSI, STDOUT)
        }
        sink("file_latest", CUSTOM_RENDERER, logFile(File("logs/latest.log")))
        val timestamp = SimpleDateFormat("yyyy-dd-MM-HH-mm-ss").format(Date())
        sink("file", CUSTOM_RENDERER, logFile(File("logs/log-$timestamp.log")))
        val seqServer = envOrNull("SEQ_SERVER") //?: "http://localhost:5341"
        if(seqServer != null) {
            sink("seq", seq(seqServer))
        }
        logging {
            fromMinLevel(Level.DEBUG) {
                toSink("stdout")
                if(seqServer != null) {
                    toSink("seq")
                }
                toSink("file_latest")
                toSink("file")
            }
        }
    }
    val token = env("BOT_TOKEN")

    val withStacktrace = measureTime {
        logger.infoF { "defaultGuidId: $TEST_GUILD_ID" }
    }
    val normal = measureTime {
        logger.info { "defaultGuidId: $TEST_GUILD_ID" }
    }
    val difference = withStacktrace-normal
    logger.debug("normal: {normal} withStacktrace: {withStacktrace}, difference: {difference}", normal, withStacktrace , difference)


    val bot = ExtensibleBot(token) {
        intents {
            +Intent.GuildIntegrations
            +Intent.GuildWebhooks
        }
        i18n {
            defaultLocale = SupportedLocales.ENGLISH
        }
//        chatCommands {
//            enabled = true
//            defaultPrefix = "!"
//        }
        applicationCommands {
            defaultGuild = TEST_GUILD_ID
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
        }
        hooks {
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
