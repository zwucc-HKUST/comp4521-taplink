package hk.taplink.studio

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val receiptTime = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm:ss z", Locale.ENGLISH)
    .withZone(ZoneId.of("Asia/Hong_Kong"))

internal data class ReceiptField(val label: String, val value: String)

internal data class ReceiptDetails(
    val kind: String,
    val amount: String,
    val party: ReceiptField,
    val card: ReceiptField,
    val adjustments: List<ReceiptField>,
    val finalBalance: ReceiptField,
    val metadata: List<ReceiptField>
) {
    fun shareText(): String = buildString {
        appendLine("TapLink · Simulated receipt")
        appendLine(kind)
        appendLine("Amount: $amount")
        fun field(item: ReceiptField) { appendLine("${item.label}: ${item.value}") }
        field(party)
        field(card)
        adjustments.forEach(::field)
        field(finalBalance)
        metadata.forEach(::field)
    }.trimEnd()
}

internal fun receiptDetails(selected: Entry, wallet: Wallet, revealBalances: Boolean): ReceiptDetails {
    // Astra stores a triggered reload as a second row of the original debit operation.
    fun linkedEntry(kind: String) = if (selected.operation.isBlank()) null else wallet.entries.firstOrNull {
        it.operation == selected.operation && it.cardId == selected.cardId && it.kind == kind
    }
    val primary = if (selected.kind == "Reload") linkedEntry("Payment") ?: selected else selected
    val reload = when {
        primary.kind != "Payment" -> null
        selected.kind == "Reload" -> selected
        else -> linkedEntry("Reload")
    }
    val kind = when (val name = primary.displayKind) {
        "Reload" -> "Automatic reload"
        else -> name
    }
    val partyLabel = when (kind) {
        "Payment" -> "Merchant"
        "Pass purchase" -> "Pass"
        "Top-up", "Subsidy credit" -> "Funding source"
        else -> "Description"
    }
    fun balance(cents: Long) = if (!wallet.prefs.hidden || revealBalances) money(cents) else "Hidden"
    val number = walletCardNumber(primary.cardId)
    val name = wallet.cards.firstOrNull { it.id == primary.cardId }?.name
    return ReceiptDetails(
        kind = kind,
        amount = walletDelta(primary.delta),
        party = ReceiptField(partyLabel, primary.label),
        card = ReceiptField("Demo card", if (name == null) number else "$name · $number"),
        adjustments = if (reload == null) emptyList() else listOf(
            ReceiptField("Balance after ${kind.lowercase(Locale.ENGLISH)}", balance(primary.after)),
            ReceiptField("Automatic reload · #${reload.id}", walletDelta(reload.delta))
        ),
        finalBalance = ReceiptField("Final card balance", balance(reload?.after ?: primary.after)),
        metadata = listOf(
            ReceiptField("Receipt ID", "#${primary.id}"),
            ReceiptField("Date and time", receiptTime.format(Instant.ofEpochMilli(primary.at))),
            ReceiptField("Category", primary.category)
        )
    )
}
