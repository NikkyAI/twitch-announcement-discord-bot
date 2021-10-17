package moe.nikky

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.*
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.respond
import com.kotlindiscord.kord.extensions.utils.translate
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.execute
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.create.embed
import io.klogging.Klogging
import io.klogging.context.logContext
import io.klogging.logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.*

class TestExtension : Extension(), Klogging {
    override val name: String = "Test extension"

    override suspend fun setup() {
        chatCommand(::SlapArgs) {
            name = "slap"
            description = "Get slapped!"

            // Make sure the command didn't come from a webhook - the command will only run or be
            // shown in the help command when this returns `true`, and it'll be ignored otherwise
            check {
                failIf { this.event.member == null }
            }
//            requireBotPermissions(Permission.ViewChannel)

            action {  // Now `arguments` here will contain an instance of our arguments class
                // Because of the DslMarker annotation KordEx uses, we need to grab Kord explicitly
                val kord = this@TestExtension.kord

                // Don't slap ourselves, slap the person that ran the command!
                val realTarget = if (arguments.target?.id == kord.selfId) {
                    user!!
                } else {
                    arguments.target ?: user!!
                }

                this.message.respond {
                    content = "*slaps ${realTarget.mention} with ${arguments.weapon.message}*"
                }
            }
        }
        publicSlashCommand(::SlapArgs) {
            name = "slap"
            description = "Get slapped!"

            action {
                // Because of the DslMarker annotation KordEx uses, we need to grab Kord explicitly
                val kord = this@TestExtension.kord

                // Don't slap ourselves, slap the person that ran the command!
                val realTarget = if (arguments.target?.id == kord.selfId) {
                    user
                } else {
                    arguments.target ?: user
                }

                this.respond {
                    content = "*slaps ${realTarget.mention} with ${arguments.weapon.message}*"
                }
            }
        }
        publicSlashCommand(::BonkArgs) {
            name = "bonk"
            description = "bonk"

            action {
                // Because of the DslMarker annotation KordEx uses, we need to grab Kord explicitly
                val kord = this@TestExtension.kord

                // Don't bonk ourselves, slap the person that ran the command!
                val realTarget = if (arguments.target.id == kord.selfId) {
                    user
                } else {
                    arguments.target
                }

                respond {
                    content = "*bonks ${realTarget.mention} on the head*"
                }
            }
        }

        event<MessageCreateEvent> {  // Listen for message creation events
            action {  // Code to run when a message creation happens
                if (event.message.content.equals("hello", ignoreCase = true)) {
                    event.message.respond("Hello!")
                }
            }
        }
        event<GuildCreateEvent> {
            action {
                withLogContext(event, event.guild) { guild ->
                    if (event.guild.id != TEST_GUILD_ID) return@withLogContext
                    logger.infoF { "ready event on ${guild.name}" }
                }
            }
        }
    }

    inner class SlapArgs : Arguments() {
        // A single user argument, required for the command to be able to run
        val target by optionalUser("target", description = "Person you want to slap")
        private val nullableWeapon by optionalEnumChoice<Weapon>(
            displayName = "weapon",
            description = "What you want to slap with",
            typeName = "weapon"
        )
        val weapon: Weapon get() = nullableWeapon ?: Weapon.Trout
    }

    inner class BonkArgs : Arguments() {
        // A single user argument, required for the command to be able to run
        val target by user("target", description = "Person that needs a bonk")
    }

    enum class Weapon(override val readableName: String, val message: String) : ChoiceEnum {
        Trout("trout", "their large, smelly trout"),
        Stick("stick", "a stick"),
    }
}
