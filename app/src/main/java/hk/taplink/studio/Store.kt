package hk.taplink.studio

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper

class Store internal constructor(context: Context, databaseName: String) :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 2) {

    constructor(context: Context) : this(context, "taplink_astra.db")

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE cards (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL CHECK (length(trim(name)) > 0),
                cents INTEGER NOT NULL DEFAULT 0 CHECK (cents >= 0),
                frozen INTEGER NOT NULL DEFAULT 0 CHECK (frozen IN (0, 1))
            )"""
        )
        db.execSQL(
            """CREATE TABLE entries (
                id INTEGER PRIMARY KEY,
                card_id INTEGER NOT NULL REFERENCES cards(id),
                label TEXT NOT NULL,
                category TEXT NOT NULL,
                kind TEXT NOT NULL CHECK (kind IN ('Payment', 'Top-up', 'Reload')),
                delta INTEGER NOT NULL CHECK (
                    (kind = 'Payment' AND delta < 0) OR (kind != 'Payment' AND delta > 0)
                ),
                after_cents INTEGER NOT NULL CHECK (after_cents >= 0),
                at INTEGER NOT NULL,
                operation TEXT NOT NULL,
                leg INTEGER NOT NULL CHECK (leg = 0 OR (leg = 1 AND kind = 'Reload')),
                UNIQUE (operation, leg)
            )"""
        )
        db.execSQL("CREATE INDEX entries_by_time ON entries(at DESC, id DESC)")
        db.execSQL(
            """CREATE TABLE inbox (
                id INTEGER PRIMARY KEY,
                event_key TEXT NOT NULL UNIQUE,
                text TEXT NOT NULL,
                at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE prefs (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                selected INTEGER NOT NULL REFERENCES cards(id),
                threshold INTEGER NOT NULL CHECK (threshold >= 0),
                reload INTEGER NOT NULL CHECK (reload > 0),
                auto INTEGER NOT NULL CHECK (auto IN (0, 1)),
                alerts INTEGER NOT NULL CHECK (alerts IN (0, 1)),
                hidden INTEGER NOT NULL CHECK (hidden IN (0, 1)),
                theme TEXT NOT NULL,
                reminders INTEGER NOT NULL CHECK (reminders IN (0, 1)),
                shake INTEGER NOT NULL CHECK (shake IN (0, 1)),
                $WIDGET_BALANCE_COLUMN
            )"""
        )

        val campus = db.insertCard("Campus")
        val commute = db.insertCard("Commute")
        val weekend = db.insertCard("Weekend")
        val defaults = Prefs(selected = campus).values().apply { put("id", 1L) }
        db.insertOrThrow("prefs", null, defaults)

        // SQLiteOpenHelper wraps creation and the entire opening ledger in one transaction.
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        db.post(campus, "Top-up", 20_000, "Demo opening credit", "Funding", "seed-campus-open", now - 15 * day)
        db.post(commute, "Top-up", 16_000, "Demo opening credit", "Funding", "seed-commute-open", now - 15 * day)
        db.post(weekend, "Top-up", 12_000, "Demo opening credit", "Funding", "seed-weekend-open", now - 15 * day)
        db.post(weekend, "Payment", 2_700, "Pier Bookshop", "Shopping", "seed-books", now - 6 * day)
        db.post(campus, "Payment", 3_200, "Courtyard Cafe", "Dining", "seed-lunch", now - 2 * day)
        db.post(commute, "Payment", 1_180, "Harbour Tram", "Transport", "seed-tram", now - day)
        db.post(campus, "Payment", 850, "Courtyard Cafe", "Dining", "seed-tea", now)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != 1 || newVersion != 2) {
            throw SQLiteException("Unsupported wallet database upgrade from $oldVersion to $newVersion.")
        }
        db.execSQL("ALTER TABLE prefs ADD COLUMN $WIDGET_BALANCE_COLUMN")
    }

    internal fun <T> applyAndLoad(action: Store.() -> T): Pair<Result<T>, Wallet> =
        try {
            writableDatabase.atomic {
                val value = action(this@Store)
                Result.success(value) to load()
            }
        } catch (rejected: IllegalArgumentException) {
            // The failed transaction has ended. Show the balance or card status that
            // caused the rejection, including changes from another writer.
            Result.failure<T>(rejected) to load()
        }

    fun load(): Wallet = writableDatabase.atomic {
        val prefs = preferences()
        val cards = rows("SELECT $CARD_COLUMNS FROM cards ORDER BY id") { it.card() }
        val entries = rows("SELECT $ENTRY_COLUMNS FROM entries ORDER BY at DESC, id DESC") { it.entry() }
        val notes = rows("SELECT id, text, at, event_key FROM inbox ORDER BY at DESC, id DESC") {
            val message = if (prefs.hidden && it.getString(3).startsWith("low/")) {
                "A demo payment left your card below its low-balance threshold."
            } else it.getString(1)
            Note(it.getLong(0), message, it.getLong(2))
        }
        Wallet(cards, entries, notes, prefs)
    }

    fun select(id: Long) = changePrefs { it.copy(selected = id) }

    fun addCard(name: String) {
        writableDatabase.insertCard(name)
    }

    fun rename(id: Long, name: String) {
        writableDatabase.updateCard(id, ContentValues().apply { put("name", cardName(name)) })
    }

    fun freeze(id: Long, frozen: Boolean) {
        writableDatabase.updateCard(id, ContentValues().apply { put("frozen", frozen) })
    }

    fun changePrefs(update: (Prefs) -> Prefs) {
        writableDatabase.atomic {
            val next = update(preferences())
            require(next.threshold >= 0) { "The low-balance threshold must be zero or higher." }
            require(next.reload > 0) { "The reload amount must be positive." }
            require(next.theme.isNotBlank()) { "Choose a theme." }
            requireCard(next.selected)
            check(this.update("prefs", next.values(), "id = 1", null) == 1)
        }
    }

    fun transact(cardId: Long, kind: String, cents: Long, label: String, category: String, operation: String): Long =
        writableDatabase.atomic {
            post(cardId, kind, cents, label, category, operation, System.currentTimeMillis())
        }

    fun remind(key: String, text: String) {
        require(key.isNotBlank()) { "A reminder key is required." }
        require(text.isNotBlank()) { "A reminder message is required." }
        writableDatabase.atomic {
            if (preferences().reminders) {
                addNote("reminder/$key", text, System.currentTimeMillis())
            }
        }
    }

    // The caller holds a database transaction for the replay check and every derived write.
    private fun SQLiteDatabase.post(
        cardId: Long, kind: String, cents: Long, label: String, category: String, operation: String, at: Long
    ): Long {
        require(kind == "Payment" || kind == "Top-up" || kind == "Reload") { "Unknown transaction type." }
        require(cents > 0) { "Enter an amount greater than zero." }
        require(operation.isNotBlank()) { "An operation key is required." }
        val title = label.trim()
        val group = category.trim()
        require(title.isNotEmpty() && group.isNotEmpty()) { "A transaction label and category are required." }

        val payment = kind == "Payment"
        val delta = if (payment) -cents else cents
        val original = rawQuery(
            "SELECT $ENTRY_COLUMNS FROM entries WHERE operation = ? AND leg = 0", arrayOf(operation)
        ).use { if (it.moveToFirst()) it.entry() else null }
        if (original != null) {
            require(original.cardId == cardId && original.kind == kind && original.delta == delta &&
                original.label == title && original.category == group) {
                "This operation key already belongs to a different transaction."
            }
            return original.id
        }

        val card = requireCard(cardId)
        require(!card.frozen) { "Unfreeze the card before making a transaction." }
        val after = if (payment) {
            require(card.cents >= cents) { "The card has insufficient balance." }
            card.cents - cents
        } else {
            credit(card.cents, cents)
        }
        val primary = Entry(0, cardId, title, group, kind, delta, after, at, operation)
        val receiptId = insertEntry(primary, leg = 0)
        var finalBalance = after
        if (payment) {
            val prefs = preferences()
            if (prefs.auto && after < prefs.threshold) {
                finalBalance = credit(after, prefs.reload)
                val reload = primary.copy(label = "Automatic reload", category = "Funding", kind = "Reload",
                    delta = prefs.reload, after = finalBalance)
                insertEntry(reload, leg = 1)
            }
            if (prefs.alerts && finalBalance < prefs.threshold) {
                addNote("low/$operation", "Low balance on ${card.name}: ${money(finalBalance)}.", at)
            }
        }
        updateCard(cardId, ContentValues().apply { put("cents", finalBalance) })
        return receiptId
    }

    private fun SQLiteDatabase.addNote(key: String, text: String, at: Long) {
        // Repeated delivery keeps the first message and its timestamp.
        execSQL(
            """INSERT INTO inbox (event_key, text, at)
               SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM inbox WHERE event_key = ?)""",
            arrayOf(key, text, at, key)
        )
    }

    private fun SQLiteDatabase.insertEntry(entry: Entry, leg: Int): Long {
        val values = ContentValues().apply {
            put("card_id", entry.cardId)
            put("label", entry.label)
            put("category", entry.category)
            put("kind", entry.kind)
            put("delta", entry.delta)
            put("after_cents", entry.after)
            put("at", entry.at)
            put("operation", entry.operation)
            put("leg", leg)
        }
        return insertOrThrow("entries", null, values)
    }

    private fun SQLiteDatabase.insertCard(name: String): Long =
        insertOrThrow("cards", null, ContentValues().apply { put("name", cardName(name)) })

    private fun SQLiteDatabase.updateCard(id: Long, values: ContentValues) {
        require(update("cards", values, "id = ?", arrayOf(id.toString())) == 1) { "Card does not exist." }
    }

    private fun SQLiteDatabase.requireCard(id: Long): Card =
        rawQuery("SELECT $CARD_COLUMNS FROM cards WHERE id = ?", arrayOf(id.toString())).use {
            require(it.moveToFirst()) { "Card does not exist." }
            it.card()
        }

    private fun SQLiteDatabase.preferences(): Prefs = rawQuery(
        """SELECT selected, threshold, reload, auto, alerts, hidden, theme, reminders, shake, widget_balance_visible
           FROM prefs WHERE id = 1""",
        null
    ).use {
        check(it.moveToFirst()) { "Wallet preferences are missing." }
        Prefs(selected = it.getLong(0), threshold = it.getLong(1), reload = it.getLong(2),
            auto = it.flag(3), alerts = it.flag(4), hidden = it.flag(5), theme = it.getString(6),
            reminders = it.flag(7), shake = it.flag(8), widgetBalanceVisible = it.flag(9))
    }

    private fun Prefs.values() = ContentValues().also { values ->
        values.put("selected", selected)
        values.put("threshold", threshold)
        values.put("reload", reload)
        values.put("auto", auto)
        values.put("alerts", alerts)
        values.put("hidden", hidden)
        values.put("theme", theme)
        values.put("reminders", reminders)
        values.put("shake", shake)
        values.put("widget_balance_visible", widgetBalanceVisible)
    }

    private fun cardName(name: String) = name.trim().also {
        require(it.isNotEmpty()) { "Enter a card name." }
    }

    private fun credit(balance: Long, amount: Long): Long {
        require(amount <= Long.MAX_VALUE - balance) { "This credit would exceed the maximum balance." }
        return balance + amount
    }

    private companion object {
        const val WIDGET_BALANCE_COLUMN =
            "widget_balance_visible INTEGER NOT NULL DEFAULT 0 CHECK (widget_balance_visible IN (0, 1))"
        const val CARD_COLUMNS = "id, name, cents, frozen"
        const val ENTRY_COLUMNS = "id, card_id, label, category, kind, delta, after_cents, at, operation"
    }
}

private fun Cursor.flag(index: Int) = getInt(index) != 0
private fun Cursor.card() = Card(getLong(0), getString(1), getLong(2), flag(3))
private fun Cursor.entry() = Entry(
    getLong(0), getLong(1), getString(2), getString(3), getString(4),
    getLong(5), getLong(6), getLong(7), getString(8)
)

private inline fun <T> SQLiteDatabase.rows(sql: String, read: (Cursor) -> T): List<T> =
    rawQuery(sql, null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(read(cursor)) }
    }

private inline fun <T> SQLiteDatabase.atomic(block: SQLiteDatabase.() -> T): T {
    beginTransactionNonExclusive()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}
