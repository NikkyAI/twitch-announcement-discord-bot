package moe.nikky.checks

import dev.kordex.core.checks.hasPermission
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.utils.getLocale
import dev.kordex.core.utils.translate
import dev.kord.common.entity.Permission
import dev.kord.core.event.Event
import dev.kord.core.event.interaction.InteractionCreateEvent
import io.klogging.logger

private val logger = logger("moe.nikky.BotChecks")

suspend fun CheckContext<InteractionCreateEvent>.hasPermissions(vararg permissions: Permission) {
    val locale = event.getLocale()
    val mappedPermissions = permissions.groupBy { permission ->
        hasPermission(permission)
        passed.also { passedState ->
            logger.debug { "${permission.translate(locale)} : $passedState" }
            passed = true
        }
    }
    val missingPermissions = mappedPermissions[false] ?: emptyList()
    if (missingPermissions.isNotEmpty()) {
        val permissionNames = permissions.joinToString(", ") {
            val name = it.translate(locale)
            when (it) {
                in missingPermissions -> "**$name**"
                else -> name
            }
        }
        fail("required permissions missing: $permissionNames")
    }
}

suspend fun <T : Event> CheckContext<T>.anyCheck(vararg checks: suspend CheckContext<T>.() -> Unit) {
    anyCheck(checks.toList())
}

suspend fun <T : Event> CheckContext<T>.anyCheck(checks: List<suspend CheckContext<T>.() -> Unit>) {
    if (!passed) {
        return
    }
    val messages = checks.map { check ->
        check()
        if (passed) return
        passed = true
        message.also {
            message = null
        }
    }

    fail("none of the checks passed: \n - ${messages.joinToString("\n - ")}")
}