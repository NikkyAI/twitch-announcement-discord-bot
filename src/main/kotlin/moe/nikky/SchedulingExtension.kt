package moe.nikky

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.long
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.components.ephemeralStringSelectMenu
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.storage.Data
import dev.kordex.core.storage.StorageType
import dev.kordex.core.storage.StorageUnit
import dev.kordex.core.time.TimestampType
import dev.kordex.core.utils.suggestLongMap
import dev.kordex.core.utils.suggestStringMap
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.interaction.followup.EphemeralFollowupMessage
import dev.kordex.core.i18n.toKey
import io.klogging.Klogging
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.component.inject
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SchedulingExtension() : Extension(), Klogging {
    override val name = "scheduling"

    val localTimeExt: LocalTimeExtension by inject()

    @Serializable
    data class SchedulingData(
        val events: Map<String, EventData> = emptyMap()
    ) : Data {

        fun update(key: String, event: EventData): SchedulingData {
            return copy(
                events = events + (key to event)
            )
        }

        inline fun update(key: String, transform: (EventData) -> EventData): SchedulingData {
            return update(
                key, transform(events[key] ?: relayError("unknown event key: $key"))
            )
        }

    }

    @Serializable
    data class EventData(
        val channelId: Snowflake,
        val messageId: Snowflake,
        val id: String,
        val name: String,
        val description: String,
        val start: Instant,
        val end: Instant,
        val slotLength: Duration = 30.minutes,
        val signups: List<Signup>
    ) {
        fun addSignup(
            signup: Signup
        ): EventData {
            return copy(
                signups = (signups + signup).distinct()
            )
        }
        fun removeSignup(
            signup: Signup
        ): EventData {
            return copy(
                signups = (signups - signup)
            )
        }
    }

    @Serializable
    data class Signup(
        val user: Snowflake,
        val slot: Instant,
        val duration: Duration,
    )

    private val dataStorage = StorageUnit(
        storageType = StorageType.Data,
        namespace = name,
        identifier = "scheduling",
        dataType = SchedulingData::class
    )


    private suspend fun GuildBehavior.config() =
        dataStorage
            .withGuild(this.id)

    val localDatetimeFormat = LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        dayOfMonth()
        chars(" @ ")
        hour()
        char(':')
        minute()
//        optional {
//            char(':'); second()
//            optional {
//                char('.'); secondFraction(minLength = 3)
//            }
//        }
    }

    init {
        bot.getKoin().loadModules(
            listOf(
                module {
                    single { this@SchedulingExtension }
                }
            )
        )
    }

    private val configurationExtension: ConfigurationExtension by inject()


    companion object {
        private val requiredPermissions = arrayOf(
            Permission.ViewChannel,
            Permission.SendMessages,
            Permission.ManageMessages,
        )


    }

//    val emojiList = run {
//        listOf(
//            "🇦", "🇧", "🇨", "🇩", "🇪",
//            "🇫", "🇬", "🇭", "🇮", "🇯",
//            "🇰", "🇱", "🇲", "🇳", "🇴",
//            "🇵", "🇶", "🇷", "🇸", "🇹",
//            "🇺", "🇻", "🇼", "🇽", "🇾",
//            "🇿",
//            "0️⃣", "1️⃣", "2️⃣",
//            "3️⃣", "5️⃣", "6️⃣",
//            "7️⃣", "4️⃣", "8️⃣",
//            "9️⃣", "🔟"
//        ).map {
//            ReactionEmoji.from(it)
//        }
//    }

//    val emojiList2 = run {
//
//        EmojiManager.getAllEmojisSubGrouped()
//            .forEach { (subgroup, emojis) ->
//                emojis.forEach { emoji ->
//                    runBlocking {
//                        logger.info { "${subgroup.group} ${subgroup.name} ${emoji.unicode}: $emoji" }
//                    }
//                }
//
//            }
////        }
//        EmojiManager.getAllEmojisByGroup(EmojiGroup.SYMBOLS).filter { emoji ->
////            logger.info { "${emoji.unicode}: $emoji" }
//            emoji.allAliases.any {
//                it.startsWith(":number") ||
//                        it.startsWith(":regional_indicator_")
//            }
//        }.also {
//            runBlocking {
//                logger.info { "filtered" }
//            }
//            it.forEach { emoji ->
//                runBlocking {
//                    logger.info { "${emoji.unicode}: $emoji" }
//                }
//        }
//        }
//    }.map {
//        ReactionEmoji.from(it.emoji)
//    }
//
//    val emojilist3 = run {
//        runBlocking {
//            EmojiManager.getAllEmojisByGroup(EmojiGroup.SYMBOLS)
//                .forEach { emoji ->
//                    logger.infoF { "${emoji.emoji} ${emoji.unicode} ${emoji.description}" }
//                }
//            EmojiManager.getAllEmojisByGroup(EmojiGroup.OBJECTS)
//                .forEach { emoji ->
//                    logger.infoF { "${emoji.emoji} ${emoji.unicode} ${emoji.description}" }
//                }
//        }
//    }

    val timeEmoji = mapOf(
        0 to false to "\uD83D\uDD5B",
        1 to false to "\uD83D\uDD50",
        2 to false to "\uD83D\uDD51",
        3 to false to "\uD83D\uDD52",
        4 to false to "\uD83D\uDD53",
        5 to false to "\uD83D\uDD54",
        6 to false to "\uD83D\uDD55",
        7 to false to "\uD83D\uDD56",
        8 to false to "\uD83D\uDD57",
        9 to false to "\uD83D\uDD58",
        10 to false to "\uD83D\uDD59",
        11 to false to "\uD83D\uDD5A",
//        12 to false to "\uD83D\uDD5B",
        0 to true to "\uD83D\uDD67",
        1 to true to "\uD83D\uDD5C",
        2 to true to "\uD83D\uDD5D",
        3 to true to "\uD83D\uDD5E",
        4 to true to "\uD83D\uDD5F",
        5 to true to "\uD83D\uDD60",
        6 to true to "\uD83D\uDD61",
        7 to true to "\uD83D\uDD62",
        8 to true to "\uD83D\uDD63",
        9 to true to "\uD83D\uDD64",
        10 to true to "\uD83D\uDD65",
        11 to true to "\uD83D\uDD66",
//        12 to true to "\uD83D\uDD67",
    )

    private fun emojiForTime(time: LocalTime): String {
        val key = (time.hour % 12) to (time.minute >= 30)
        return timeEmoji[key] ?: timeEmoji.values.random()
    }

    private suspend fun tryParseInstant(value: String): Instant? {
        try {
            return Instant.parse(value)
        } catch (e: IllegalArgumentException) {
            logger.warn { "failed to parse $value as a iso8601 timestamp" }
        }

        try {
            val epochSeconds = value.substringAfter(':').substringBeforeLast(":").toLong()
            return Instant.fromEpochSeconds(epochSeconds)

        } catch (e: Exception) {
            logger.warn { "failed to parse $value as a discord timestamp" }
        }

        return null
    }

    suspend fun parseInstant(value: String): Instant {
        return tryParseInstant(value) ?: relayError("failed to parse $value")
    }

    inner class SchedulingCreateArgs : Arguments() {
        val id by string {
            name = "id".toKey()
            description = "id of the event".toKey()
        }
        val name by string {
            name = "name".toKey()
            description = "name of the event".toKey()
        }
        val description by string {
            name = "description".toKey()
            description = "freeform event description".toKey()
//            defaultValue = "a new cool event"
        }
        val startTime by string {
            name = "start".toKey()
            description = "supports discord timestamps hammertime".toKey()
            validate {
                tryParseInstant(value) ?: fail("failed to parse $value".toKey())
            }
        }
        val endTime by string {
            name = "end".toKey()
            description = "supports discord timestamps hammertime".toKey()
            validate {
                tryParseInstant(value) ?: fail("failed to parse $value".toKey())
            }
        }

        val slotLength by long {
            name = "slotlength".toKey()
            description = "slot length in minutes".toKey()
//            choices(
//                listOf(
//                    15,
//                    30,
//                    45,
//                    60,
//                    90,
//                    120
//                )
//                    .associate {
//                        it.minutes.toString() to it.toLong()
//                    }
//            )

            validate {
                passIf { value in (15..300) }
            }

            autoComplete {
                suggestLongMap(
                    listOf(
                        30,
                        45,
                        60,
                        90,
                        120
                    )
                        .associate {
                            it.minutes.toString() to it.toLong()
                        }
                )
            }
        }

//        val endTime by arg("end", "event ends at", object : SingleConverter<Instant>() {
//            override val signatureTypeString: String = "converters.instant.signatureType"
//
//            override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
//                val arg: String = named ?: parser?.parseNext()?.data ?: return false
//                try {
//                    parsed = Instant.parse(arg)
//                } catch (e: IllegalArgumentException) {
//                    relayError(e.message ?: "failed to parse $arg")
//                }
//                return true
//            }
//
//            override suspend fun parseOption(context: CommandContext, option: OptionValue<*>): Boolean {
//                val optionValue = (option as? StringOptionValue)?.value ?: return false
//                try {
//                    parsed = Instant.parse(optionValue)
//                } catch (e: IllegalArgumentException) {
//                    relayError(e.message ?: "failed to parse $optionValue")
//                }
//                return true
//            }
//
//            override suspend fun toSlashOption(arg: Argument<*>): OptionsBuilder =
//                StringChoiceBuilder(arg.displayName, arg.description).apply { required = true }
//
//        })
    }

    inner class SignupArgs : Arguments() {
        val event by stringChoice {
            name = "event".toKey()
            description = "Select a event".toKey()
//            validate {
//                val schedulingData = this.context.getGuild()!!.config().get() ?: SchedulingData()
//                failIfNot("unknown event '$value'") {
//                    value !in schedulingData.events.values.map { it.name }
//                }
//            }
            autoComplete { event ->
                val channel = getChannel().asChannelOf<GuildMessageChannel>()
                withLogContext(event, channel.guild) { guild ->
                    val schedulingData = guild.config().get() ?: SchedulingData()
                    schedulingData.events.keys

                    suggestStringMap(
                        schedulingData.events.values.associate { it.name to it.id },
                        suggestInputWithoutMatches = true
                    )
                }
            }
        }
//        val timeslot by string {
//            name = "timeslots"
//
//            autoComplete { event ->
//                val channel = getChannel().asChannelOf<GuildMessageChannel>()
//                withLogContext(event, channel.guild) { guild ->
//                    val schedulingData = guild.config().get() ?: SchedulingData()
//                    this@autoComplete.
//                    schedulingData.events.keys
//
//                    suggestStringMap(
//                        schedulingData.events.mapValues { it.value.name },
//                        suggestInputWithoutMatches = true
//                    )
//                }
//            }
//        }
    }

    private suspend fun updateEventMessage(guild: GuildBehavior, event: EventData) {

        val channel = guild.getChannelOf<TopGuildMessageChannel>(event.channelId)
        val message = channel.getMessage(event.messageId)

        val timeslots =
            event.start.epochSeconds until event.end.epochSeconds step event.slotLength.inWholeSeconds

        val timeslotsMap = timeslots.mapIndexed { index, epochSeconds ->
            val start = Instant.fromEpochSeconds(epochSeconds)
            val end = start + event.slotLength

            val signups = event.signups.filter {
                it.slot == start
            }
            val users = signups.map { "<@${it.user}>" }
            "${TimestampType.ShortDateTime.format(start.epochSeconds)} -> ${
                TimestampType.ShortDateTime.format(
                    end.epochSeconds
                )
            }" + " " + users.joinToString(" ")
        }

        message.edit {
            content = """
                id: `${event.id}`
                event: ${event.name}
                start: ${TimestampType.LongDateTime.format(event.start.epochSeconds)} ${
                    TimestampType.RelativeTime.format(
                        event.start.epochSeconds
                    )
                }
                end: ${TimestampType.LongDateTime.format(event.end.epochSeconds)} ${
                    TimestampType.RelativeTime.format(
                        event.end.epochSeconds
                    )
                }
                slots: ${event.slotLength}

                ${event.description}

                signup with
                ```
                /signup event:${event.id}
                ```
            """.trimIndent() + "\n" + timeslotsMap.joinToString("\n")
        }
    }

    override suspend fun setup() {

        ephemeralSlashCommand {
            name = "scheduling".toKey()
            description = "create and edit open signup events".toKey()
            allowInDms = false

            ephemeralSubCommand(::SchedulingCreateArgs) {
                name = "create".toKey()
                description = "register a new event".toKey()


                requireBotPermissions(
                    Permission.SendMessages,
                )
                check {
                    with(configurationExtension) { requiresBotControl() }
                }
                action {
                    withLogContext(event, guild) { guild ->
                        val config = guild.config()
                        val schedulingData = config.get() ?: SchedulingData()

                        val existingEvent = schedulingData.events[arguments.id]
                        if (existingEvent != null) {
                            relayError("event ${existingEvent.id} ${existingEvent.name} exists already")
                        }
                        val startTime = parseInstant(arguments.startTime)
                        val endTime = parseInstant(arguments.endTime)
                        val slotLength = arguments.slotLength.minutes

                        val messageChannel = event.interaction.channel.asChannelOf<GuildMessageChannel>()

                        val message = messageChannel.createMessage {
                            content = """
                                new event placeholder
                            """.trimIndent()
                            suppressNotifications = true
                        }


                        val newEvent = EventData(
                            channelId = channel.id,
                            messageId = message.id,
                            id = arguments.id,
                            name = arguments.name,
                            description = arguments.description,
                            start = startTime,
                            end = endTime,
                            slotLength = slotLength,
                            signups = emptyList()
                        )

                        config.save(
                            (config.get() ?: SchedulingData()).update(arguments.id, newEvent)
                        )

                        updateEventMessage(guild, newEvent)

//                        try {
//                            timeslotsMap.keys.forEach {
//                                message.addReaction(it)
//                            }
//                        } catch (e: Exception) {
//                            logger.error(e) { e.message ?: "unknown" }
//                            relayError(e.message ?: "unknown")
//                        }

                        respond {
                            content = "event created"
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = "list".toKey()
                description = "list events".toKey()

                action {
                    withLogContext(event, guild) { guild ->
                        logger.infoF { "loading config" }
                        val config = guild.config()
                        val schedulingData = config.get() ?: SchedulingData()

                        logger.info { "got ${schedulingData.events.size} events" }
                        val response = schedulingData.events.map {
                            "${it.key} ${it.value.name} ${TimestampType.RelativeTime.format(it.value.start.epochSeconds)}"
                        }.joinToString("\n")
                        logger.infoF { response }

                        respond {
                            content = response
                        }
                    }
                }
            }
        }

        ephemeralSlashCommand(::SignupArgs) {
            name = "signup".toKey()
            description = "signup for events".toKey()
            allowInDms = false

            action {
                withLogContext(event, guild) { guild ->
                    val config = guild.config()
                    val schedulingData = config.get() ?: SchedulingData()
                    logger.info { "loading event ${arguments.event}" }
                    val scheduledEvent = schedulingData.events[arguments.event]
                        ?: relayError("could not find event for key ${arguments.event}")

//                    val messageChannel = event.interaction.channel.asChannelOf<GuildMessageChannel>()
                    try {
//                    val locale = getLocale()
                        val timeConfig = with(localTimeExt) {
                            guild.config(event.interaction.user.id)
                                .get() ?: relayError(
                                """
                                |please set your timezone using
                                |```
                                |/timezone set
                                |```
                                |and run the command again
                            """.trimMargin()
                            )
                        }
                        val timezone = timeConfig.timezone
                        var selectedTimeslot: Instant? = null
                        var response: EphemeralFollowupMessage? = null
//                        var button: EphemeralInteractionButton<ModalForm>? = null
//                        var timeslotSelection: EphemeralStringSelectMenu<ModalForm>? = null

                        suspend fun updateResponse(
                            buttonEnabled: Boolean = false,
                        ) {
//                            button = null
                            response?.edit {
                                content = "please select a timeslot and submit"
                                components {
                                    removeAll()
                                    ephemeralStringSelectMenu(
                                        row = 0
                                    ) {
                                        minimumChoices = 1
                                        maximumChoices = 1 // TODO: allow signing up for multiple timeslots ?
                                        placeholder = "timeslot start".toKey()

                                        val timeslots =
                                            scheduledEvent.start.epochSeconds..scheduledEvent.end.epochSeconds step scheduledEvent.slotLength.inWholeSeconds
                                        timeslots.forEachIndexed() { index, epochSeconds ->
                                            val instant = Instant.fromEpochSeconds(epochSeconds)
                                            val localDatetime =  instant
                                                .toLocalDateTime(timezone)
                                            val formattedTime = localDatetime
                                                .format(localDatetimeFormat)

                                            val end = instant + scheduledEvent.slotLength
                                            val localDatetimeEnd = end
                                                .toLocalDateTime(timezone)
                                            val formattedTimeEnd = localDatetimeEnd
                                                .format(localDatetimeFormat)
                                            val value = instant.toString()

                                            option(formattedTime.toKey(), value) {
                                                description = TimestampType.LongDateTime.format(epochSeconds).toKey()
                                                if (instant == selectedTimeslot) {
                                                    default = true
                                                }
                                                description = "slot: $index, until $formattedTimeEnd".toKey()


                                                emoji = DiscordPartialEmoji(
                                                    name = emojiForTime(localDatetime.time)
                                                )
                                            }
                                        }


                                        action { modal ->
                                            selectedTimeslot = selected.firstOrNull()?.let {
                                                Instant.parse(it)
                                            }


//                                        validateValues()
                                            val instant = selectedTimeslot
                                            if (instant != null && instant >= scheduledEvent.start && instant < scheduledEvent.end) {
                                                if (!buttonEnabled) {
                                                    updateResponse(buttonEnabled = true)
                                                }
                                            } else {
                                                if (buttonEnabled) {
                                                    updateResponse(buttonEnabled = false)
                                                }
                                            }
                                        }
                                    }
                                    ephemeralButton(
                                        row = 1
                                    ) {
                                        if (buttonEnabled) {
                                            enable()
                                        } else {
                                            disable()
                                        }
//                                    disable()
                                        style = ButtonStyle.Success
                                        label = "Submit".toKey()

                                        action { modal ->
                                            try {
                                                val instant = selectedTimeslot ?: relayError("no timeslot was selected")

                                                val signup = Signup(
                                                    user = user.id,
                                                    slot = instant,
                                                    duration = scheduledEvent.slotLength
                                                )

                                                //TODO: check for duplication

                                                config.save(
                                                    (config.get() ?: SchedulingData()).update(
                                                        arguments.event
                                                    ) { event ->
                                                        event.addSignup(signup).also {
                                                            updateEventMessage(guild, event)
                                                        }
                                                    }
                                                )

                                                response?.edit {
                                                    content = "registered ${user.mention} for ${
                                                        TimestampType.ShortDateTime.format(instant.epochSeconds)
                                                    }"

                                                    suppressEmbeds = true
                                                    logger.info { "components: ${components?.size}" }
                                                    logger.info { "embeds: ${embeds?.size}" }
                                                    components {
                                                        removeAll()
                                                    }
                                                    embeds?.clear()
                                                    components?.clear()
                                                } ?: relayError("failed up update response")

                                            } catch (e: Exception) {
                                                logger.error(e) { "something exploded" }
                                                relayError(e.message ?: "unknown error")
                                            }
                                        }
                                    }
                                }
                            } ?: relayError("failed up update response")
                        }

                        response = respond {
                            content = "please select a timeslot and submit"
                        }
                        updateResponse()
                    } catch (e: Exception) {
                        logger.error(e) { "something exploded" }
                        relayError(e.message ?: "unknown error")
                    }
                }
            }
        }
    }
}