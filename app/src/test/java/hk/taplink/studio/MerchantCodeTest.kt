package hk.taplink.studio
import org.junit.Assert.*
import org.junit.Test

class MerchantCodeTest {
    @Test fun acceptsKnownMerchantAndExactCents() {
        val (merchant, amount) = parseMerchantCode("TL1|PIER|18.50")
        assertEquals("Star Pier Coffee", merchant.name)
        assertEquals(1850L, parseCents(amount))
    }

    @Test fun rejectsUnusableCodes() {
        listOf("https://example.com", "TL1|EVIL|10", "TL1|PIER|0", "TL1|BOOKS|-1",
            "TL1|FERRY|1.001", "TL1|PIER|10|extra", "TL1|PIER|1e6").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseMerchantCode(it) }
        }
    }

    @Test fun enforcesExactAmountBoundaries() {
        assertEquals(1L, parseCents("0.01"))
        assertEquals(100_000_000L, parseCents("1000000"))
        assertThrows(IllegalArgumentException::class.java) { parseCents("1000000.01") }
        assertEquals("HK$1,234.56", money(123456))
    }

    @Test fun onlyThresholdsAcceptZero() {
        listOf("0", "0.00", " 0.0 ").forEach {
            assertEquals(0L, parseCents(it, allowZero = true))
            assertThrows(IllegalArgumentException::class.java) { parseCents(it) }
        }
        listOf("-1", "0.001", "1000000.01").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseCents(it, allowZero = true) }
        }
    }
}
