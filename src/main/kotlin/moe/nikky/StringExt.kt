package moe.nikky

fun String.indent(indent: String) = lines().joinToString(prefix = indent, separator = "\n" + indent)


fun String.linesChunkedByMaxLength(maxLength: Int = 2000) : List<String> {
    val results = mutableListOf<String>()
    var accumulator = StringBuilder(2000)
    lines().forEach { line ->
        if(accumulator.length + line.length + 5 >= maxLength) {
            results += accumulator.toString()
            accumulator = StringBuilder(2000)
        }

        accumulator.appendLine(line)
    }
    results += accumulator.toString()
    return results
}

