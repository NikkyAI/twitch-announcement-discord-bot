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
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.util.*

class TestExtension : Extension() {
    override val name: String = "Test extension"

    private val logger = KotlinLogging.logger {}

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
                if(event.guild.id != TEST_GUILD_ID) return@action
                val guild = event.guild
                logger.info { "ready event on ${guild.name}" }

//                logger.info { "channels" }
//                guild.channels.collect {
//                    logger.info { "name:  ${it.name}" }
//                    logger.info { "type:  ${it.type}" }
//                    logger.info { "perms: " }
//                    it.permissionOverwrites.forEach { override ->
//                        guild.snowflakeToName(override.target).let {
//                            logger.info { "target:  $it" }
//                        }
//                        override.allowed.values.map { it.translate(Locale.ENGLISH) }.let {
//                            logger.info { "allowed: $it" }
//                        }
//                        override.denied.values.map { it.translate(Locale.ENGLISH) }.let {
//                            logger.info { "denied:  $it" }
//                        }
//                    }
//                    logger.info { "pos:   ${it.rawPosition}" }
//                    logger.info { "data:  ${it.data}" }
//                }
//                kord.rest.webhook.createWebhook(
//
//                )

//                //TODO: make sure role has correct settings
//                logger.info { "editing role" }
//                role.edit {
//                    name = "test-role"
//                    permissions = Permissions()
//                }
//
//                logger.info { "getting category" }
//                val category = guild.channels.filterIsInstance<Category>().firstOrNull { it.name == "Testing" }
//                    ?: guild.createCategory("Testing") {
//                        permissionOverwrites.addAll(
//                            listOf(
//                                Overwrite(
//                                    guild.id,
//                                    OverwriteType.Role,
//                                    allow = Permissions(
//                                    ),
//                                    deny = Permissions(
//                                        Permission.ViewChannel
//                                    )
//                                ),
//                                Overwrite(
//                                    role.id,
//                                    OverwriteType.Role,
//                                    allow = Permissions(
//                                        Permission.ViewChannel
//                                    ),
//                                    deny = Permissions()
//                                )
//                            )
//                        )
//                    }
//
//                logger.info { "getting channel" }
//                val channel = guild.channels.filterIsInstance<TextChannel>()
//                    .firstOrNull { it.name == "channelname" && it.type == ChannelType.GuildText }
//                    ?: guild.createTextChannel("channelname")
//
//                logger.info { "editing channel" }
//                channel.edit {
//                    name = "channelname"
//                    topic = "testing"
//                    nsfw = true
//                    parentId = category.id
//                    permissionOverwrites = mutableSetOf(
//                        Overwrite(
//                            guild.id,
//                            OverwriteType.Role,
//                            allow = Permissions(
//                            ),
//                            deny = Permissions(
//                                Permission.ViewChannel
//                            )
//                        ),
//                        Overwrite(
//                            role.id,
//                            OverwriteType.Role,
//                            allow = Permissions(
//                                Permission.ViewChannel
//                            ),
//                            deny = Permissions()
//                        ),
//                        Overwrite(
//                            kord.selfId,
//                            OverwriteType.Member,
//                            allow = Permissions(
//                                Permission.ViewChannel,
//                                Permission.All
//                            ),
//                            deny = Permissions()
//                        ),
//                    )
//                }
//                channel.category!!.asChannel().permissionOverwrites.forEach { channel.addOverwrite(it, "inheriting from category") }
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
