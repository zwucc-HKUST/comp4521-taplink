package hk.taplink.studio

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtrasInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "extras-test-${UUID.randomUUID()}.db"
    private val hongKong = ZoneId.of("Asia/Hong_Kong")
    private lateinit var store: Store
    private var cardId = 0L

    @Before
    fun openDatabase() {
        store = Store(context, databaseName)
        store.changePrefs { it.copy(auto = false, alerts = false) }
        store.addCard("Extras test")
        cardId = store.load().cards.single { it.name == "Extras test" }.id
    }

    @After
    fun deleteDatabase() {
        try {
            if (::store.isInitialized) store.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun exactFareTriggersOnePaymentAndReloadEvenAfterPurchaseReplay() {
        val day = LocalDate.now(hongKong)
        store.changePrefs { it.copy(auto = true, threshold = 1, reload = 10_000) }
        topUp(5_999)
        assertRejectedWithoutChanges { store.buyExtrasPass(cardId, day, "buy") }
        topUp(1)
        val before = store.load()
        assertEquals(6_000L, before.cards.single { it.id == cardId }.cents)

        store.buyExtrasPass(cardId, day, "buy")
        val paid = store.load()
        val posted = paid.entries.filter { it.cardId == cardId } - before.entries.toSet()
        assertEquals(2, posted.size)
        val payment = posted.single { it.kind == "Payment" }
        val reload = posted.single { it.kind == "Reload" }
        assertEquals(-6_000L, payment.delta)
        assertEquals(0L, payment.after)
        assertEquals("Transport", payment.category)
        assertEquals(payment.operation, reload.operation)
        assertEquals(10_000L, reload.delta)
        assertEquals(10_000L, reload.after)
        assertEquals(reload.after, paid.cards.single { it.id == cardId }.cents)
        val pass = checkNotNull(store.readExtras(cardId).pass)
        assertEquals(day, pass.start)
        assertEquals(day.plusMonths(1), pass.endExclusive)
        assertFalse(pass.covers(day.minusDays(1)))
        assertTrue(pass.covers(day))
        assertTrue(pass.covers(pass.endExclusive.minusDays(1)))
        assertFalse(pass.covers(pass.endExclusive))

        reopen()
        store.buyExtrasPass(cardId, day, "buy")
        assertEquals(paid, store.load())
        assertEquals(pass, store.readExtras(cardId).pass)
        // The reload left enough money for a second pass, so only the active pass blocks it.
        assertRejectedWithoutChanges { store.buyExtrasPass(cardId, day, "another-intent") }
    }

    @Test
    fun staleReviewAndFrozenCardFailAndExpiredPassCanRenewOnce() {
        val day = LocalDate.now(hongKong)
        topUp(12_000)
        assertRejectedWithoutChanges { store.buyExtrasPass(cardId, day.minusDays(1), "stale-review") }
        store.freeze(cardId, true)
        assertRejectedWithoutChanges { store.buyExtrasPass(cardId, day, "original") }
        store.freeze(cardId, false)
        store.buyExtrasPass(cardId, day, "original")

        // Move the purchased pass to a prior period whose exclusive endpoint is today.
        val previousStart = day.minusMonths(1)
        val dates = ContentValues().apply {
            put("start_day", previousStart.toEpochDay())
            put("end_day", day.toEpochDay())
        }
        assertEquals(1, store.writableDatabase.update("extras_pass", dates, "card_id=?", arrayOf(cardId.toString())))
        val expired = checkNotNull(store.readExtras(cardId).pass)
        assertFalse(expired.covers(day))
        val beforeRenewal = store.load()
        store.buyExtrasPass(cardId, previousStart, "original")
        assertEquals(beforeRenewal, store.load())
        assertEquals(expired, store.readExtras(cardId).pass)

        store.buyExtrasPass(cardId, day, "renew")
        val renewed = store.load()
        assertEquals(2, renewed.entries.count { it.cardId == cardId && it.kind == "Payment" })
        assertEquals(0L, renewed.cards.single { it.id == cardId }.cents)
        assertEquals(ExtrasPass(day, day.plusMonths(1)), store.readExtras(cardId).pass)
        store.buyExtrasPass(cardId, day, "renew")
        assertEquals(renewed, store.load())
    }

    @Test
    fun subsidyUsesHongKongMonthAndCreditsTheExactThresholdOnce() {
        val month = YearMonth.now(hongKong)
        val start = month.atDay(1).atStartOfDay(hongKong).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(hongKong).toInstant().toEpochMilli()
        topUp(20_000)
        payAt(start - 1, 1)
        payAt(end, 1)
        payAt(start, 1, category = "Dining")
        payAt(start, 5_999)
        assertEquals(5_999L, extrasTransportSpend(store.load(), cardId, month))
        assertRejectedWithoutChanges { store.claimExtrasSubsidy(cardId, month) }
        payAt(end - 1, 1)
        assertEquals(6_000L, extrasTransportSpend(store.load(), cardId, month))
        assertRejectedWithoutChanges { store.claimExtrasSubsidy(cardId, month.minusMonths(1)) }
        assertRejectedWithoutChanges { store.claimExtrasSubsidy(cardId, month.plusMonths(1)) }
        store.freeze(cardId, true)
        assertRejectedWithoutChanges { store.claimExtrasSubsidy(cardId, month) }
        store.freeze(cardId, false)
        val before = store.load()

        store.claimExtrasSubsidy(cardId, month)
        val credited = store.load()
        val entry = (credited.entries - before.entries.toSet()).single()
        val balance = before.cards.single { it.id == cardId }.cents + 1_200
        assertEquals(cardId, entry.cardId)
        assertEquals("Top-up", entry.kind)
        assertEquals(1_200L, entry.delta)
        assertEquals(balance, entry.after)
        assertEquals(balance, credited.cards.single { it.id == cardId }.cents)
        val claim = store.readExtras(cardId).claims.single()
        assertEquals(month, claim.month)
        assertEquals(6_000L, claim.spend)
        assertEquals(entry.delta, claim.credit)

        reopen()
        store.claimExtrasSubsidy(cardId, month)
        assertEquals(credited, store.load())
        assertEquals(listOf(claim), store.readExtras(cardId).claims)
    }

    @Test
    fun lastMonthsClaimStaysInHistoryAndANewPassQualifiesThisMonth() {
        val day = LocalDate.now(hongKong)
        val month = YearMonth.from(day)
        val previous = month.minusMonths(1)
        val lastMonthEnd = month.atDay(1).atStartOfDay(hongKong).toInstant().toEpochMilli() - 1
        topUp(12_000)
        payAt(lastMonthEnd - 1, 6_000)
        val priorCredit = store.transact(cardId, "Top-up", 1_200, "Fictional transport subsidy", "Subsidy",
            "extras.claim.$cardId.$previous")
        dateEntry(priorCredit, lastMonthEnd)
        // The public claim action accepts only the current month. Seed one completed historical claim.
        store.readExtras(cardId)
        store.writableDatabase.execSQL(
            "INSERT INTO extras_claim(card_id, month, spend, credit, claimed_at, entry_id) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(cardId, previous.toString(), 6_000L, 1_200L, lastMonthEnd, priorCredit)
        )
        assertRejectedWithoutChanges { store.claimExtrasSubsidy(cardId, month) }

        store.buyExtrasPass(cardId, day, "this-month")
        store.claimExtrasSubsidy(cardId, month)
        val claims = store.readExtras(cardId).claims
        assertEquals(listOf(month, previous), claims.map { it.month })
        assertEquals(ExtrasClaim(previous, 6_000, 1_200, lastMonthEnd), claims.last())
        assertEquals(6_000L, claims.first().spend)
        assertEquals(1_200L, claims.first().credit)
        assertEquals(2_400L, store.load().cards.single { it.id == cardId }.cents)
    }

    @Test
    fun failedPassInsertRollsBackNestedPaymentAndReload() {
        val day = LocalDate.now(hongKong)
        store.changePrefs { it.copy(auto = true, threshold = 1, reload = 10_000) }
        topUp(6_000)
        val before = store.load()
        val records = store.readExtras(cardId)
        store.writableDatabase.execSQL(
            """CREATE TRIGGER reject_pass BEFORE INSERT ON extras_pass
               BEGIN SELECT RAISE(ABORT, 'forced pass insert failure'); END"""
        )
        try {
            store.buyExtrasPass(cardId, day, "retry")
            fail("Expected the pass insert to fail")
        } catch (error: SQLiteConstraintException) {
            assertTrue(error.message.orEmpty().contains("forced pass insert failure"))
        }
        reopen()
        assertEquals(before, store.load())
        assertEquals(records, store.readExtras(cardId))
        store.writableDatabase.execSQL("DROP TRIGGER reject_pass")
        store.buyExtrasPass(cardId, day, "retry")
        assertEquals(day, store.readExtras(cardId).pass?.start)
    }

    @Test
    fun couponIsRedeemableThroughExpiryAndStaysRedeemedAfterReopen() {
        val day = LocalDate.now(hongKong)
        val offers = store.readExtras(cardId).coupons
        val lastDayId = offers.first().offer.id
        val expiredId = offers[1].offer.id
        setCouponExpiry(lastDayId, day)
        setCouponExpiry(expiredId, day.minusDays(1))
        val before = store.load()
        assertRejectedWithoutChanges { store.redeemExtrasCoupon(expiredId) }
        assertNull(store.readExtras(cardId).coupons.single { it.offer.id == expiredId }.redeemedAt)
        store.favoriteExtrasOffer(lastDayId, true)
        store.redeemExtrasCoupon(lastDayId)
        val redeemed = store.readExtras(cardId).coupons.single { it.offer.id == lastDayId }
        assertTrue(redeemed.favorite)
        assertEquals(day, redeemed.expires)
        assertNotNull(redeemed.redeemedAt)

        reopen()
        assertEquals(redeemed, store.readExtras(cardId).coupons.single { it.offer.id == lastDayId })
        assertRejectedWithoutChanges { store.redeemExtrasCoupon(lastDayId) }
        store.favoriteExtrasOffer(lastDayId, false)
        reopen()
        assertEquals(redeemed.copy(favorite = false), store.readExtras(cardId).coupons.single { it.offer.id == lastDayId })
        assertEquals(before, store.load())
    }

    private fun reopen() {
        store.close()
        store = Store(context, databaseName)
    }

    private fun topUp(cents: Long) {
        store.transact(cardId, "Top-up", cents, "Test funding", "Funding", UUID.randomUUID().toString())
    }

    private fun payAt(millis: Long, cents: Long, category: String = "Transport") {
        val id = store.transact(cardId, "Payment", cents, "Test fare", category, UUID.randomUUID().toString())
        dateEntry(id, millis)
    }

    private fun dateEntry(id: Long, millis: Long) {
        val timestamp = ContentValues().apply { put("at", millis) }
        assertEquals(1, store.writableDatabase.update("entries", timestamp, "id=?", arrayOf(id.toString())))
    }

    private fun setCouponExpiry(id: String, date: LocalDate) {
        val values = ContentValues().apply { put("expires_day", date.toEpochDay()) }
        assertEquals(1, store.writableDatabase.update("extras_coupon", values, "offer_id=?", arrayOf(id)))
    }

    private fun assertRejectedWithoutChanges(action: () -> Unit) {
        val before = store.load()
        val records = store.readExtras(cardId)
        try {
            action()
            fail("Expected the operation to be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(before, store.load())
            assertEquals(records, store.readExtras(cardId))
        }
    }
}
