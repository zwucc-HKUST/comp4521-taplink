package hk.taplink.studio

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

class WidgetRefreshJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.jobId
        synchronized(Companion) { started.add(id) }
        running[id] = scope.launch {
            runInterruptible(Dispatchers.IO) {
                val app = applicationContext
                TapWalletWidget.refresh(app, { schedule(app, id + 1) }) { TapWalletWidget.updateAll(app, it) }
            }
            running.remove(id)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        internal const val FIRST_JOB_ID = 0x574700
        private val delaysMillis = longArrayOf(10_000L, 30_000L, 90_000L, 270_000L)
        private val started = mutableSetOf<Int>()

        @Synchronized
        internal fun schedule(context: Context, id: Int = FIRST_JOB_ID) {
            val delay = delaysMillis.getOrNull(id - FIRST_JOB_ID) ?: return
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            // Keep the original delay for queued work. An already-started job might have published
            // before this failure and be about to finish, so give the new failure its own delivery.
            if (id !in started && scheduler.getPendingJob(id) != null) return
            val job = JobInfo.Builder(id, ComponentName(context, WidgetRefreshJob::class.java))
                .setPersisted(true)
                .setMinimumLatency(delay)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            if (scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) {
                started.remove(id)
            } else {
                Log.w("TapWalletWidget", "Android declined widget refresh job $id")
            }
        }
    }
}
