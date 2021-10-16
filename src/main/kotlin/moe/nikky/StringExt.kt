package moe.nikky

fun String.indent(indent: String) = lines().joinToString(prefix = indent, separator = "\n" + indent)
