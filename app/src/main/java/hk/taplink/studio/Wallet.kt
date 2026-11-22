package hk.taplink.studio

import java.math.BigDecimal
import java.util.Locale

data class Card(val id: Long, val name: String, val cents: Long, val frozen: Boolean)
data class Entry(val id: Long, val cardId: Long, val label: String, val category: String,
                 val kind: String, val delta: Long, val after: Long, val at: Long, val operation: String)
data class Note(val id: Long, val text: String, val at: Long)
data class Prefs(val selected: Long = 1, val threshold: Long = 5000, val reload: Long = 10000,
                 val auto: Boolean = false, val alerts: Boolean = true, val hidden: Boolean = false,
                 val theme: String = "System", val reminders: Boolean = true, val shake: Boolean = false)
data class Wallet(val cards: List<Card>, val entries: List<Entry>, val notes: List<Note>, val prefs: Prefs)

fun money(cents: Long): String = String.format(Locale.US, "HK$%,.2f", BigDecimal.valueOf(cents, 2))

fun parseCents(input: String, allowZero: Boolean = false): Long {
    val raw = input.trim()
    require(raw.length in 1..14 && raw.matches(Regex("""\d+(\.\d{1,2})?"""))) {
        "Enter an amount with up to two decimal places."
    }
    val cents = raw.toBigDecimal().movePointRight(2).longValueExact()
    val minimum = if (allowZero) 0L else 1L
    require(cents in minimum..100_000_000L) { "Enter an amount between ${money(minimum)} and HK$1,000,000." }
    return cents
}
