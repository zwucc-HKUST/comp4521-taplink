package hk.taplink.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryKindTest {
    private val pass = Entry(1, 4, "Demo monthly pass", "Transport", "Payment",
        -6_000, 0, 0, "extras.pass.4.buy")
    private val claim = Entry(2, 4, "Fictional transport subsidy", "Subsidy", "Top-up",
        1_200, 1_200, 0, "extras.claim.4.2026-11")

    @Test fun extrasKeepTheirTypeWhenTheDescriptionChanges() {
        assertEquals("Pass purchase", pass.copy(label = "Monthly fare", category = "Transit").displayKind)
        assertEquals("Subsidy credit", claim.copy(label = "Demo credit", category = "Transport").displayKind)
    }

    @Test fun ordinaryTransactionsCanUseTheSameLabelsAndCategoriesAsExtras() {
        assertEquals("Payment", pass.copy(operation = "seed-tram").displayKind)
        assertEquals("Top-up", claim.copy(operation = "seed-campus-open").displayKind)
    }

    @Test fun reloadSharingThePassOperationKeepsItsOwnType() {
        val reload = pass.copy(id = 3, label = "Automatic reload", category = "Funding",
            kind = "Reload", delta = 10_000, after = 10_000)
        assertEquals("Pass purchase", pass.displayKind)
        assertEquals("Reload", reload.displayKind)
    }
}
