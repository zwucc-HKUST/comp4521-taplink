package hk.taplink.studio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.TimeUnit

internal fun Context.canLocate() =
    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// Called on the main thread while Explore is resumed. All callbacks run on that same thread.
@SuppressLint("MissingPermission")
internal fun locateOnce(context: Context, complete: (Location?) -> Unit): () -> Unit {
    val manager = context.getSystemService(LocationManager::class.java)
    if (!context.canLocate() || manager == null) {
        complete(null)
        return {}
    }

    val main = Handler(Looper.getMainLooper())
    var active = true
    lateinit var listener: LocationListener
    fun cancel() {
        if (!active) return
        active = false
        main.removeCallbacksAndMessages(null)
        runCatching { manager.removeUpdates(listener) }
    }
    fun finish(fix: Location?) {
        if (active) {
            cancel()
            complete(fix)
        }
    }
    fun fresh(fix: Location): Boolean {
        val age = SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
        return age in 0L..TimeUnit.MINUTES.toNanos(10)
    }
    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (fresh(location)) finish(location)
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Required on Android 10")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    val available = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
    val providers = listOf("fused", LocationManager.NETWORK_PROVIDER).filter { it in available }
    val cached = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter(::fresh).maxByOrNull { it.elapsedRealtimeNanos }
    if (cached != null) {
        finish(cached)
    } else {
        val subscribed = providers.map { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.isSuccess
        }.any { it }
        if (subscribed) {
            main.postDelayed({ finish(null) }, 15_000L)
        } else {
            finish(null)
        }
    }
    return ::cancel
}

