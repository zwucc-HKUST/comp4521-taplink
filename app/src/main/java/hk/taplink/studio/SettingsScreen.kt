package hk.taplink.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(controller: WalletController) {
    val wallet = controller.wallet ?: return
    val prefs = wallet.prefs
    var inbox by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val settingsScroll = rememberScrollState()
    val inboxScroll = rememberLazyListState()
    val enabled = !controller.busy

    fun update(transform: (Prefs) -> Prefs) {
        controller.run({ changePrefs(transform) })
    }

    Column(Modifier.fillMaxSize()) {
        Text("Settings", Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.headlineLarge)
        TabRow(selectedTabIndex = if (inbox) 1 else 0) {
            Tab(selected = !inbox, onClick = { inbox = false }, text = { Text("Preferences") })
            Tab(selected = inbox, onClick = { inbox = true }, text = {
                Text(if (wallet.notes.isEmpty()) "Inbox" else "Inbox (${wallet.notes.size})")
            })
        }
        if (inbox) {
            MessageInbox(wallet.notes, inboxScroll)
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(settingsScroll).padding(horizontal = 20.dp)) {
                PreferenceGroup("Wallet") {
                    val selected = wallet.cards.firstOrNull { it.id == prefs.selected }
                    PreferenceLink("Default card", selected?.let { "${it.name} · #${it.id}" } ?: "No card selected",
                        enabled && wallet.cards.isNotEmpty()) { dialog = "card" }
                    PreferenceDivider()
                    PreferenceSwitch("Hide balances", "Mask card balances in the wallet and widget.",
                        prefs.hidden, enabled) { value -> update { it.copy(hidden = value) } }
                    PreferenceDivider()
                    PreferenceSwitch("Show balance on widget",
                        if (prefs.hidden) "Your widget choice applies when you turn off Hide balances."
                        else "Allow the home-screen widget to show your selected card's balance.",
                        prefs.widgetBalanceVisible, enabled) { value -> update { it.copy(widgetBalanceVisible = value) } }
                    PreferenceDivider()
                    PreferenceSwitch("Shake to hide or reveal", "Toggle balance visibility while viewing the wallet.",
                        prefs.shake, enabled) { value -> update { it.copy(shake = value) } }
                }
                PreferenceGroup("Funding & alerts") {
                    PreferenceSwitch("Automatic reload", "Add demo funds after a payment leaves the card below its threshold.",
                        prefs.auto, enabled) { value -> update { it.copy(auto = value) } }
                    PreferenceDivider()
                    PreferenceLink("Threshold & reload amount",
                        "Low balance below ${money(prefs.threshold)} · Reload ${money(prefs.reload)}", enabled) {
                        dialog = "amounts"
                    }
                    PreferenceDivider()
                    PreferenceSwitch("Low-balance alerts", "Alert me when a payment finishes below the threshold.",
                        prefs.alerts, enabled) { value -> update { it.copy(alerts = value) } }
                }
                PreferenceGroup("Reminders") {
                    NotificationAccess()
                    PreferenceSwitch("Scheduled reminders", "Allow occasional reminders about the demo wallet.",
                        prefs.reminders, enabled) { value -> update { it.copy(reminders = value) } }
                }
                PreferenceGroup("Appearance") {
                    PreferenceLink("Theme", prefs.theme, enabled) { dialog = "theme" }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    when (dialog) {
        "card" -> PreferencePicker("Default card", wallet.cards, { it.id == prefs.selected }, {
            "${it.name} · #${it.id}" + if (it.frozen) " · Frozen" else ""
        }, enabled, { dialog = null }) { card ->
            if (!controller.busy) {
                controller.run({ select(card.id) })
                dialog = null
            }
        }
        "theme" -> PreferencePicker("Theme", listOf("System", "Light", "Dark"), { it == prefs.theme }, { it },
            enabled, { dialog = null }) { theme ->
            if (!controller.busy) {
                controller.run({ changePrefs { it.copy(theme = theme) } })
                dialog = null
            }
        }
        "amounts" -> FundingAmountsDialog(prefs, enabled, { dialog = null }) { threshold, reload ->
            controller.run({ changePrefs { it.copy(threshold = threshold, reload = reload) } })
        }
    }
}

@Composable
private fun PreferenceGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun PreferenceDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RowScope.PreferenceText(title: String, detail: String) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun PreferenceSwitch(title: String, detail: String, checked: Boolean, enabled: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = set)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PreferenceText(title, detail)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun PreferenceLink(title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        PreferenceText(title, detail)
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun <T> PreferencePicker(title: String, options: List<T>, selected: (T) -> Boolean, caption: (T) -> String,
    enabled: Boolean, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).selectableGroup()) {
            items(options) { option ->
                Row(Modifier.fillMaxWidth().selectable(selected = selected(option), enabled = enabled,
                    role = Role.RadioButton, onClick = { onPick(option) }).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected(option), onClick = null, enabled = enabled)
                    Spacer(Modifier.width(12.dp))
                    Text(caption(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun FundingAmountsDialog(prefs: Prefs, enabled: Boolean, onDismiss: () -> Unit, onSave: (Long, Long) -> Unit) {
    var threshold by rememberSaveable(stateSaver = walletDraftSaver) { mutableStateOf(BigDecimal.valueOf(prefs.threshold, 2).toPlainString()) }
    var reload by rememberSaveable(stateSaver = walletDraftSaver) { mutableStateOf(BigDecimal.valueOf(prefs.reload, 2).toPlainString()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    fun parsed(value: String, allowZero: Boolean = false): Long? = try {
        parseCents(value, allowZero)
    } catch (_: IllegalArgumentException) {
        null
    }
    val thresholdCents = parsed(threshold, allowZero = true)
    val reloadCents = parsed(reload)
    LaunchedEffect(saving, enabled, prefs.threshold, prefs.reload) {
        if (saving && enabled) {
            if (prefs.threshold == thresholdCents && prefs.reload == reloadCents) onDismiss()
            saving = false
        }
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Funding amounts") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The threshold applies to both automatic reload and low-balance alerts.",
                style = MaterialTheme.typography.bodyMedium)
            FundingField("Low-balance threshold (HK$)", threshold, { threshold = it },
                submitted && thresholdCents == null, ImeAction.Next, !saving, "Enter HK$0 to HK$1,000,000 with up to two decimal places.")
            FundingField("Reload amount (HK$)", reload, { reload = it }, submitted && reloadCents == null,
                ImeAction.Done, !saving, "Enter HK$0.01 to HK$1,000,000 with up to two decimal places.")
        }
    }, confirmButton = { Button(enabled = enabled && !saving, onClick = {
        submitted = true
        if (thresholdCents != null && reloadCents != null) {
            saving = true
            onSave(thresholdCents, reloadCents)
        }
    }) { Text("Save amounts") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun FundingField(label: String, value: String, onEdit: (String) -> Unit, invalid: Boolean,
    ime: ImeAction, enabled: Boolean, error: String) {
    OutlinedTextField(value = value, onValueChange = onEdit, label = { Text(label) }, singleLine = true, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ime),
        supportingText = if (invalid) { { Text(error) } } else null)
}

private val inboxDayFormat = DateTimeFormatter.ofPattern("EEEE, d MMM uuuu", Locale.ENGLISH)
private val inboxTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

@Composable
private fun MessageInbox(notes: List<Note>, state: androidx.compose.foundation.lazy.LazyListState) {
    val zone = ZoneId.of("Asia/Hong_Kong")
    val groups = remember(notes, zone) {
        notes.sortedWith(compareByDescending<Note> { it.at }.thenByDescending { it.id })
            .groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        if (groups.isEmpty()) {
            item {
                Text("No messages yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Wallet alerts and reminders will appear here.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        groups.forEach { (day, messages) ->
            item("day-$day") {
                Text(inboxDayFormat.format(day), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            items(messages, key = { "note-${it.id}" }) { note ->
                Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(note.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(5.dp))
                        Text(inboxTimeFormat.format(Instant.ofEpochMilli(note.at).atZone(zone)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item("space-$day") { Spacer(Modifier.height(8.dp)) }
        }
    }
}
