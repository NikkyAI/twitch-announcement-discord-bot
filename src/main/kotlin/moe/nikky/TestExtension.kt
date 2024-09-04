package moe.nikky

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.optionalDuration
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.optionalUser
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.respond
import dev.kord.common.entity.GuildScheduledEventEntityMetadata
import dev.kord.common.entity.GuildScheduledEventPrivacyLevel
import dev.kord.common.entity.Permission
import dev.kord.common.entity.ScheduledEntityType
import dev.kord.common.entity.optional.Optional
import dev.kord.common.entity.optional.optional
import dev.kord.core.behavior.createScheduledEvent
import dev.kord.core.entity.Guild
import dev.kord.core.event.message.MessageCreateEvent
import io.klogging.Klogging
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import moe.nikky.twitch.TwitchExtension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TestExtension : Extension(), Klogging {
    override val name: String = "test"

    val twitch by lazy {
        getKoin().get<TwitchExtension>()
    }

    override suspend fun setup() {
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
//        ephemeralSlashCommand {
//            name = "events"
//            description = "scheduled events things"
//            allowInDms = false
//
//            ephemeralSubCommand(::ScheduleArgs) {
//                name = "create"
//                description = "creates a scheduled event"
//
//                requireBotPermissions(
//                    Permission.ManageEvents
//                )
//
//                action {
//                    withLogContext(event, guild) { guild ->
//                        updateSchedule(
//                            guild = guild,
//                            startTime = Clock.System.now() + (arguments.delay?.toDuration() ?: 10.minutes),
//                            name = arguments.title,
//                            description = arguments.description,
//                            location = arguments.location,
//                        )
//                        respond {
//                            content = "event created"
//                        }
//                    }
//                }
//            }
//
//            ephemeralSubCommand() {
//                name = "list"
//                description = "list events"
//
//
//                requireBotPermissions(
//
//                )
//
//                action {
//                    withLogContext(event, guild) { guild ->
//                        val events = guild.scheduledEvents.toList()
//                        val messages = events.map { event ->
//                            val status = event.status::class.simpleName
//                            val type = event.entityType::class.simpleName
//                            val location = event.entityMetadata?.location?.value
//                                """>
//                                event: ${event.name}
//                                description: ${event.description}
//                                start: ${event.scheduledStartTime}
//                                end: ${event.scheduledEndTime}
//                                status: ${status}
//                                type: ${type}
//                                location: $location
//                                metadata: ${event.entityMetadata}
//                                data: ${event.data}
//                            """.trimIndent().also {
//                                this@TestExtension.logger.infoF { it }
//                            }
//                        }
//                        respond {
//                            this.content = "${messages.size} \n" + messages.joinToString { "\n\n" }
//                        }
//                    }
//
//                }
//            }
//        }

//        ephemeralMessageCommand {
//            name = "testmessagecmd"
//
//            action {
//                val targetMessage = event.interaction.getTarget()
//                respond {
//                    content = """message content: \n```${targetMessage.content}\n```"""
//                }
//            }
//        }
//        ephemeralUserCommand {
//            name = "testusercmd"
//
//            action {
//                val targetUser = event.interaction.getTarget()
//                respond {
//                    content = """
//                        |banner: ${targetUser.getBannerUrl(Image.Format.GIF)}
//                        |profile link: <${targetUser.profileLink}>
//                    """.trimMargin()
//                }
//            }
//        }

        event<MessageCreateEvent> {  // Listen for message creation events
            action {  // Code to run when a message creation happens
                if (event.message.content.equals("hello", ignoreCase = true)) {
                    event.message.respond("Hello!")
                }
            }
        }
//        event<GuildCreateEvent> {
//            action {
//                withLogContext(event, event.guild) { guild ->
//                    if (event.guild.id != TEST_GUILD_ID) return@withLogContext
//                    logger.infoF { "ready event on ${guild.name}" }
//                }
//            }
//        }
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

    inner class ScheduleArgs : Arguments() {
        val title by string {
            name = "title"
            description = "scheduled event title"
        }
        val description by optionalString {
            name = "description"
            description = "scheduled event description"
        }
        val location by optionalString {
            name = "location"
            description = "event location"
        }
        val delay: DateTimePeriod? by optionalDuration {
            name = "delay"
            description = "delay before the event starts"
        }
    }

    private suspend fun updateSchedule(
        guild: Guild,
        startTime: Instant,
        name: String,
        description: String? = null,
        duration: Duration = 1.hours,
        location: String? = null,
    ) {
        guild.createScheduledEvent(
            name = name,
            privacyLevel = GuildScheduledEventPrivacyLevel.GuildOnly,
            scheduledStartTime = startTime,
            entityType = ScheduledEntityType.External
        ) {
            description?.let {
                this.description = it
            }
            scheduledEndTime = startTime + duration
            entityMetadata = GuildScheduledEventEntityMetadata(
                location = location?.optional() ?: Optional.Missing()
            )
        }
    }
}
