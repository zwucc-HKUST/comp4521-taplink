package hk.taplink.studio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.*

class WalletController(private val app: Application) : AndroidViewModel(app) {
    var wallet by mutableStateOf<Wallet?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    fun clearError() { error = null }

    init { refresh() }
    fun refresh() = run({ Unit })

    fun <T> run(action: Store.() -> T, done: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val value = try {
                    val (result, snapshot) = withContext(Dispatchers.IO) {
                        Store(app).use { it.applyAndLoad(action) }
                    }
                    wallet = snapshot
                    Platform.guarded("publish wallet updates") { Platform.sync(app, snapshot) }
                    result.getOrThrow()
                } finally {
                    busy = false
                }
                done(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Could not open the wallet."
            }
        }
    }
}
