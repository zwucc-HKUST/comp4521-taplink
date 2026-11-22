package hk.taplink.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

// Each route retains its drafts in the same Activity state Bundle. Large pastes stay
// editable in memory, but must not exhaust the Binder budget when the Activity stops.
internal val walletDraftSaver = Saver<String, String>(
    save = { if (it.length <= 4096) it else "" },
    restore = { it }
)

@Composable
internal fun WalletDialog(
    title: String,
    onBack: () -> Unit,
    busy: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy)
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                if (busy) LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Updating wallet" }
                )
                content()
            }
        }
    }
}

@Composable
internal fun WalletPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
internal fun WalletSecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
internal fun WalletDetail(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun WalletMissingCard(onBack: () -> Unit) {
    WalletDialog("Card unavailable", onBack) {
        Text("This card is no longer in the wallet.")
        WalletPrimaryButton("Back", onClick = onBack)
    }
}

@Composable
internal fun WalletCardManager(
    wallet: Wallet, selected: Card?, controller: WalletController,
    onCreate: () -> Unit, onRename: (Card) -> Unit, onFreeze: (Card) -> Unit, onDismiss: () -> Unit
) {
    val enabled = !controller.busy
    WalletDialog("Manage cards", onDismiss, controller.busy) {
        if (selected == null) {
            Text("Create your first demo card.")
        } else {
            Text("Select the card to use in your wallet.", style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                wallet.cards.forEach { card ->
                    val active = card.id == selected.id
                    val colors = MaterialTheme.colorScheme
                    Surface(shape = RoundedCornerShape(12.dp), color = if (active) colors.secondaryContainer else colors.surface,
                        border = BorderStroke(1.dp, if (active) colors.primary else colors.outlineVariant)) {
                        Row(
                            Modifier.fillMaxWidth().selectable(active, enabled = enabled, role = Role.RadioButton,
                                onClick = {
                                    if (!active && !controller.busy) controller.run(action = { select(card.id) })
                                }).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = active, onClick = null, enabled = enabled)
                            Column {
                                Text(card.name, style = MaterialTheme.typography.titleMedium)
                                Text(walletCardNumber(card.id) + if (card.frozen) " · Frozen" else " · Active",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onRename(selected) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Rename")
                }
                OutlinedButton(onClick = { onFreeze(selected) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(if (selected.frozen) "Unfreeze" else "Freeze")
                }
            }
            HorizontalDivider()
        }
        WalletPrimaryButton("Create card", enabled, onCreate)
        WalletSecondaryButton("Done", enabled, onDismiss)
    }
}

@Composable
internal fun WalletNameDialog(card: Card?, wallet: Wallet, controller: WalletController, onBack: () -> Unit) {
    val creating = card == null
    var name by rememberSaveable(stateSaver = walletDraftSaver) { mutableStateOf(card?.name.orEmpty()) }
    var validation by rememberSaveable { mutableStateOf(false) }
    var submittedName by rememberSaveable(stateSaver = walletDraftSaver) { mutableStateOf("") }
    val originalIds = rememberSaveable { wallet.cards.map { it.id }.toLongArray() }
    val saved = submittedName.isNotEmpty() && if (creating) {
        wallet.cards.any { it.id !in originalIds && it.name == submittedName }
    } else {
        card?.name == submittedName
    }
    LaunchedEffect(saved) {
        if (saved) onBack()
    }
    WalletDialog(if (creating) "Create card" else "Rename card", onBack, controller.busy) {
        if (card == null) Text("Choose a name for your new demo card.", style = MaterialTheme.typography.bodyMedium)
        else Text(walletCardNumber(card.id), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = name, onValueChange = { name = it; validation = false },
            enabled = !controller.busy, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("Card name") }, isError = validation
        )
        if (validation) Text("Enter a card name.", color = MaterialTheme.colorScheme.error)
        WalletPrimaryButton(if (creating) "Create card" else "Save name", !controller.busy && !saved) {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) {
                validation = true
            } else if (!controller.busy) {
                if (card != null && card.name == cleanName) {
                    onBack()
                } else {
                    submittedName = cleanName
                    controller.run(action = { if (card == null) addCard(cleanName) else rename(card.id, cleanName) })
                }
            }
        }
        WalletSecondaryButton("Cancel", !controller.busy, onBack)
    }
}

@Composable
internal fun WalletFreezeDialog(card: Card, freezeCard: Boolean, controller: WalletController, onBack: () -> Unit) {
    val action = if (freezeCard) "Freeze" else "Unfreeze"
    val finished = card.frozen == freezeCard
    LaunchedEffect(finished) {
        if (finished) onBack()
    }
    WalletDialog("$action card?", onBack, controller.busy) {
        WalletDetail("Demo card", card.name + " · " + walletCardNumber(card.id))
        Text(if (freezeCard) "Payments and top-ups will be paused until you unfreeze this card."
            else "Payments and top-ups will be available again.")
        WalletPrimaryButton("$action card", !controller.busy && !finished) {
            if (!controller.busy) controller.run(action = { freeze(card.id, freezeCard) })
        }
        WalletSecondaryButton("Cancel", !controller.busy, onBack)
    }
}
