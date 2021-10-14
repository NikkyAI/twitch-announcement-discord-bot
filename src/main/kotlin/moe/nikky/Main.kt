package moe.nikky

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.i18n.SupportedLocales
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.envOrNull
import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import mu.KotlinLogging
import org.koin.dsl.module
import java.security.Security

private val logger = KotlinLogging.logger {}
val TEST_GUILD_ID = envOrNull("TEST_GUILD")?.let { Snowflake(it) }

@Deprecated("remove")
val commandPrefix: String = if(TEST_GUILD_ID != null) "" else ""

@ExperimentalCoroutinesApi
@PrivilegedIntent
suspend fun main() {
    DebugProbes.install()
    DebugProbes.sanitizeStackTraces = true

    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")

    val token = env("BOT_TOKEN")

    logger.info { "defaultGuidId: $TEST_GUILD_ID" }

    val bot = ExtensibleBot(token) {
        intents {
            +Intent.GuildIntegrations
            +Intent.GuildWebhooks
        }
        i18n {
            defaultLocale = SupportedLocales.ENGLISH
        }
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

    logger.info { "bot starting" }
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
