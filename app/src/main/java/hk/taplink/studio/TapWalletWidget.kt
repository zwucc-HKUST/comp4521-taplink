package hk.taplink.studio

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import java.util.concurrent.Executors

class TapWalletWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            super.onReceive(context, intent)
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TapWalletWidget::class.java))
        if (ids.isNotEmpty()) onUpdate(context, manager, ids)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val completion = goAsync()
        val app = context.applicationContext
        synchronized(lock) {
            // An upgraded or restored launcher can still hold a previously visible balance.
            published = null
            Platform.guarded("mask the wallet widget") {
                manager.updateAppWidget(appWidgetIds, views(app, Display()))
            }
        }
        reader.execute {
            try {
                refresh(app, { WidgetRefreshJob.schedule(app) }) { manager.updateAppWidget(appWidgetIds, it) }
            } finally {
                completion.finish()
            }
        }
    }

    companion object {
        private val reader = Executors.newSingleThreadExecutor()
        private val lock = Any()
        private var published: Display? = null
        internal data class Display(val name: String = "Open wallet", val balance: String = "Balance hidden")

        internal fun fromWallet(wallet: Wallet): Display {
            val card = wallet.cards.find { it.id == wallet.prefs.selected } ?: return Display()
            return if (wallet.prefs.widgetBalanceVisible && !wallet.prefs.hidden) Display(card.name, money(card.cents))
            else Display(card.name)
        }

        internal fun sync(context: Context, wallet: Wallet) {
            val display = fromWallet(wallet)
            synchronized(lock) {
                if (published == display) return
                published = null
                Platform.guarded("update the wallet widget") {
                    render(context, display) { updateAll(context, it) }
                    published = display
                }
            }
        }

        internal fun updateAll(context: Context, views: RemoteViews) {
            AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, TapWalletWidget::class.java), views)
        }

        internal fun refresh(context: Context, retry: () -> Unit, update: (RemoteViews) -> Unit) {
            fun publish(display: Display) {
                synchronized(lock) {
                    if (Thread.currentThread().isInterrupted) return
                    // A broadcast can cover just one widget, and a fallback can change the host's content.
                    published = null
                    Platform.guarded("refresh the wallet widget") { render(context, display, retry, update) }
                }
            }
            try {
                Store(context).use { store ->
                    val db = store.writableDatabase
                    // Hold the writer lock through submission so a privacy toggle cannot commit between
                    // reading consent and sending the amount to Android. Wallet writes release it before sync().
                    db.beginTransactionNonExclusive()
                    try {
                        publish(fromWallet(store.load()))
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            } catch (failure: Exception) {
                Log.w("TapWalletWidget", "Could not read wallet for widget refresh", failure)
                publish(Display())
            }
        }

        internal fun render(context: Context, display: Display, retry: () -> Unit = { WidgetRefreshJob.schedule(context) },
                            update: (RemoteViews) -> Unit) {
            try {
                val content = views(context, display)
                content.setOnClickPendingIntent(R.id.tap_widget_root, Platform.dashboardIntent(context))
                update(content)
            } catch (failure: Exception) {
                // Redact even if attaching the click action or publishing the full update failed.
                try {
                    update(views(context, Display()))
                } catch (maskFailure: Exception) {
                    Log.w("TapWalletWidget", "Could not mask the widget", maskFailure)
                    retry()
                }
                throw failure
            }
        }

        private fun views(context: Context, display: Display) =
            RemoteViews(context.packageName, R.layout.tap_wallet_widget).apply {
                setTextViewText(R.id.tap_widget_card, display.name)
                setTextViewText(R.id.tap_widget_balance, display.balance)
            }
    }
}
