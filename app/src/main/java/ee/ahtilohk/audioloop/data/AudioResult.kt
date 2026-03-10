package ee.ahtilohk.audioloop.data

/**
 * A sealed class representing the result of an audio operation.
 * Addresses technical debt #7 (Error handling).
 */
sealed class AudioResult<out T> {
    data class Success<out T>(val data: T) : AudioResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : AudioResult<Nothing>()
    object Loading : AudioResult<Nothing>()
}

/**
 * Functional interface for error recovery strategies.
 */
fun interface ErrorRecovery {
    fun recover(): Boolean
}
