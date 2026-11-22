package hk.taplink.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptDetailsTest {
    private val payment = Entry(11, 3, "Star Pier Coffee", "Food", "Payment", -1_850, 7_325, 0, "checkout")
    private val reload = Entry(12, 3, "Automatic reload", "Funding", "Reload", 2_500, 9_825, 0, "checkout")
    private val wallet = Wallet(listOf(Card(3, "Campus", 300, false)), listOf(reload, payment), emptyList(), Prefs())

    @Test fun paymentAndReloadOpenTheSameHistoricalReceipt() {
        val receipt = receiptDetails(payment, wallet, false)
        val reopened = receiptDetails(reload, wallet, false)
        assertEquals(receipt, reopened)
        assertEquals(receipt.shareText(), reopened.shareText())
        assertEquals("−HK$18.50", receipt.amount)
        assertEquals("Campus · •••• 0003", receipt.card.value)
        assertEquals(listOf(ReceiptField("Balance after payment", "HK$73.25"),
            ReceiptField("Automatic reload · #12", "+HK$25.00")), receipt.adjustments)
        assertEquals(ReceiptField("Final card balance", "HK$98.25"), receipt.finalBalance)
        assertTrue(receipt.shareText().contains("Receipt ID: #11"))
    }

    @Test fun hiddenBalancesRequireAnExplicitRevealForBothDisplayAndShare() {
        val hidden = wallet.copy(prefs = wallet.prefs.copy(hidden = true))
        val masked = receiptDetails(reload, hidden, false)
        assertEquals("Hidden", masked.adjustments.first().value)
        assertEquals("Hidden", masked.finalBalance.value)
        val text = masked.shareText()
        assertFalse(text.contains("73.25"))
        assertFalse(text.contains("98.25"))
        assertTrue(text.contains("Amount: −HK$18.50"))
        assertTrue(text.contains("Automatic reload · #12: +HK$25.00"))
        val revealed = receiptDetails(payment, hidden, true)
        assertTrue(revealed.shareText().contains("Balance after payment: HK$73.25"))
        assertTrue(revealed.shareText().contains("Final card balance: HK$98.25"))
    }

    @Test fun onlyTheSameCardAndNonblankOperationCanSupplyALinkedLeg() {
        val others = wallet.copy(entries = listOf(reload.copy(cardId = 4), reload.copy(operation = "another")))
        val receipt = receiptDetails(payment, others, false)
        assertTrue(receipt.adjustments.isEmpty())
        assertEquals("HK$73.25", receipt.finalBalance.value)
        val blankDebit = payment.copy(operation = "")
        val blankCredit = reload.copy(operation = "")
        val legacy = wallet.copy(entries = listOf(blankDebit, blankCredit))
        assertTrue(receiptDetails(blankDebit, legacy, false).adjustments.isEmpty())
        val standalone = receiptDetails(blankCredit, legacy, false)
        assertEquals("Automatic reload", standalone.kind)
        assertEquals("+HK$25.00", standalone.amount)
        assertEquals("HK$98.25", standalone.finalBalance.value)
    }

    @Test fun passAndSubsidyUseSemanticKindsAndTheirOwnLedgerBalances() {
        val pass = payment.copy(label = "Demo monthly pass", operation = "extras.pass.3.review")
        val passReload = reload.copy(operation = pass.operation)
        val passWallet = wallet.copy(entries = listOf(passReload, pass))
        val linked = receiptDetails(passReload, passWallet, false)
        assertEquals(receiptDetails(pass, passWallet, false), linked)
        assertEquals("Pass purchase", linked.kind)
        assertEquals(ReceiptField("Pass", pass.label), linked.party)
        assertEquals("Balance after pass purchase", linked.adjustments.first().label)
        assertEquals("HK$98.25", linked.finalBalance.value)

        val topUp = payment.copy(kind = "Top-up", label = "Demo wallet funds", delta = 1_200, after = 1_250)
        val subsidy = topUp.copy(label = "Fictional transport subsidy", operation = "extras.claim.3.2026-11")
        for ((entry, kind) in listOf(topUp to "Top-up", subsidy to "Subsidy credit")) {
            val credit = receiptDetails(entry, wallet, false)
            assertEquals(kind, credit.kind)
            assertEquals(ReceiptField("Funding source", entry.label), credit.party)
            assertTrue(credit.adjustments.isEmpty())
            assertEquals("+HK$12.00", credit.amount)
            assertEquals("HK$12.50", credit.finalBalance.value)
        }
    }
}
