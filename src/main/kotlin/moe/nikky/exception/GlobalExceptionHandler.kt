package moe.nikky.exception

import io.klogging.Klogging
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import moe.nikky.errorF
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// registered in META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler
class GlobalExceptionHandler: AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler, Klogging {
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        runBlocking {
            logger.errorF(exception) { "unhandled exception in coroutine" }
        }
    }
}
