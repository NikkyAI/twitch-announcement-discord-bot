package moe.nikky.checks

import com.kotlindiscord.kord.extensions.checks.guildFor
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.utils.getLocale
import com.kotlindiscord.kord.extensions.utils.translate
import dev.kord.common.entity.Permission
import dev.kord.core.event.Event
import dev.kord.core.event.interaction.InteractionCreateEvent
import moe.nikky.ConfigurationService
import mu.KotlinLogging

private val logger = KotlinLogging.logger{}

suspend fun CheckContext<InteractionCreateEvent>.hasBotControl(config: ConfigurationService) {
    val guild = guildFor(event)
    val state = config[guild]

    anyCheck(
        {
            hasPermission(Permission.Administrator)
        },
        {
            hasRoleNullable { event ->
                state.adminRole
            }
        }
    )
    if (!passed) {
        fail(
            "must have permission: **${Permission.Administrator.translate(event.getLocale())}**"
                + (state.adminRole?.let { "\nor role: ** ${it.mention}**" } ?: "\nand no adminrole is configured")
        )
    }
}

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

private suspend fun <T: Event> CheckContext<T>.anyCheck(vararg checks: suspend CheckContext<T>.() -> Unit) {
    anyCheck(checks.toList())
}
private suspend fun <T: Event> CheckContext<T>.anyCheck(checks: List<suspend CheckContext<T>.() -> Unit>) {
    if (!passed) {
        return
    }
    val messages = checks.map { check ->
        check()
        if(passed) return
        passed = true
        message.also {
            message = null
        }
    }

    fail("none of the checks passed: \n - ${messages.joinToString("\n - ")}")
}