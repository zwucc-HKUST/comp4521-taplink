package hk.taplink.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class WalletPanel { Dashboard, Manage, CreateFirst, Create, Rename, Freeze, Thaw, Pay, TopUp, Receipt }

internal fun walletCardNumber(id: Long) = "•••• " + id.toString().takeLast(4).padStart(4, '0')

private val walletDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

internal fun walletTimestamp(at: Long): String =
    walletDateFormat.format(Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Hong_Kong")))

internal fun walletDelta(cents: Long): String =
    if (cents < 0L) "−" + money(-cents) else "+" + money(cents)

@Composable
fun WalletScreen(controller: WalletController, go: (String) -> Unit) {
    val wallet = controller.wallet ?: return
    val selected = wallet.cards.firstOrNull { it.id == wallet.prefs.selected } ?: wallet.cards.firstOrNull()
    var panel by rememberSaveable { mutableStateOf(WalletPanel.Dashboard) }
    var targetId by rememberSaveable { mutableLongStateOf(0L) }
    var entryId by rememberSaveable { mutableLongStateOf(0L) }
    val recent = remember(wallet.entries, selected?.id) {
        wallet.entries.filter { it.cardId == selected?.id }
            .sortedWith(compareByDescending<Entry> { it.at }.thenByDescending { it.id }).take(3)
    }

    val toggleBalance: () -> Unit = {
        if (!controller.busy) controller.run(action = { changePrefs { it.copy(hidden = !it.hidden) } })
    }
    val sensorAvailable = rememberWalletShakeListener(
        enabled = wallet.prefs.shake && panel == WalletPanel.Dashboard && selected != null,
        onShake = toggleBalance
    )
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Wallet", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            OutlinedButton(onClick = { panel = WalletPanel.Manage }) {
                WalletIcon(WalletGlyph.Cards, MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
                Text("Cards", Modifier.padding(start = 8.dp))
            }
        }
        if (selected == null) {
            Text("Add a demo card to start using your wallet.")
            Button(onClick = { panel = WalletPanel.CreateFirst }) { Text("Create card") }
        } else {
            WalletHero(selected, wallet.prefs, !controller.busy, toggleBalance)
            if (selected.frozen) {
                Text("This card is frozen. Open Cards to unfreeze it.", color = MaterialTheme.colorScheme.error)
            } else if (!wallet.prefs.hidden && selected.cents < wallet.prefs.threshold) {
                Text("Low balance · Below " + money(wallet.prefs.threshold),
                    color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletAction("Pay", WalletGlyph.Pay, primary = true, enabled = !selected.frozen && !controller.busy,
                    modifier = Modifier.weight(1f)) {
                    targetId = selected.id
                    panel = WalletPanel.Pay
                }
                WalletAction("Top up", WalletGlyph.TopUp, primary = false, enabled = !selected.frozen && !controller.busy,
                    modifier = Modifier.weight(1f)) {
                    targetId = selected.id
                    panel = WalletPanel.TopUp
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent activity", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { go("Activity") }) { Text("View all") }
        }
        if (recent.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text("Activity for this card will appear here after a payment or top-up.",
                    Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column {
                recent.forEach { entry ->
                    WalletRecentEntry(entry) {
                        entryId = entry.id
                        panel = WalletPanel.Receipt
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Surface(
            onClick = { go("Passes") }, shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletIcon(WalletGlyph.Ticket, MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Passes & offers", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                WalletIcon(WalletGlyph.Forward, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.size(20.dp))
            }
        }
        if (sensorAvailable && selected != null) {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = wallet.prefs.shake, role = Role.Switch, enabled = !controller.busy,
                    onValueChange = { enabled ->
                        controller.run(action = { changePrefs { it.copy(shake = enabled) } })
                    }
                ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Shake to hide or show balance", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = wallet.prefs.shake, onCheckedChange = null)
            }
        }
        Text("Educational prototype · Simulated balances and payments only.",
            Modifier.padding(bottom = 20.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val target = wallet.cards.firstOrNull { it.id == targetId }
    val dashboard = { panel = WalletPanel.Dashboard }
    val manage = { panel = WalletPanel.Manage }
    when (panel) {
        WalletPanel.Dashboard -> Unit
        WalletPanel.Manage -> WalletCardManager(wallet, selected, controller,
            onCreate = { panel = WalletPanel.Create },
            onRename = { targetId = it.id; panel = WalletPanel.Rename },
            onFreeze = { targetId = it.id; panel = if (it.frozen) WalletPanel.Thaw else WalletPanel.Freeze },
            onDismiss = dashboard)
        WalletPanel.CreateFirst -> WalletNameDialog(null, wallet, controller, dashboard)
        WalletPanel.Create -> WalletNameDialog(null, wallet, controller, manage)
        WalletPanel.Rename -> if (target != null) WalletNameDialog(target, wallet, controller, manage)
            else WalletMissingCard(manage)
        WalletPanel.Freeze, WalletPanel.Thaw -> if (target != null)
            WalletFreezeDialog(target, panel == WalletPanel.Freeze, controller, manage)
            else WalletMissingCard(manage)
        WalletPanel.Pay, WalletPanel.TopUp -> if (target != null)
            WalletTransactionDialog(target, panel == WalletPanel.Pay, wallet, controller, dashboard)
            else WalletMissingCard(dashboard)
        WalletPanel.Receipt -> {
            val entry = wallet.entries.firstOrNull { it.id == entryId }
            if (entry != null) WalletReceipt(entry, wallet, dashboard)
            else WalletDialog("Receipt unavailable", dashboard) {
                Text("This entry is no longer in the wallet.")
                WalletPrimaryButton("Close", onClick = dashboard)
            }
        }
    }
}

@Composable
private fun WalletHero(card: Card, prefs: Prefs, enabled: Boolean, onToggle: () -> Unit) {
    val ivory = Color(0xFFFFF3DB)
    val mint = Color(0xFFC4EADB)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF103C39), Color(0xFF16544D))))) {
        WalletHarbourArt(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.name, Modifier.weight(1f), color = ivory, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge)
                Surface(shape = CircleShape,
                    color = if (card.frozen) Color(0xFFFFD9C0) else Color(0xFFD3EBDD)) {
                    Text(if (card.frozen) "Frozen" else "Active", Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        color = Color(0xFF193A31), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Demo balance", color = mint, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val balanceSemantics = if (prefs.hidden) Modifier.clearAndSetSemantics {
                    contentDescription = "Balance hidden"
                } else Modifier
                Text(if (prefs.hidden) "••••" else money(card.cents),
                    Modifier.weight(1f, fill = false).then(balanceSemantics), color = ivory, style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold)
                val action = if (prefs.hidden) "Show balance" else "Hide balance"
                IconButton(onClick = onToggle, enabled = enabled, modifier = Modifier.semantics { contentDescription = action }) {
                    WalletIcon(if (prefs.hidden) WalletGlyph.Reveal else WalletGlyph.Conceal, ivory,
                        Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            val number = walletCardNumber(card.id)
            Text(number, Modifier.clearAndSetSemantics { contentDescription = "Card ending in " + number.takeLast(4) },
                color = ivory, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WalletAction(
    label: String, glyph: WalletGlyph, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val foreground = when {
        !enabled -> colors.onSurfaceVariant
        primary -> colors.onPrimary
        else -> colors.onTertiaryContainer
    }
    val background = when {
        !enabled -> colors.surfaceContainerHigh
        primary -> colors.primary
        else -> colors.tertiaryContainer
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = background) {
        Column(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WalletIcon(glyph, foreground)
            Text(label, color = foreground, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WalletRecentEntry(entry: Entry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open receipt", onClick = onClick)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val payment = entry.kind == "Payment"
        Box(Modifier.size(38.dp).background(
            if (payment) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            CircleShape), contentAlignment = Alignment.Center) {
            WalletIcon(if (payment) WalletGlyph.Pay else WalletGlyph.TopUp,
                if (payment) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.titleSmall)
            Text(entry.displayKind + " · " + walletTimestamp(entry.at), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(walletDelta(entry.delta), style = MaterialTheme.typography.labelLarge,
            color = if (entry.delta < 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
    }
}
