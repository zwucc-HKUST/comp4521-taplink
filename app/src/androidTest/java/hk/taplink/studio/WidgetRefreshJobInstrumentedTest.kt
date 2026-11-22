package hk.taplink.studio

import android.Manifest
import android.app.job.JobScheduler
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRefreshJobInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun bothPublicationsFailThenScheduledServiceRedactsTheHostedWidgetUsingCurrentPrefs() {
        val manager = AppWidgetManager.getInstance(context)
        val scheduler = requireNotNull(context.getSystemService(JobScheduler::class.java))
        val host = AppWidgetHost(context, 0x7157)
        val provider = ComponentName(context, TapWalletWidget::class.java)
        val jobId = WidgetRefreshJob.FIRST_JOB_ID
        val automation = instrumentation.uiAutomation
        Store(context).use { store ->
            val original = store.load()
            val selected = original.cards.single { it.id == original.prefs.selected }
            val id = host.allocateAppWidgetId()
            try {
                // While setting up the test, keep the wallet-wide mask over the saved widget opt-in.
                store.changePrefs { it.copy(hidden = true, widgetBalanceVisible = true) }
                automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
                try {
                    assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider))
                } finally {
                    automation.dropShellPermissionIdentity()
                }
                val view = onMain {
                    host.startListening()
                    host.createView(context, id, manager.getAppWidgetInfo(id))
                }
                fun content() = onMain {
                    view.findViewById<TextView>(R.id.tap_widget_card)?.text.toString() to
                        view.findViewById<TextView>(R.id.tap_widget_balance)?.text.toString()
                }
                // Wait for the binding broadcast's database refresh before simulating an exposed old host.
                awaitCondition { content() == (selected.name to "Balance hidden") }
                val old = Wallet(listOf(Card(1, "Previous widget card", 5200, false)), emptyList(), emptyList(),
                    Prefs(widgetBalanceVisible = true))
                TapWalletWidget.render(context, TapWalletWidget.fromWallet(old)) { manager.updateAppWidget(id, it) }
                awaitCondition { content() == ("Previous widget card" to "HK$52.00") }

                val rejected = IllegalStateException("Widget host unavailable")
                var attempts = 0
                val result = runCatching {
                    TapWalletWidget.render(context, TapWalletWidget.fromWallet(old)) {
                        attempts++
                        throw rejected
                    }
                }
                assertSame(rejected, result.exceptionOrNull())
                assertEquals(2, attempts)
                val pending = requireNotNull(scheduler.getPendingJob(jobId))
                assertEquals(ComponentName(context, WidgetRefreshJob::class.java), pending.service)
                assertTrue(pending.isPersisted)
                assertTrue(pending.minLatencyMillis > 0)
                assertEquals("HK$52.00", content().second)

                // Revoke the opt-in and release the master mask while Android owns the retry.
                // Write directly so the scheduled service is the only publisher that can repair the host.
                store.changePrefs { it.copy(widgetBalanceVisible = false, hidden = false) }
                val user = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("am get-current-user"))
                    .bufferedReader().use { it.readText().trim().toInt() }
                val shell = automation.executeShellCommand("cmd jobscheduler run -f --user $user ${context.packageName} $jobId")
                ParcelFileDescriptor.AutoCloseInputStream(shell).bufferedReader().use { it.readText() }
                awaitCondition {
                    content() == (selected.name to "Balance hidden") && scheduler.getPendingJob(jobId) == null
                }
                assertEquals(null, scheduler.getPendingJob(jobId + 1))
            } finally {
                onMain { host.stopListening() }
                host.deleteHost()
                store.changePrefs { it.copy(hidden = original.prefs.hidden, widgetBalanceVisible = original.prefs.widgetBalanceVisible) }
                TapWalletWidget.sync(context, store.load())
            }
        }
    }

    private fun <T> onMain(action: () -> T): T {
        val task = FutureTask(Callable { action() })
        instrumentation.runOnMainSync(task)
        return task.get()
    }

    private fun awaitCondition(check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!check()) {
            assertTrue("Timed out waiting for the widget host or job scheduler", SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(50)
        }
    }
}
