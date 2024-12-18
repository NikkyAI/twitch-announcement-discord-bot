package moe.nikky

import br.com.colman.dicehelper.Dice
import br.com.colman.dicehelper.DiceNotation
import br.com.colman.dicehelper.FixedDice
import br.com.colman.dicehelper.RandomDice
import br.com.colman.dicehelper.diceNotation
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import io.klogging.Klogging

class DiceExtension() : Extension(), Klogging {
    override val name: String = "dice"
    override suspend fun setup() {
        publicSlashCommand(::DiceArgs) {
            name = "dice".toKey()
            description = "rolls dice".toKey()
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
            name = "notation".toKey()
            description = "dices to roll".toKey()
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
