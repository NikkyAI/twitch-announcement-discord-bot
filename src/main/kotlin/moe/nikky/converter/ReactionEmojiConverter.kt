@file:OptIn(
    KordPreview::class,
    ConverterToDefaulting::class,
    ConverterToMulti::class,
    ConverterToOptional::class
)

package moe.nikky.converter

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Argument
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.converters.*
import com.kotlindiscord.kord.extensions.modules.annotations.converters.Converter
import com.kotlindiscord.kord.extensions.modules.annotations.converters.ConverterType
import com.kotlindiscord.kord.extensions.parser.StringParser
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.interaction.OptionValue
import dev.kord.rest.builder.interaction.OptionsBuilder
import dev.kord.rest.builder.interaction.StringChoiceBuilder
import dev.kord.x.emoji.Emojis
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
@OptIn(KordPreview::class)
public class ReactionEmojiConverter(
    override var validator: Validator<ReactionEmoji> = null,
) : SingleConverter<ReactionEmoji>() {
    override val signatureTypeString: String = "converters.reactionemoji.signatureType"

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
                    context.translate("converters.emoji.error.invalid", replacements = arrayOf(id))
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
                "Value `$arg` is not a valid emoji."
            )
            ReactionEmoji.Unicode(unicodeEmoji.unicode)
        }
    }

    override suspend fun toSlashOption(arg: Argument<*>): OptionsBuilder =
        StringChoiceBuilder(arg.displayName, arg.description).apply { required = true }

    override suspend fun parseOption(context: CommandContext, option: OptionValue<*>): Boolean {
        val optionValue = (option as? OptionValue.StringOptionValue)?.value ?: return false

        parsed = findEmoji(optionValue, context)
        return true
    }
}