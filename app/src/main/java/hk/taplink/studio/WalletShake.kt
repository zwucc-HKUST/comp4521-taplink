package hk.taplink.studio

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private tailrec fun Context.walletActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.walletActivity()
    else -> null
}

/** Register only for a visible, resumed dashboard with the user's gesture preference enabled. */
@Composable
internal fun rememberWalletShakeListener(enabled: Boolean, onShake: () -> Unit): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.walletActivity() }
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val currentOnShake by rememberUpdatedState(onShake)
    DisposableEffect(enabled, activity, manager, sensor) {
        if (!enabled || activity == null || manager == null || sensor == null) {
            onDispose { }
        } else {
            var registered = false
            var lastShakeNs = 0L
            var settled = true
            val gravitySquared = SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!registered) return
                    val (x, y, z) = event.values
                    val gSquared = (x * x + y * y + z * z) / gravitySquared
                    if (gSquared < 1.4f * 1.4f) settled = true
                    if (settled && gSquared > 2.5f * 2.5f && event.timestamp - lastShakeNs > 1_500_000_000L) {
                        settled = false
                        lastShakeNs = event.timestamp
                        currentOnShake()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            fun stop() {
                if (registered) manager.unregisterListener(listener)
                registered = false
                settled = true
            }
            fun syncRegistration() {
                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    if (!registered) registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                } else {
                    stop()
                }
            }
            val observer = LifecycleEventObserver { _, _ -> syncRegistration() }
            activity.lifecycle.addObserver(observer)
            syncRegistration()
            onDispose {
                activity.lifecycle.removeObserver(observer)
                stop()
            }
        }
    }
    return activity != null && sensor != null
}
