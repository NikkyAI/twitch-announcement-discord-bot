@file:Suppress("UNCHECKED_CAST")

package moe.nikky

import dev.kordex.core.storage.Data
import dev.kordex.core.storage.DataAdapter
import dev.kordex.core.storage.StorageUnit
import dev.kordex.core.storage.storageFileRoot
import io.github.xn32.json5k.Json5
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * Standard data adapter class implementing the TOML format. Stores TOML files in folders.
 *
 * This is a pretty simple implementation, so it's a good example to use when writing your own data adapters.
 */
public open class Json5DataAdapter : DataAdapter<String>() {
    private val json = Json5 {
        prettyPrint = true
        encodeDefaults = true
        useSingleQuotes = false
        quoteMemberNames = false
    }

    private val StorageUnit<*>.file: File
        get() = getPath().toFile()

    private val StorageUnit<*>.pathString: String
        get() = getPath().pathString

    private fun StorageUnit<*>.getPath(): Path {
        var path = storageFileRoot / storageType.type / namespace

        if (guild != null) path /= "guild-$guild"
        if (channel != null) path /= "channel-$channel"
        if (user != null) path /= "user-$user"
        if (message != null) path /= "message-$message"

        return path / "$identifier.json5"
    }

    override suspend fun <R : Data> delete(unit: StorageUnit<R>): Boolean {
        removeFromCache(unit)

        val file = unit.file

        if (file.exists()) {
            return file.delete()
        }

        return false
    }

    override suspend fun <R : Data> get(unit: StorageUnit<R>): R? {
        val dataId = unitCache[unit]

        if (dataId != null) {
            val data = dataCache[dataId]

            if (data != null) {
                return data as R
            }
        }

        return reload(unit)
    }

    override suspend fun <R : Data> reload(unit: StorageUnit<R>): R? {
        val dataId = unit.pathString
        val file = unit.file

        if (file.exists()) {
            val result: R = json.decodeFromString(unit.serializer, file.readText())

            dataCache[dataId] = result
            unitCache[unit] = dataId
        }

        return dataCache[dataId] as R?
    }

    override suspend fun <R : Data> save(unit: StorageUnit<R>, data: R): R {
        val dataId = unit.pathString

        dataCache[dataId] = data
        unitCache[unit] = dataId

        val file = unit.file

        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }

        file.writeText(json.encodeToString(unit.serializer, data))

        return data
    }

    override suspend fun <R : Data> save(unit: StorageUnit<R>): R? {
        val data = get(unit) ?: return null
        val file = unit.file

        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }

        file.writeText(json.encodeToString(unit.serializer, data))

        return data
    }
}