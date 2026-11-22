package hk.taplink.studio

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun WalletReceipt(entry: Entry, wallet: Wallet, onDismiss: () -> Unit, dismissLabel: String = "Done") {
    val context = LocalContext.current
    var revealed by remember(entry.id, wallet.prefs.hidden) { mutableStateOf(false) }
    var shareError by remember(entry.id) { mutableStateOf(false) }
    val receipt = remember(entry, wallet, revealed) { receiptDetails(entry, wallet, revealed) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    WalletDialog("Simulated receipt", onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = colors.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                    Text(receipt.kind, style = type.titleMedium)
                    Text(receipt.amount, style = type.headlineLarge)
                }
                ReceiptRow(receipt.party)
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReceiptRow(receipt.card)
                receipt.adjustments.forEach { ReceiptRow(it) }
                HorizontalDivider()
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(receipt.finalBalance.label, style = type.titleSmall)
                    Text(receipt.finalBalance.value, style = type.headlineSmall)
                }
                if (wallet.prefs.hidden) {
                    WalletSecondaryButton(if (revealed) "Hide receipt balances" else "Reveal receipt balances") {
                        revealed = !revealed
                    }
                }
            }
        }
        receipt.metadata.forEach { ReceiptRow(it) }
        if (shareError) {
            Text("No app is available to share this receipt.", Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.error, style = type.bodyMedium)
        }
        OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "TapLink · ${receipt.kind} receipt")
                .putExtra(Intent.EXTRA_TEXT, receipt.shareText())
            try {
                context.startActivity(Intent.createChooser(send, "Share simulated receipt"))
            } catch (_: ActivityNotFoundException) {
                shareError = true
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Share receipt") }
        WalletPrimaryButton(dismissLabel, onClick = onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptRow(field: ReceiptField) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(field.label, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelLarge)
        Text(field.value, style = MaterialTheme.typography.bodyLarge)
    }
}
