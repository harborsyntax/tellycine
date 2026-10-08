package app.tellycine.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Lazily initialises the Cast framework. On devices without Google Play services
 * [castContext] stays null and every cast entry point hides itself.
 */
class CastManager(private val context: Context) {

    val castContext: CastContext? by lazy {
        val available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (!available) {
            null
        } else {
            try {
                @Suppress("DEPRECATION")
                CastContext.getSharedInstance(context)
            } catch (e: Exception) {
                Log.w("CastManager", "Cast unavailable", e)
                null
            }
        }
    }

    /** True while connected to a Cast device, so new playback should go to the TV. */
    fun isConnected(): Boolean = castContext?.sessionManager?.currentCastSession?.isConnected == true

    fun deviceName(): String? = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
}
