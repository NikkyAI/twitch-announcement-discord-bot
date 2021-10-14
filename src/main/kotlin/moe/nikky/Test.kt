package moe.nikky

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        flowOf(1).collect {

            println(Thread.currentThread().getStackTrace()
                .joinToString("\n") { "File name: ${it.getFileName()}, at line: ${it.getLineNumber()}" })

            methodCall()
        }
    }
}

fun methodCall() {
    val x = Thread.currentThread().getStackTrace()[2]
    println("File name: ${x.getFileName()}, at line: ${x.getLineNumber()}")
}