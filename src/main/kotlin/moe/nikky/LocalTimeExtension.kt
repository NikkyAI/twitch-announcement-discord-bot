package moe.nikky

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.ephemeralUserCommand
import com.kotlindiscord.kord.extensions.storage.Data
import com.kotlindiscord.kord.extensions.storage.StorageType
import com.kotlindiscord.kord.extensions.storage.StorageUnit
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.suggestStringMap
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.Guild
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.create.embed
import io.klogging.Klogging
import kotlinx.datetime.Clock
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

class LocalTimeExtension : Extension(), Klogging {
    override val name: String = "localtime"

    private val userConfig = StorageUnit(
        StorageType.Config,
        name,
        "timezone-config",
        TimezoneConfig::class
    )

    private fun GuildBehavior.config(userId: Snowflake) =
        userConfig
            .withGuild(id)
            .withUser(userId)

    companion object {

        private val SUGGEST_TIMEZONES = listOf(
            "UTC", "GMT", "Europe/London",
            "CET", "Europe/Berlin", "Europe/Paris",
            "NZ", "Japan", "Asia/Tokyo",
            "Asia/Manila", "Asia/Kolkata", "Asia/Jakarta",
            "US/Eastern", "US/Central", "America/New_York",
            "US/Pacific", "America/Sao_Paulo", "America/Chicago",
            "America/Los_Angeles", "Europe/Moscow", "Singapore",
        )
    }

    override suspend fun setup() {

        ephemeralSlashCommand(::TimezoneArgs) {
            name = "timezone"
            description = "list or set timezones"

            action {
                withLogContext(event, guild) { guild ->
                    logger.infoF { "received timezone id: ${arguments.timezoneId}" }
                    val timezone = try {
                        TimeZone.of(arguments.timezoneId)
                    } catch (e: IllegalTimeZoneException) {
                        respond {
                            content = "Possibly you meant one of the following timezones?"
                            embed {
                                SUGGEST_TIMEZONES.mapNotNull { zoneId ->
                                    try {
                                        TimeZone.of(zoneId)
                                    } catch (e: IllegalTimeZoneException) {
                                        logger.errorF { "incorrect timezone id: '$zoneId'" }
                                        null
                                    }
                                }.forEach { timezone ->
                                    field {
                                        val formattedTime = formatTime(Clock.System.now(), timezone)
                                        name = timezone.id
                                        value = "\uD83D\uDD57 `$formattedTime`"
                                        inline = true
                                    }
                                }
                            }
                        }
                        return@withLogContext
                    }

                    val configStorage = guild.config(event.interaction.user.id)
                    configStorage.save(
                        TimezoneConfig(timezoneId = timezone.id)
                    )

                    respond {
                        val formattedTime = formatTime(Clock.System.now(), timezone)

                        content =
                            "Timezone has been set to **${timezone.id}**. Your current time should be `$formattedTime`"
                    }
                }
            }

        }

        ephemeralUserCommand {
            name = "Local Time"

            action {
                withLogContext(event, guild) { guild ->
                    val targetUser = event.interaction.getTarget()
                    val selfUser = event.interaction.user

                    respond {
                        content = calculateDifference(
                            guild = guild,
                            targetUser = targetUser,
                            selfUser = selfUser
                        )
                    }
                }
            }
        }
        ephemeralSlashCommand(::TimezoneTargetArgs) {
            name = "LocalTime"
            description = "get the local time for a user"

            action {
                withLogContext(event, guild) { guild ->
                    val targetUser = arguments.user
                    val selfUser = event.interaction.user

                    respond {
                        content = calculateDifference(
                            guild = guild,
                            targetUser = targetUser,
                            selfUser = selfUser
                        )
                    }
                }
            }
        }
    }

    suspend fun calculateDifference(
        guild: GuildBehavior,
        targetUser: User,
        selfUser: User,
    ): String {
        val targetConfig =  guild.config(targetUser.id).get()
        val selfConfig = guild.config(selfUser.id).get()

        if (targetConfig == null) {
            return "${targetUser.mention} has not set their timezone"
        } else {
            val now = Clock.System.now()

            val difference = selfConfig?.let { selfConfig ->
                val selfOffset = selfConfig.timezone.offsetAt(now).totalSeconds.seconds
                val targetOffset = targetConfig.timezone.offsetAt(now).totalSeconds.seconds

                logger.infoF { "selfConfig: $selfConfig" }
                logger.infoF { "targetConfig: $targetConfig" }
                logger.infoF { "selfOffset: $selfOffset" }
                logger.infoF { "targetOffset: $targetOffset" }

                val difference = targetOffset - selfOffset
                if (difference != ZERO) {
                    ", relative offset is `$difference`"
                } else null
            } ?: ""
            val formattedTime = formatTime(now, targetConfig.timezone)
            return "Time in ${targetUser.mention}'s timezone: `$formattedTime`$difference"
        }
    }

    private fun formatTime(
        instant: Instant,
        timeZone: TimeZone,
    ): String {
        val localDateTime = instant.toLocalDateTime(timeZone = timeZone)
        return "%02d:%02d".format(localDateTime.hour, localDateTime.minute)
    }

    inner class TimezoneArgs : Arguments() {
        val timezoneId by string {
            name = "timezone"
            description = "time zone id"

            autoComplete { event ->
                val now = Clock.System.now()
                logger.infoF { "running autocomplete: ${focusedOption.focused} ${focusedOption.value}" }
                suggestStringMap(
                    SUGGEST_TIMEZONES.mapNotNull { zoneId ->
                        try {
                            zoneId to TimeZone.of(zoneId)
                        } catch (e: IllegalTimeZoneException) {
                            logger.errorF { "incorrect timezone id: '$zoneId'" }
                            null
                        }
                    }.associate { (zoneId, timeZone) ->
                        "$zoneId \uD83D\uDD57 ${formatTime(now, timeZone)}" to zoneId
                    }.toMap()
                )
            }
        }
    }
    inner class TimezoneTargetArgs : Arguments() {
        val user by user {
            name = "user"
            description = "user to get local time for"
        }
    }
}


@Serializable
data class TimezoneConfig(
    val timezoneId: String,
) : Data {
    val timezone: TimeZone by lazy { TimeZone.of(timezoneId) }
}
