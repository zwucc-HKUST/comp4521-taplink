package hk.taplink.studio

import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "wallet-test-${UUID.randomUUID()}.db"
    private lateinit var store: Store

    @Before
    fun openStore() {
        store = Store(context, dbName)
    }

    @After
    fun cleanUp() {
        store.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun openingLedgerReconcilesPerCardAndReopenPreservesTheSnapshot() {
        val opening = store.load()
        assertTrue(opening.cards.size >= 3)
        assertTrue(opening.cards.any { it.id == opening.prefs.selected })
        assertTrue(opening.entries.any { it.kind == "Payment" })
        for (card in opening.cards) {
            val history = opening.entries.filter { it.cardId == card.id }.reversed()
            assertEquals("Top-up", history.first().kind)
            var running = 0L
            for (entry in history) {
                running += entry.delta
                assertEquals(running, entry.after)
                assertTrue(entry.after >= 0)
            }
            assertEquals(running, card.cents)
        }
        reopen()
        assertEquals(opening, store.load())
    }

    @Test
    fun cardEditsAndAllPreferencesSurviveRestartAndInvalidEditsLeaveThemIntact() {
        val originalCards = store.load().cards
        val id = newCard()
        store.rename(id, "  Field trips  ")
        store.freeze(id, true)
        store.select(id)
        val expected = Prefs(selected = id, threshold = 315, reload = 490, auto = true,
            alerts = false, hidden = true, theme = "Dark", reminders = false, shake = true)
        store.changePrefs { expected.copy(selected = it.selected) }
        reopen()
        val saved = store.load()
        assertEquals(expected, saved.prefs)
        assertEquals(originalCards, saved.cards.filter { it.id != id })
        assertEquals(Card(id, "Field trips", 0, true), saved.cards.single { it.id == id })

        val missing = saved.cards.maxOf { it.id } + 1
        rejected { store.select(missing) }
        rejected { store.rename(missing, "Missing") }
        rejected { store.freeze(missing, false) }
        rejected { store.transact(missing, "Top-up", 1, "Demo", "Funding", "absent") }
        rejected { store.rename(id, "  ") }
        rejected { store.addCard("") }
        rejected { store.changePrefs { it.copy(selected = missing, hidden = false) } }
        rejected { store.changePrefs { it.copy(threshold = -1) } }
        rejected { store.changePrefs { it.copy(reload = 0) } }
        assertEquals(saved, store.load())
        store.freeze(id, false)
        assertEquals(false, store.load().cards.single { it.id == id }.frozen)
    }

    @Test
    fun topUpCreditsOnlyTheAddressedCardAndReplayReturnsItsStoredReceipt() {
        val id = newCard()
        val before = store.load()
        val receipt = store.transact(id, "Top-up", 1_025, "Demo credit", "Funding", "deposit")
        val saved = store.load()
        val entry = (saved.entries - before.entries.toSet()).single()
        assertEquals(receipt, entry.id)
        assertEquals(id, entry.cardId)
        assertEquals("Top-up", entry.kind)
        assertEquals(1_025L, entry.delta)
        assertEquals(1_025L, entry.after)
        assertEquals(entry.after, saved.cards.single { it.id == id }.cents)
        assertEquals(before.cards.filter { it.id != id }, saved.cards.filter { it.id != id })
        assertEquals(before.notes, saved.notes)
        reopen()
        assertEquals(receipt, store.transact(id, "Top-up", 1_025, "Demo credit", "Funding", "deposit"))
        assertEquals(saved, store.load())
    }

    @Test
    fun paymentAndOneReloadKeepTheirOwnBalancesAndReplayReturnsThePrimaryReceipt() {
        val id = newCard(1_000)
        store.changePrefs { it.copy(threshold = 900, reload = 200, auto = true, alerts = true) }
        val before = store.load()
        val receipt = pay(id, 750, "checkout")
        val posted = store.load()
        val (payment, reload) = posted.entries.filter { it.operation == "checkout" }.sortedBy { it.id }
        assertEquals(before.entries.size + 2, posted.entries.size)
        assertEquals(receipt, payment.id)
        assertEquals("Payment", payment.kind)
        assertEquals(-750L, payment.delta)
        assertEquals(250L, payment.after)
        assertEquals("Reload", reload.kind)
        assertEquals(200L, reload.delta)
        assertEquals(450L, reload.after)
        assertEquals(id, reload.cardId)
        assertEquals(payment.at, reload.at)
        assertEquals(450L, posted.cards.single { it.id == id }.cents)
        assertEquals(before.cards.filter { it.id != id }, posted.cards.filter { it.id != id })
        val note = (posted.notes - before.notes.toSet()).single()
        assertTrue(note.text.contains(money(450)))

        store.freeze(id, true)
        store.changePrefs { it.copy(auto = false, alerts = false) }
        val frozen = store.load()
        reopen()
        assertEquals(receipt, pay(id, 750, "checkout"))
        assertEquals(frozen, store.load())
    }

    @Test
    fun sameOperationKeyCannotBeReusedWithDifferentIntent() {
        val id = newCard(1_000)
        val other = newCard()
        val original = pay(id, 100, "review")
        val saved = store.load()
        rejected { pay(other, 100, "review") }
        rejected { pay(id, 101, "review") }
        rejected { store.transact(id, "Top-up", 100, "Test cafe", "Dining", "review") }
        rejected { store.transact(id, "Payment", 100, "Other cafe", "Dining", "review") }
        rejected { store.transact(id, "Payment", 100, "Test cafe", "Transport", "review") }
        assertEquals(original, pay(id, 100, "review"))
        assertEquals(saved, store.load())
    }

    @Test
    fun reloadAndAlertUseStrictThresholdsAndAlertUsesFinalBalance() {
        val id = newCard(1_000)
        store.changePrefs { it.copy(threshold = 500, reload = 10, auto = true, alerts = true) }
        val initial = store.load()
        pay(id, 500, "at-threshold")  // 500, so no reload.
        pay(id, 10, "restored")       // 490 then 500, so no alert.
        val restored = store.load()
        assertEquals(listOf("Payment"), restored.entries.filter { it.operation == "at-threshold" }.map { it.kind })
        assertEquals(listOf("Reload", "Payment"), restored.entries.filter { it.operation == "restored" }.map { it.kind })
        assertEquals(500L, restored.cards.single { it.id == id }.cents)
        assertEquals(initial.notes, restored.notes)
        store.changePrefs { it.copy(auto = false, alerts = false) }
        pay(id, 1, "silent")
        assertEquals(initial.notes, store.load().notes)
        store.changePrefs { it.copy(alerts = true, reminders = false) }
        pay(id, 1, "alert")
        val alerted = store.load()
        assertTrue((alerted.notes - initial.notes.toSet()).single().text.contains(money(498)))
    }

    @Test
    fun invalidAndFrozenOperationsLeaveNoTraceAndReloadCannotFinanceAnUnaffordablePayment() {
        val id = newCard(100)
        store.changePrefs { it.copy(auto = true, threshold = 1_000, reload = 10_000) }
        val before = store.load()
        rejected { pay(id, 101, "retry") }
        rejected { pay(id, -1, "negative") }
        rejected { pay(id, 0, "zero") }
        rejected { pay(id, 1, "  ") }
        rejected { store.transact(id, "Top-up", -1, "Demo", "Funding", "credit") }
        rejected { store.transact(id, "Payment", 1, "", "Dining", "merchant") }
        rejected { store.transact(id, "Payment", 1, "Cafe", " ", "category") }
        rejected { store.transact(id, "Unknown", 1, "Cafe", "Dining", "unknown") }
        assertEquals(before, store.load())
        store.freeze(id, true)
        val frozen = store.load()
        rejected { pay(id, 50, "frozen") }
        rejected { store.transact(id, "Top-up", 1, "Demo", "Funding", "blocked") }
        assertEquals(frozen, store.load())
        store.freeze(id, false)
        store.transact(id, "Top-up", 1, "Demo", "Funding", "credit")
        val receipt = pay(id, 101, "retry")
        assertEquals(0L, store.load().entries.single { it.id == receipt }.after)
    }

    @Test
    fun overflowingTopUpOrAutomaticReloadDoesNotWrapOrLeaveAPartialDebit() {
        val id = newCard(100)
        store.changePrefs { it.copy(auto = true, threshold = 100, reload = Long.MAX_VALUE) }
        val before = store.load()
        rejected { store.transact(id, "Top-up", Long.MAX_VALUE, "Demo", "Funding", "large") }
        rejected { pay(id, 1, "overflow") }
        assertEquals(before, store.load())
    }

    @Test
    fun lateDatabaseFailureRollsBackLedgerAndInboxAndAllowsRetryAfterRestart() {
        val id = newCard(1_000)
        store.changePrefs { it.copy(auto = true, threshold = 900, reload = 200) }
        val before = store.load()
        // Abort the final card write after SQLite has accepted both entries and the message.
        store.writableDatabase.execSQL(
            """CREATE TRIGGER fail_settlement BEFORE UPDATE OF cents ON cards
               BEGIN SELECT RAISE(ABORT, 'injected settlement failure'); END"""
        )
        try {
            pay(id, 750, "retry-settlement")
            fail("The injected database failure should reach the caller.")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message.orEmpty().contains("injected settlement failure"))
        }
        reopen()
        assertEquals(before, store.load())
        store.writableDatabase.execSQL("DROP TRIGGER fail_settlement")
        val receipt = pay(id, 750, "retry-settlement")
        val settled = store.load()
        assertEquals(250L, settled.entries.single { it.id == receipt }.after)
        assertEquals(450L, settled.cards.single { it.id == id }.cents)
        assertEquals(before.notes.size + 1, settled.notes.size)
        assertEquals(before.entries.size + 2, settled.entries.size)
    }

    @Test
    fun remindersRespectPreferencesAndKeepTheFirstDeliveryAcrossRestarts() {
        val initial = store.load().notes
        store.changePrefs { it.copy(reminders = false) }
        store.remind("expiry", "Skipped")
        assertEquals(initial, store.load().notes)
        store.changePrefs { it.copy(reminders = true, alerts = false) }
        store.remind("expiry", "Demo pass expires tomorrow.")
        val delivered = store.load().notes
        assertEquals("Demo pass expires tomorrow.", (delivered - initial.toSet()).single().text)
        reopen()
        store.remind("expiry", "Repeated delivery")
        assertEquals(delivered, store.load().notes)
        store.remind("next-expiry", "Another scheduled reminder")
        assertEquals(delivered.size + 1, store.load().notes.size)
    }

    @Test
    fun concurrentHelpersShareOneReceiptAndCannotSpendTheSameBalanceTwice() {
        val id = newCard(1_000)
        val peer = Store(context, dbName)
        val pool = Executors.newFixedThreadPool(2)
        try {
            peer.load()
            fun race(first: String, second: String): List<Long?> {
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val jobs = listOf(store to first, peer to second).map { (writer, key) ->
                    pool.submit(Callable {
                        ready.countDown()
                        check(start.await(10, TimeUnit.SECONDS))
                        try {
                            writer.transact(id, "Payment", 500, "Tram", "Transport", key)
                        } catch (e: IllegalArgumentException) {
                            if (!e.message.orEmpty().contains("insufficient balance")) throw e
                            null
                        }
                    })
                }
                check(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                return jobs.map { it.get(10, TimeUnit.SECONDS) }
            }
            val repeat = race("same", "same")
            assertTrue(repeat.first() != null)
            assertEquals(repeat.first(), repeat.last())
            val distinct = race("ticket-a", "ticket-b")
            assertEquals(1, distinct.filterNotNull().size)
            val settled = store.load()
            assertEquals(0L, settled.cards.single { it.id == id }.cents)
            assertEquals(2, settled.entries.count { it.cardId == id && it.kind == "Payment" })
            assertEquals(settled, peer.load())
        } finally {
            pool.shutdownNow()
            peer.close()
        }
    }

    @Test
    fun snapshotFailureRollsBackTheActionAndItsOperationKey() {
        val id = newCard(100)
        val before = store.load()
        store.writableDatabase.execSQL(
            """CREATE TRIGGER invalidate_snapshot AFTER UPDATE OF cents ON cards
               BEGIN DELETE FROM prefs; END"""
        )
        try {
            store.applyAndLoad { transact(id, "Payment", 10, "Tram", "Transport", "reviewed") }
            fail("Publishing this action requires readable wallet preferences.")
        } catch (expected: IllegalStateException) {
            assertEquals("Wallet preferences are missing.", expected.message)
        }
        reopen()
        assertEquals(before, store.load())
        store.writableDatabase.execSQL("DROP TRIGGER invalidate_snapshot")
        val (receipt, posted) = store.applyAndLoad { transact(id, "Payment", 10, "Tram", "Transport", "reviewed") }
        assertEquals(receipt.getOrThrow(), posted.entries.single { it.operation == "reviewed" }.id)
        assertEquals(posted, store.load())
    }

    @Test
    fun rejectedConfirmationReturnsTheOtherWritersCommittedBalance() {
        val id = newCard(100)
        Store(context, dbName).use { peer ->
            peer.transact(id, "Payment", 90, "Tram", "Transport", "other-review")
        }
        val (attempt, snapshot) = store.applyAndLoad { transact(id, "Payment", 90, "Cafe", "Dining", "my-review") }
        assertTrue(attempt.exceptionOrNull() is IllegalArgumentException)
        assertEquals(10L, snapshot.cards.single { it.id == id }.cents)
        assertTrue(snapshot.entries.none { it.operation == "my-review" })
        assertEquals(store.load(), snapshot)
    }

    @Test
    fun hidingBalancesMasksExistingAlertsButPreservesRemindersAndOriginalAmounts() {
        val id = newCard(200)
        store.changePrefs { it.copy(threshold = 150, auto = false, alerts = true) }
        pay(id, 100, "low")
        store.remind("other", "Review HK$1.00 in transport spending.")
        val visible = store.load().notes
        assertTrue(visible.any { it.text.contains("Low balance") && it.text.contains(money(100)) })
        store.changePrefs { it.copy(hidden = true) }
        reopen()
        val hidden = store.load().notes
        assertEquals(visible.map { it.id }, hidden.map { it.id })
        assertTrue(hidden.any { it.text.contains("below its low-balance threshold") })
        assertTrue(hidden.any { it.text == "Review HK$1.00 in transport spending." })
        assertTrue(hidden.none { it.text.contains("Low balance on") })
        store.changePrefs { it.copy(hidden = false) }
        assertEquals(visible, store.load().notes)
    }

    @Test
    fun competingPassPurchasesAndMonthlyClaimsReconcileAfterRestart() {
        val id = newCard(extrasPassPrice * 2)
        val today = LocalDate.now(extrasZone)
        val month = YearMonth.from(today)
        val pool = Executors.newFixedThreadPool(2)
        Store(context, dbName).use { peer ->
            try {
                peer.load()
                fun <T> race(action: (Store, String) -> T): List<T> {
                    val ready = CountDownLatch(2)
                    val start = CountDownLatch(1)
                    val jobs = listOf(store to "one", peer to "two").map { (writer, token) ->
                        pool.submit(Callable {
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS))
                            action(writer, token)
                        })
                    }
                    check(ready.await(10, TimeUnit.SECONDS))
                    start.countDown()
                    return jobs.map { it.get(10, TimeUnit.SECONDS) }
                }
                val winner = race { writer, token ->
                    try {
                        writer.buyExtrasPass(id, today, token)
                        token
                    } catch (expected: IllegalArgumentException) {
                        assertEquals("This card already has a current pass.", expected.message)
                        null
                    }
                }.filterNotNull().single()
                peer.buyExtrasPass(id, today, winner)
                race { writer, _ -> writer.claimExtrasSubsidy(id, month) }
            } finally {
                pool.shutdownNow()
            }
        }
        reopen()
        val wallet = store.load()
        val extras = store.readExtras(id)
        assertTrue(extras.pass!!.covers(today))
        assertEquals(month, extras.month)
        assertEquals(extrasPassPrice, extras.transportSpend)
        assertEquals(extrasPassPrice, extras.claims.single().spend)
        assertEquals(extrasClaimCredit, extras.claims.single().credit)
        assertEquals(wallet.cards.single { it.id == id }, extras.card)
        assertEquals(extrasPassPrice + extrasClaimCredit, extras.card.cents)
        assertEquals(1, wallet.entries.count { it.cardId == id && it.kind == "Payment" })
        assertEquals(1, wallet.entries.count { it.cardId == id && it.category == "Subsidy" })
    }

    private fun newCard(cents: Long = 0): Long {
        store.addCard("Test card")
        val id = store.load().cards.maxOf { it.id }
        if (cents > 0) {
            store.transact(id, "Top-up", cents, "Demo credit", "Funding", "opening/$id")
        }
        return id
    }

    private fun pay(id: Long, cents: Long, key: String) =
        store.transact(id, "Payment", cents, "Test cafe", "Dining", key)

    private fun reopen() {
        store.close()
        store = Store(context, dbName)
    }

    private fun rejected(action: () -> Unit) {
        try {
            action()
            fail("Expected the store to reject the operation.")
        } catch (_: IllegalArgumentException) {
            // Validation failures must use the public input-error contract.
        }
    }
}
