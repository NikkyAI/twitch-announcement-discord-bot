package moe.nikky.checks

import dev.kordex.core.checks.failed
import dev.kordex.core.checks.nullMember
import dev.kordex.core.checks.passed
import dev.kordex.core.checks.types.CheckContext
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.event.Event
import dev.kordex.core.checks.memberFor
import dev.kordex.core.i18n.toKey
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList


public suspend fun <T : Event> CheckContext<T>.hasRoleNullable(builder: suspend (T) -> RoleBehavior?) {
    if (!passed) {
        return
    }

    val logger = KotlinLogging.logger("moe.nikky.checks.hasRoleNullable")
    val member = memberFor(event)

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
                        "checks.hasRole.failed".toKey(), //.translate(role.mention),
                )
            }
        } else {
            fail()
        }
    }
}