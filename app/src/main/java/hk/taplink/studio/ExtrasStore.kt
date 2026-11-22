package hk.taplink.studio

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal val extrasZone: ZoneId = ZoneId.of("Asia/Hong_Kong")
internal const val extrasPassPrice = 6000L
internal const val extrasClaimThreshold = 6000L
internal const val extrasClaimCredit = 1200L

internal data class ExtrasPass(val start: LocalDate, val endExclusive: LocalDate) {
    fun covers(day: LocalDate) = day >= start && day < endExclusive
}

internal data class ExtrasClaim(val month: YearMonth, val spend: Long, val credit: Long, val at: Long)

internal data class ExtrasOffer(
    val id: String,
    val merchant: String,
    val title: String,
    val details: String,
    val daysAvailable: Long
)

private val extrasCatalog = listOf(
    ExtrasOffer("paper", "Paper Ferry Press", "Pocket sketchbook",
        "Exchange this demo coupon for one pocket sketchbook at the fictional print counter.", 14),
    ExtrasOffer("tea", "Lantern Tea Room", "Second cup of tea",
        "A fictional tea tasting for two. The demo coupon covers the second cup of house tea.", 21),
    ExtrasOffer("tram", "Tramline Studio", "Route postcard set",
        "Choose a pair of illustrated route postcards from the fictional studio collection.", 30)
)

internal data class ExtrasCoupon(
    val offer: ExtrasOffer,
    val expires: LocalDate,
    val favorite: Boolean,
    val redeemedAt: Long?
)

internal data class ExtrasRecords(
    val card: Card,
    val month: YearMonth,
    val transportSpend: Long,
    val pass: ExtrasPass?,
    val claims: List<ExtrasClaim>,
    val coupons: List<ExtrasCoupon>
)

internal fun extrasTransportSpend(wallet: Wallet, cardId: Long, month: YearMonth): Long {
    val start = month.atDay(1).atStartOfDay(extrasZone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(extrasZone).toInstant().toEpochMilli()
    return wallet.entries.asSequence()
        .filter { it.cardId == cardId && it.kind == "Payment" && it.delta < 0 }
        .filter { it.category.equals("Transport", ignoreCase = true) && it.at >= start && it.at < end }
        .fold(0L) { total, entry -> Math.subtractExact(total, entry.delta) }
}

// All writers acquire the same SQLite transaction before checking eligibility. Store.transact
// joins this transaction on the calling thread, including any automatic reload it records.
private fun <T> Store.withExtras(action: SQLiteDatabase.(LocalDate) -> T): T {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val today = LocalDate.now(extrasZone)
        db.execSQL("""CREATE TABLE IF NOT EXISTS extras_pass (
            operation TEXT PRIMARY KEY NOT NULL, card_id INTEGER NOT NULL, entry_id INTEGER NOT NULL UNIQUE,
            start_day INTEGER NOT NULL, end_day INTEGER NOT NULL, UNIQUE(card_id, start_day))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS extras_claim (
            card_id INTEGER NOT NULL, month TEXT NOT NULL, spend INTEGER NOT NULL, credit INTEGER NOT NULL,
            claimed_at INTEGER NOT NULL, entry_id INTEGER NOT NULL UNIQUE, PRIMARY KEY(card_id, month))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS extras_coupon (
            offer_id TEXT PRIMARY KEY NOT NULL, expires_day INTEGER NOT NULL,
            favorite INTEGER NOT NULL DEFAULT 0, redeemed_at INTEGER)""")
        for (offer in extrasCatalog) {
            db.execSQL("INSERT OR IGNORE INTO extras_coupon(offer_id, expires_day) VALUES (?, ?)",
                arrayOf<Any>(offer.id, today.plusDays(offer.daysAvailable - 1).toEpochDay()))
        }
        val result = db.action(today)
        db.setTransactionSuccessful()
        return result
    } finally {
        db.endTransaction()
    }
}

private fun SQLiteDatabase.latestExtrasPass(cardId: Long): ExtrasPass? =
    rawQuery("SELECT start_day, end_day FROM extras_pass WHERE card_id=? ORDER BY end_day DESC LIMIT 1",
        arrayOf(cardId.toString())).use { rows ->
        if (rows.moveToFirst()) ExtrasPass(LocalDate.ofEpochDay(rows.getLong(0)),
            LocalDate.ofEpochDay(rows.getLong(1))) else null
    }

private fun SQLiteDatabase.extrasCoupons(): List<ExtrasCoupon> {
    val offers = extrasCatalog.associateBy { it.id }
    val result = mutableListOf<ExtrasCoupon>()
    rawQuery("SELECT offer_id, expires_day, favorite, redeemed_at FROM extras_coupon ORDER BY expires_day, offer_id",
        null).use { rows ->
        while (rows.moveToNext()) {
            val offer = offers[rows.getString(0)] ?: continue
            result += ExtrasCoupon(offer, LocalDate.ofEpochDay(rows.getLong(1)),
                rows.getInt(2) != 0, if (rows.isNull(3)) null else rows.getLong(3))
        }
    }
    return result
}

internal fun Store.readExtras(cardId: Long): ExtrasRecords = withExtras { today ->
    val wallet = load()
    val card = requireNotNull(wallet.cards.find { it.id == cardId }) { "Card does not exist." }
    val month = YearMonth.from(today)
    val claims = mutableListOf<ExtrasClaim>()
    rawQuery("SELECT month, spend, credit, claimed_at FROM extras_claim WHERE card_id=? ORDER BY month DESC",
        arrayOf(cardId.toString())).use { rows ->
        while (rows.moveToNext()) {
            claims += ExtrasClaim(YearMonth.parse(rows.getString(0)), rows.getLong(1),
                rows.getLong(2), rows.getLong(3))
        }
    }
    ExtrasRecords(card, month, extrasTransportSpend(wallet, cardId, month), latestExtrasPass(cardId), claims, extrasCoupons())
}

internal fun Store.buyExtrasPass(cardId: Long, reviewedDay: LocalDate, intentId: String) {
    withExtras { today ->
        val operation = "extras.pass.$cardId.$intentId"
        val alreadyPaid = rawQuery("SELECT 1 FROM extras_pass WHERE operation=?", arrayOf(operation))
            .use { it.moveToFirst() }
        if (alreadyPaid) return@withExtras
        require(today == reviewedDay) { "The date changed. Review the new pass dates." }
        val latest = latestExtrasPass(cardId)
        require(latest == null || latest.endExclusive <= today) { "This card already has a current pass." }
        val entry = transact(cardId, "Payment", extrasPassPrice, "Demo monthly pass", "Transport", operation)
        execSQL("INSERT INTO extras_pass(operation, card_id, entry_id, start_day, end_day) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>(operation, cardId, entry, today.toEpochDay(), today.plusMonths(1).toEpochDay()))
    }
}

internal fun Store.claimExtrasSubsidy(cardId: Long, reviewedMonth: YearMonth) {
    withExtras { today ->
        val month = YearMonth.from(today)
        require(month == reviewedMonth) { "The month changed. Review the current subsidy." }
        val exists = rawQuery("SELECT 1 FROM extras_claim WHERE card_id=? AND month=?",
            arrayOf(cardId.toString(), month.toString())).use { it.moveToFirst() }
        if (exists) return@withExtras
        val wallet = load()
        require(wallet.cards.any { it.id == cardId && !it.frozen }) { "An active card is required to claim." }
        val spend = extrasTransportSpend(wallet, cardId, month)
        require(spend >= extrasClaimThreshold) { "This card has not reached the demo spending threshold." }
        val entry = transact(cardId, "Top-up", extrasClaimCredit, "Fictional transport subsidy", "Subsidy",
            "extras.claim.$cardId.$month")
        execSQL("INSERT INTO extras_claim(card_id, month, spend, credit, claimed_at, entry_id) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(cardId, month.toString(), spend, extrasClaimCredit, System.currentTimeMillis(), entry))
    }
}

internal fun Store.favoriteExtrasOffer(id: String, favorite: Boolean) {
    withExtras {
        val values = ContentValues().apply { put("favorite", if (favorite) 1 else 0) }
        check(update("extras_coupon", values, "offer_id=?", arrayOf(id)) == 1) { "Demo offer unavailable." }
    }
}

internal fun Store.redeemExtrasCoupon(id: String) {
    withExtras { today ->
        val values = ContentValues().apply { put("redeemed_at", System.currentTimeMillis()) }
        val count = update("extras_coupon", values, "offer_id=? AND redeemed_at IS NULL AND expires_day>=?",
            arrayOf(id, today.toEpochDay().toString()))
        require(count == 1) { "This demo coupon has expired or has already been redeemed." }
    }
}

// Entry point for the app's deferrable reminder worker. Store.remind deduplicates by key.
fun Store.remindExtrasExpiries() {
    withExtras { today ->
        val wallet = load()
        if (!wallet.prefs.reminders) return@withExtras
        val window = today..today.plusDays(3)
        for (card in wallet.cards) {
            val pass = latestExtrasPass(card.id) ?: continue
            val lastDay = pass.endExclusive.minusDays(1)
            if (pass.covers(today) && lastDay in window) {
                remind("extras.pass.expiry.${card.id}.$lastDay",
                    "The demo pass on ${card.name} is valid through $lastDay.")
            }
        }
        for (coupon in extrasCoupons()) {
            if (coupon.favorite && coupon.redeemedAt == null && coupon.expires in window) {
                remind("extras.coupon.expiry.${coupon.offer.id}.${coupon.expires}",
                    "Your saved demo offer “${coupon.offer.title}” expires on ${coupon.expires}.")
            }
        }
    }
}
