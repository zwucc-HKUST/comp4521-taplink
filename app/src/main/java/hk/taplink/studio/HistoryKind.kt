package hk.taplink.studio

private const val PASS_PURCHASE = "Pass purchase"
private const val SUBSIDY_CREDIT = "Subsidy credit"

internal val historyKinds = listOf("Payment", "Top-up", "Reload", PASS_PURCHASE, SUBSIDY_CREDIT)

// A pass and its automatic reload share an operation key. Only the debit is a purchase.
internal val Entry.displayKind: String
    get() = when {
        kind == "Payment" && operation.startsWith("extras.pass.$cardId.") -> PASS_PURCHASE
        kind == "Top-up" && operation.startsWith("extras.claim.$cardId.") -> SUBSIDY_CREDIT
        else -> kind
    }
