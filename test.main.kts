#!/usr/bin/env -S kotlinc -script -Xplugin="${KOTLIN_HOME}/lib/kotlinx-serialization-compiler-plugin.jar"

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Foo(
    val bar: String = "baz"
)

val json = Json {
    prettyPrint = true
}

println(json.encodeToString(Foo.serializer(), Foo(bar = "bazz")))
