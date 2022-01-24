package moe.nikky

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.*
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.profileLink
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.Image
import io.klogging.Klogging

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

        ephemeralMessageCommand {
            name = "testmessagecmd"

            action {
                val targetMessage = event.interaction.getTarget()
                respond {
                    content = """message content: \n```${targetMessage.content}\n```"""
                }
            }
        }
        ephemeralUserCommand {
            name = "testusercmd"

            action {
                val targetUser = event.interaction.getTarget()
                respond {
                    content = """
                        |banner: ${targetUser.getBannerUrl(Image.Format.GIF)}
                        |profile link: <${targetUser.profileLink}>
                    """.trimMargin()
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
        val target by optionalUser {
            name = "target"
            description = "Person you want to slap"
        }
        private val nullableWeapon by optionalEnumChoice<Weapon> {
            name = "weapon"
            description = "What you want to slap with"
            typeName = "weapon"
        }
        val weapon: Weapon get() = nullableWeapon ?: Weapon.Trout
    }

    inner class BonkArgs : Arguments() {
        // A single user argument, required for the command to be able to run
        val target by user {
            name = "target"
            description = "Person that needs a bonk"
        }
    }

    enum class Weapon(override val readableName: String, val message: String) : ChoiceEnum {
        Trout("trout", "their large, smelly trout"),
        Stick("stick", "a stick"),
    }
}
