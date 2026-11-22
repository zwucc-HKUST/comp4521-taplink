package hk.taplink.studio

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.Future

class TapMaintenanceJob : JobService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var task: Future<*>? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val ticket = ++generation
        task = worker.submit {
            Platform.guarded("run maintenance") {
                val today = LocalDate.now(ExploreCatalogue.hongKong)
                Platform.guarded("refresh the demo catalogue") { ExploreCatalogue.refresh(this, today) }
                if (!Thread.currentThread().isInterrupted) runReminders(today)
            }
            main.post {
                if (ticket == generation) {
                    task = null
                    jobFinished(params, false)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        generation++
        task?.cancel(true)
        task = null
        return true
    }

    override fun onDestroy() {
        generation++
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun runReminders(day: LocalDate) {
        Store(applicationContext).use { store ->
            val added = writeReminders(store, day)
            if (added.isEmpty() || Thread.currentThread().isInterrupted || !store.load().prefs.reminders) return
            Platform.post(this, Platform.REMINDERS_CHANNEL, "reminders/${added.maxOf { it.id }}",
                if (added.size == 1) "New wallet reminder" else "New wallet reminders",
                "Open your wallet messages to read the update.")
        }
    }

    private fun writeReminders(store: Store, day: LocalDate): List<Note> {
        val db = store.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val wallet = store.load()
            if (!wallet.prefs.reminders || Thread.currentThread().isInterrupted) return emptyList()
            // This transaction isolates the before/after note IDs from concurrent payments.
            val previous = wallet.notes.mapTo(HashSet()) { it.id }
            val monday = day.minusDays(day.dayOfWeek.value.toLong() - 1)
            val since = day.minusDays(6).atStartOfDay(ExploreCatalogue.hongKong).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            val count = wallet.entries.count { it.kind == "Payment" && it.at in since..now }
            val summary = when (count) {
                0 -> "No demo payments in the past seven days."
                1 -> "One demo payment in the past seven days."
                else -> "$count demo payments in the past seven days."
            }
            store.remind("weekly/$monday", "Weekly wallet review. $summary Open Activity for your spending breakdown.")
            store.remindExtrasExpiries()
            val added = store.load().notes.filter { it.id !in previous }
            db.setTransactionSuccessful()
            return added
        } finally {
            db.endTransaction()
        }
    }
}
