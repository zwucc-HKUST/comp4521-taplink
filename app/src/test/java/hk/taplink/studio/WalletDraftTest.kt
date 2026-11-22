package hk.taplink.studio

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletDraftTest {
    @Test
    fun recreateRetainsOrdinaryTextAndClearsAnOversizedPasteWithoutChangingItsAmount() {
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        fun roundTrip(text: String) = with(walletDraftSaver) {
            restore(checkNotNull(scope.save(text)))
        }
        assertEquals(" 50.25 ", roundTrip(" 50.25 "))
        assertEquals("Harbour", roundTrip("Harbour"))
        assertEquals("", roundTrip("1".repeat(600_000)))
    }
}
