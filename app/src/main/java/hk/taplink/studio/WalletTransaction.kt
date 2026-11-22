package hk.taplink.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.UUID

private const val demoFundingSource = "Demo wallet funds"

private fun walletAmountIssue(card: Card, payment: Boolean, cents: Long): String? = when {
    card.frozen -> "This card is frozen. Unfreeze it in Cards to continue."
    cents <= 0L -> "Enter an amount greater than zero."
    payment && cents > card.cents -> "This card has insufficient funds for that payment."
    !payment && cents > Long.MAX_VALUE - card.cents -> "This amount would exceed the balance limit."
    else -> null
}

@Composable
private fun WalletFormError(message: String) {
    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun WalletTransactionDialog(
    card: Card, payment: Boolean, wallet: Wallet, controller: WalletController, onDismiss: () -> Unit
) {
    val kind = if (payment) "Payment" else "Top-up"
    var merchantName by rememberSaveable { mutableStateOf(merchants.firstOrNull()?.name.orEmpty()) }
    var amount by rememberSaveable(stateSaver = walletDraftSaver) { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf("") }

    // These values describe exactly one reviewed intent, including across Activity recreation.
    var operation by rememberSaveable { mutableStateOf("") }
    var reviewedCents by rememberSaveable { mutableLongStateOf(0L) }
    var reviewedLabel by rememberSaveable { mutableStateOf("") }
    var reviewedCategory by rememberSaveable { mutableStateOf("") }
    if (operation.isNotEmpty()) {
        val committed = wallet.entries.firstOrNull {
            it.operation == operation && it.kind == kind && it.cardId == card.id
        }
        if (committed != null) {
            WalletReceipt(committed, wallet, onDismiss)
            return
        }
        val edit: () -> Unit = { if (!controller.busy) operation = "" }
        val issue = walletAmountIssue(card, payment, reviewedCents)
        WalletDialog(if (payment) "Review payment" else "Review top-up", edit, controller.busy) {
            Text(money(reviewedCents), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary)
            WalletDetail("Demo card", card.name + " · " + walletCardNumber(card.id))
            WalletDetail(if (payment) "Merchant" else "Funding source", reviewedLabel)
            if (!wallet.prefs.hidden) WalletDetail("Current balance", money(card.cents))
            if (issue != null) WalletFormError(issue)
            WalletPrimaryButton(if (payment) "Confirm simulated payment" else "Confirm simulated top-up",
                !controller.busy && issue == null) {
                if (!controller.busy) {
                    val nonce = operation
                    val label = reviewedLabel
                    val category = reviewedCategory
                    val cents = reviewedCents
                    controller.run(action = { transact(card.id, kind, cents, label, category, nonce) })
                }
            }
            WalletSecondaryButton("Back to edit", !controller.busy, edit)
        }
        return
    }

    val selectedMerchant = if (payment) merchants.firstOrNull { it.name == merchantName } else null
    WalletDialog(if (payment) "Pay a merchant" else "Top up", onDismiss, controller.busy) {
        WalletDetail("Demo card", card.name + " · " + walletCardNumber(card.id))
        if (payment) {
            Text("Merchant", style = MaterialTheme.typography.titleSmall)
            MerchantScan(onFound = { merchant, decodedAmount ->
                merchantName = merchant.name
                amount = decodedAmount
                formError = ""
            })
            Column(Modifier.selectableGroup()) {
                merchants.forEach { merchant ->
                    val chosen = merchant.name == merchantName
                    Row(Modifier.fillMaxWidth().selectable(chosen, role = Role.RadioButton, onClick = {
                        merchantName = merchant.name
                        formError = ""
                    }).heightIn(min = 48.dp).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadioButton(selected = chosen, onClick = null)
                        Column {
                            Text(merchant.name, style = MaterialTheme.typography.bodyLarge)
                            Text(merchant.category, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            WalletDetail("Funding source", demoFundingSource)
            Text("Choose an amount or enter your own.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("50", "100", "200").forEach { fixed ->
                    FilterChip(selected = amount == fixed, onClick = { amount = fixed; formError = "" },
                        label = { Text("HK$" + fixed) })
                }
            }
        }
        OutlinedTextField(value = amount, onValueChange = { amount = it; formError = "" },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Amount (HKD)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            isError = formError.isNotEmpty())
        if (formError.isNotEmpty()) WalletFormError(formError)
        WalletPrimaryButton(if (payment) "Review payment" else "Review top-up", !controller.busy) {
            val cents = try {
                parseCents(amount.trim())
            } catch (_: NumberFormatException) {
                formError = "Enter an HKD amount, for example 25.50."
                return@WalletPrimaryButton
            } catch (badAmount: IllegalArgumentException) {
                formError = badAmount.message?.takeIf { it.isNotBlank() } ?: "Enter a valid HKD amount."
                return@WalletPrimaryButton
            }
            val issue = walletAmountIssue(card, payment, cents)
            if (issue != null) {
                formError = issue
            } else if (payment && selectedMerchant == null) {
                formError = "Choose a demo merchant."
            } else {
                reviewedCents = cents
                reviewedLabel = selectedMerchant?.name ?: demoFundingSource
                reviewedCategory = selectedMerchant?.category ?: "Top-up"
                operation = UUID.randomUUID().toString()
            }
        }
        WalletSecondaryButton("Cancel", !controller.busy, onDismiss)
    }
}
