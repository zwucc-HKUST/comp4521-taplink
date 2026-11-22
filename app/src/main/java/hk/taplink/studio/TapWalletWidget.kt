package hk.taplink.studio

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import java.util.concurrent.Executors

class TapWalletWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val completion = goAsync()
        val app = context.applicationContext
        val versionAtRequest = synchronized(lock) { version }
        reader.execute {
            try {
                var display = Display()
                Platform.guarded("load the widget card") {
                    display = Store(app).use { fromWallet(it.load()) }
                }
                synchronized(lock) {
                    // A foreground preference/card change wins over an older database read.
                    val current = if (versionAtRequest == version) display else latestDisplay
                    // This broadcast may address only a subset of installed widgets.
                    published = null
                    Platform.guarded("refresh the wallet widget") { render(app, manager, appWidgetIds, current) }
                }
            } finally {
                completion.finish()
            }
        }
    }

    companion object {
        private val reader = Executors.newSingleThreadExecutor()
        private val lock = Any()
        private var version = 0L
        private var latestDisplay = Display()
        private var published: Display? = null
        private data class Display(val name: String = "Open wallet", val balance: String = "Balance hidden")

        private fun fromWallet(wallet: Wallet): Display {
            val card = wallet.cards.find { it.id == wallet.prefs.selected } ?: return Display()
            return Display(card.name, if (wallet.prefs.hidden) "Balance hidden" else money(card.cents))
        }

        internal fun sync(context: Context, wallet: Wallet) {
            val display = fromWallet(wallet)
            synchronized(lock) {
                version++
                latestDisplay = display
                if (published == display) return
                Platform.guarded("update the wallet widget") {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(ComponentName(context, TapWalletWidget::class.java))
                    if (ids.isNotEmpty()) render(context, manager, ids, display)
                    published = display
                }
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, display: Display) {
            val views = RemoteViews(context.packageName, R.layout.tap_wallet_widget)
            views.setTextViewText(R.id.tap_widget_card, display.name)
            views.setTextViewText(R.id.tap_widget_balance, display.balance)
            views.setOnClickPendingIntent(R.id.tap_widget_root, Platform.dashboardIntent(context))
            manager.updateAppWidget(ids, views)
        }
    }
}
