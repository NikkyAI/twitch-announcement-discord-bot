package moe.nikky

import dev.kord.common.entity.Overwrite
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.CategoryBehavior
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.createCategory
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull


suspend fun GuildBehavior.snowflakeToName(id: Snowflake) =
    getRoleOrNull(id)?.name ?: getMemberOrNull(id)?.displayName


suspend fun GuildBehavior.getOrCreateTextChannel(
    name: String,
    category: CategoryBehavior?,
    permissions: List<Overwrite>? = null,
) = channels.filterIsInstance<TextChannel>().firstOrNull { it.name.equals(name, ignoreCase = true) }
    ?.edit {
        if (category != null) {
            parentId = category.id
        }
        if (permissions != null) {
            permissionOverwrites = permissions.toMutableSet()
        }
    }
    ?: createTextChannel(name) {
        if (category != null) {
            parentId = category.id
        }
        if (permissions != null) {
            permissionOverwrites.clear()
            permissionOverwrites.addAll(permissions)
        }
    }

suspend fun GuildBehavior.getOrCreateCategory(
    name: String,
    permissions: MutableList<Overwrite>? = null,
) = channels.filterIsInstance<Category>().firstOrNull { it.name.equals(name, ignoreCase = true) }
    ?.edit {
        if (permissions != null) {
            permissionOverwrites = permissions.toMutableSet()
        }
    }
    ?: createCategory(name) {
        if (permissions != null) {
            permissionOverwrites.clear()
            permissionOverwrites.addAll(permissions)
        }
    }
