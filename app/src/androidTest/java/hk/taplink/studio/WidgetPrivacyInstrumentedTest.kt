package hk.taplink.studio

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.FutureTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPrivacyInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dbName = "widget-privacy-${UUID.randomUUID()}.db"

    @After fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test fun openingPopulatedV1UpgradesInPlaceWithoutGrantingWidgetConsent() {
        val sql = instrumentation.context.assets.open("astra_v1_widget_migration.sql")
            .bufferedReader().use { it.readText() }
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            for (statement in sql.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
                db.execSQL(statement)
            }
            assertEquals(1, db.version)
            db.rawQuery("SELECT * FROM prefs", null).use {
                assertEquals(-1, it.getColumnIndex("widget_balance_visible"))
            }
        }

        val upgraded = Store(context, dbName).use { store ->
            val wallet = store.load()
            assertEquals(2, store.writableDatabase.version)
            assertEquals(Prefs(selected = 17, threshold = 315, reload = 490, auto = true,
                alerts = false, hidden = false, theme = "Dark", reminders = false, shake = true,
                widgetBalanceVisible = false), wallet.prefs)
            assertEquals("Balance hidden", TapWalletWidget.fromWallet(wallet).balance)
            assertEquals(listOf(Card(5, "Reserve", 2500, true), Card(17, "Travel", 5200, false)), wallet.cards)
            assertEquals(listOf(74L, 73L, 72L, 71L), wallet.entries.map { it.id })
            assertEquals(73L, store.transact(17, "Payment", 6000, "Demo monthly pass", "Transport", "extras.pass.17.fixture"))
            assertEquals(listOf(Note(31, "Check your pass.", 1704067203000L)), wallet.notes)
            val extras = store.readExtras(17)
            assertEquals(ExtrasPass(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)), extras.pass)
            assertEquals(listOf(ExtrasClaim(YearMonth.of(2024, 1), 6000, 1200, 1704067202000L)), extras.claims)
            val tea = extras.coupons.single { it.offer.id == "tea" }
            assertEquals(LocalDate.ofEpochDay(19743), tea.expires)
            assertTrue(tea.favorite)
            assertEquals(1704067204000L, tea.redeemedAt)
            wallet
        }
        Store(context, dbName).use { assertEquals(upgraded, it.load()) }
    }

    @Test fun firstInstallAndReopenRespectBothSavedPrivacyChoices() {
        Store(context, dbName).use { store ->
            val fresh = store.load()
            assertEquals(2, store.writableDatabase.version)
            assertFalse(fresh.prefs.hidden)
            assertFalse(fresh.prefs.widgetBalanceVisible)
            assertEquals("Balance hidden", TapWalletWidget.fromWallet(fresh).balance)
            store.changePrefs { it.copy(widgetBalanceVisible = true) }
            store.changePrefs { it.copy(hidden = true) }
        }
        Store(context, dbName).use { store ->
            val masked = store.load()
            assertTrue(masked.prefs.widgetBalanceVisible)
            assertTrue(masked.prefs.hidden)
            assertEquals("Balance hidden", TapWalletWidget.fromWallet(masked).balance)

            store.changePrefs { it.copy(hidden = false) }
            val visible = store.load()
            val selected = visible.cards.single { it.id == visible.prefs.selected }
            assertEquals(money(selected.cents), TapWalletWidget.fromWallet(visible).balance)
            assertEquals("Balance hidden", TapWalletWidget.fromWallet(visible.copy(cards = emptyList())).balance)

            store.changePrefs { it.copy(hidden = true) }
            store.changePrefs { it.copy(widgetBalanceVisible = false) }
            assertEquals("Balance hidden", TapWalletWidget.fromWallet(store.load()).balance)
            store.changePrefs { it.copy(hidden = false) }
        }
        Store(context, dbName).use { store ->
            val revoked = store.load()
            assertFalse(revoked.prefs.widgetBalanceVisible)
            assertEquals("Balance hidden", TapWalletWidget.fromWallet(revoked).balance)
        }
    }

    @Test fun failedUpdateReappliesHiddenTextOverAnExposedWidget() {
        val wallet = Wallet(listOf(Card(1, "Travel", 5200, false)), emptyList(), emptyList(),
            Prefs(widgetBalanceVisible = true))
        val onMain = FutureTask<Unit> {
            val host = FrameLayout(context)
            val view = RemoteViews(context.packageName, R.layout.tap_wallet_widget).apply(context, host)
            val balance = view.findViewById<TextView>(R.id.tap_widget_balance)
            assertEquals("Balance hidden", balance.text.toString())
            TapWalletWidget.render(context, TapWalletWidget.fromWallet(wallet)) { it.reapply(context, view) }
            assertEquals("HK$52.00", balance.text.toString())

            val revoked = wallet.copy(prefs = wallet.prefs.copy(widgetBalanceVisible = false))
            val failure = IllegalStateException("First widget update rejected")
            var attempts = 0
            val result = runCatching {
                TapWalletWidget.render(context, TapWalletWidget.fromWallet(revoked)) { update ->
                    if (++attempts == 1) throw failure
                    update.reapply(context, view)
                }
            }
            assertSame(failure, result.exceptionOrNull())
            assertEquals(2, attempts)
            assertEquals("Balance hidden", balance.text.toString())
        }
        instrumentation.runOnMainSync(onMain)
        onMain.get()
    }
}
