@file:OptIn(
    KordPreview::class,
    ConverterToDefaulting::class,
    ConverterToMulti::class,
    ConverterToOptional::class
)

package moe.nikky.converter

import dev.kordex.core.DiscordRelayedException
import dev.kordex.core.commands.Argument
import dev.kordex.core.commands.CommandContext
import dev.kordex.core.commands.converters.*
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.interaction.OptionValue
import dev.kord.core.entity.interaction.StringOptionValue
import dev.kord.rest.builder.interaction.OptionsBuilder
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import dev.kord.x.emoji.Emojis
import dev.kordex.core.annotations.InternalAPI
import dev.kordex.core.annotations.converters.Converter
import dev.kordex.core.annotations.converters.ConverterType
import dev.kordex.core.commands.ChoiceOptionWrapper
import dev.kordex.core.commands.OptionWrapper
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.types.Key
import dev.kordex.parser.StringParser
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull

/**
 * Argument converter for Discord [ReactionEmoji] arguments.
 *
 * This converter supports specifying emojis by supplying:
 *
 * * The actual emoji itself
 * * The emoji ID, either with or without surrounding colons
 * * The emoji name, either with or without surrounding colons -
 * the first matching emoji available to the bot will be used
 *
 * @see reactionemoji
 * @see reactionemojiList
 */
@Converter(
    "reactionEmoji",

    types = [ConverterType.LIST, ConverterType.OPTIONAL, ConverterType.SINGLE]
)
public class ReactionEmojiConverter(
    override var validator: Validator<ReactionEmoji> = null,
) : SingleConverter<ReactionEmoji>() {
    override val signatureType = "converters.reactionemoji.signatureType".toKey()

    override suspend fun parse(parser: StringParser?, context: CommandContext, named: String?): Boolean {
        val arg: String = named ?: parser?.parseNext()?.data ?: return false

        parsed = findEmoji(arg, context)
        return true
    }

    private suspend fun findEmoji(arg: String, context: CommandContext): ReactionEmoji {
        val guildEmoji = if (arg.startsWith("<a:") || arg.startsWith("<:") && arg.endsWith('>')) { // Emoji mention
            val id: String = arg.substring(0, arg.length - 1).split(":").last()

            try {
                val snowflake: Snowflake = Snowflake(id)

                kord.guilds.mapNotNull {
                    it.getEmojiOrNull(snowflake)
                }.firstOrNull()
            } catch (e: NumberFormatException) {
                throw DiscordRelayedException(
                    "converters.emoji.error.invalid".toKey() // .translate(id)
                )
            }
        } else { // ID or name
            val name = if (arg.startsWith(":") && arg.endsWith(":")) arg.substring(1, arg.length - 1) else arg

            try {
                val snowflake: Snowflake = Snowflake(name)

                kord.guilds.mapNotNull {
                    it.getEmojiOrNull(snowflake)
                }.firstOrNull()
            } catch (e: NumberFormatException) {  // Not an ID, let's check names
                kord.guilds.mapNotNull {
                    it.emojis.firstOrNull { emojiObj -> emojiObj.name?.lowercase().equals(name, true) }
                }.firstOrNull()
            }
        }

        return if (guildEmoji != null) {
            ReactionEmoji.from(guildEmoji)
        } else {
            val unicodeEmoji = Emojis[arg] ?: throw DiscordRelayedException(
                "Value `$arg` is not a valid emoji.".toKey()
            )
            ReactionEmoji.Unicode(unicodeEmoji.unicode)
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun toSlashOption(arg: Argument<*>): OptionWrapper<*> = ChoiceOptionWrapper.String(arg.displayName, arg.description) { required = true }

    override suspend fun parseOption(context: CommandContext, option: OptionValue<*>): Boolean {
        val optionValue = (option as? StringOptionValue)?.value ?: return false

        parsed = findEmoji(optionValue, context)
        return true
    }
}