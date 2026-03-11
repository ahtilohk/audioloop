package ee.ahtilohk.audioloop

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Standard log wrapper for AudioLoop.
 * In production, it logs non-fatal exceptions to Firebase Crashlytics.
 */
object AppLog {
    private const val TAG = "AudioLoop"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        throwable?.let {
            try {
                FirebaseCrashlytics.getInstance().recordException(it)
            } catch (e: Exception) {
                // Crashlytics not initialized or other error
            }
        }
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }
}
