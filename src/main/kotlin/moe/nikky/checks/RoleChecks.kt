package moe.nikky.checks

import com.kotlindiscord.kord.extensions.checks.failed
import com.kotlindiscord.kord.extensions.checks.nullMember
import com.kotlindiscord.kord.extensions.checks.passed
import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.event.Event
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList


public suspend fun <T : Event> CheckContext<T>.hasRoleNullable(builder: suspend (T) -> RoleBehavior?) {
    if (!passed) {
        return
    }

    val logger = KotlinLogging.logger("moe.nikky.checks.hasRoleNullable")
    val member = com.kotlindiscord.kord.extensions.checks.memberFor(event)

    if (member == null) {

        logger.nullMember(event)

        fail()
    } else {
        val role = builder(event)

        if (role != null) {
            if (member.asMember().roles.toList().contains(role)) {
                logger.passed()

                pass()
            } else {
                logger.failed("Member $member does not have role $role")

                fail(
                    translate(
                        "checks.hasRole.failed",
                        replacements = arrayOf(role.mention),
                    )
                )
            }
        } else {
            fail()
        }
    }
}