package moe.nikky

import br.com.colman.dicehelper.*
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import io.klogging.Klogging

class DiceExtension() : Extension(), Klogging {
    override val name: String = "dice"
    override suspend fun setup() {
        publicSlashCommand(::DiceArgs) {
            name = "dice"
            description = "rolls dice"
            allowInDms = true

            action {
                withLogContextOptionalGuild(event, guild) { guild ->
                    val diceNotation: DiceNotation = arguments.notation.diceNotation()

                    val result = diceNotation.roll()

                    val dices = result.results.joinToString("\n") {
                        """${it.dice.displayNotation.padEnd(6)} -> Σ ${it.result.sum()} :: Ø ${
                            it.result.average().format(2)
                        } :: ||${it.result.joinToString()}||"""
                    }
                    respond {
                        content = """
                        |notation: ${diceNotation.getDisplayString()}
                        |total:: Σ ${result.total}
                        |dice:: 
                        |$dices
                    """.trimMargin()
                    }
                }
            }
        }
    }

    inner class DiceArgs : Arguments() {
        val notation by string {
            name = "notation"
            description = "dices to roll"
        }
    }
}

private fun DiceNotation.getDisplayString(): String =
    dice.displayNotation + if (operator != null && notation != null) {
        " $operator ${notation?.getDisplayString()}"
    } else {
        ""
    }

private val Dice.displayNotation: String
    get() = when (this) {
        is FixedDice -> value.toString()
        is RandomDice -> "${amount}d${maxFaceValue}"
        else -> "unknown type ${this::class.qualifiedName}"
    }

fun Double.format(digits: Int) = "%.${digits}f".format(this)
