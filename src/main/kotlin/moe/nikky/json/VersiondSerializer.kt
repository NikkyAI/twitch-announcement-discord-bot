package moe.nikky.json

import io.klogging.Klogging
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import moe.nikky.debugF
import mu.KotlinLogging

data class VersionMigrator<T : Any, R : Any>(
    val json: Json,
    val old: KSerializer<T>,
    val new: KSerializer<R>,
    val converter: (T) -> R,
): Klogging {
    fun migrate(
        jsonObject: JsonObject,
        newGeneration: Int,
        versionKey: String
    ): JsonObject {
        val decodedOld = json.decodeFromJsonElement(old, JsonObject(jsonObject - versionKey))
        val converted = converter(decodedOld)
        val encodedNew = json.encodeToJsonElement(new, converted)
        return JsonObject(mapOf(versionKey to JsonPrimitive(newGeneration)) + encodedNew.jsonObject)
    }
}

//@Suppress("ReplaceRangeStartEndInclusiveWithFirstLast")
class VersionedSerializer<T : Any>(
    serializer: KSerializer<T>,
    val currentVersion: Int,
    val migrations: Map<IntRange, VersionMigrator<*, *>>,
    val versionKey: String = "formatVersion",
) : JsonTransformingSerializer<T>(serializer), Klogging {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        var jsonObject = super.transformDeserialize(element).jsonObject

        do {
            val version = jsonObject[versionKey]?.jsonPrimitive?.intOrNull ?: error("could not find '$versionKey' field")
            if (version != currentVersion) {
                val migrationKey = migrations.keys.firstOrNull { it.first == version && it.last <= currentVersion }
                    ?: error("cannot look up migration for '$versionKey: $version'")
                runBlocking {
                    logger.debugF { "applying migration $migrationKey" }
                }

                val migrator = migrations[migrationKey] ?: error("cannot look up migration for '$versionKey: $version'")
                jsonObject = migrator.migrate(jsonObject, migrationKey.last, versionKey)
            }
        } while (version != currentVersion)
        return JsonObject(jsonObject - versionKey)
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        return super.transformSerialize(element).jsonObject.let { jsonObj ->
            JsonObject(mapOf(versionKey to JsonPrimitive(currentVersion)) + jsonObj)
        }
    }
}