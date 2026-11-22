package hk.taplink.studio

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH)
private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val receiptFormat = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm:ss z", Locale.ENGLISH)

private data class LedgerFilter(
    val cardId: Long? = null,
    val kind: String? = null,
    val category: String? = null,
    val start: Long? = null,
    val end: Long? = null
) {
    val count get() = listOf(cardId, kind, category).count { it != null } +
        if (start != null || end != null) 1 else 0

    fun includesDay(day: Long) = (start == null || day >= start) && (end == null || day <= end)

    companion object {
        val saver = mapSaver(
            save = { filter: LedgerFilter ->
                mapOf("card" to filter.cardId, "kind" to filter.kind, "category" to filter.category,
                    "start" to filter.start, "end" to filter.end)
            },
            restore = {
                LedgerFilter(it["card"] as Long?, it["kind"] as String?, it["category"] as String?,
                    it["start"] as Long?, it["end"] as Long?)
            }
        )
    }
}

private fun Entry.date(zone: ZoneId) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
private fun Entry.amountText() = when {
    delta < 0 -> "−" + money(-delta)
    delta > 0 -> "+" + money(delta)
    else -> money(0)
}

private fun cardCaption(id: Long, cards: Map<Long, Card>): String =
    cards[id]?.let { "${it.name} · #$id" } ?: "Card #$id"

@Composable
fun ActivityScreen(controller: WalletController) {
    val wallet = controller.wallet ?: return
    val zone = ZoneId.of("Asia/Hong_Kong")
    val cards = remember(wallet.cards) { wallet.cards.associateBy { it.id } }
    val categories = remember(wallet.entries) { wallet.entries.map { it.category }.distinct().sorted() }
    var query by rememberSaveable(stateSaver = walletDraftSaver) { mutableStateOf("") }
    var filters by rememberSaveable(stateSaver = LedgerFilter.saver) { mutableStateOf(LedgerFilter()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var monthChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptId by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = LocalFocusManager.current

    val entries = remember(wallet.entries, zone, filters, query) {
        val term = query.trim()
        wallet.entries.filter { entry ->
            (filters.cardId == null || filters.cardId == entry.cardId) &&
                (filters.kind == null || filters.kind == entry.kind) &&
                (filters.category == null || filters.category == entry.category) &&
                filters.includesDay(entry.date(zone).toEpochDay()) && entry.label.contains(term, ignoreCase = true)
        }.sortedWith(compareByDescending<Entry> { it.at }.thenByDescending { it.id })
    }
    val dates = remember(entries, zone) { entries.groupBy { it.date(zone) } }
    val today = LocalDate.now(zone)
    val months = remember(dates, filters, today) {
        buildSet<YearMonth> {
            dates.keys.forEach { add(YearMonth.from(it)) }
            filters.start?.let { add(YearMonth.from(LocalDate.ofEpochDay(it))) }
            filters.end?.let { add(YearMonth.from(LocalDate.ofEpochDay(it))) }
            if (filters.includesDay(today.toEpochDay())) add(YearMonth.from(today))
        }.sortedDescending()
    }
    val defaultMonth = dates.keys.firstOrNull()?.let { YearMonth.from(it) } ?: months.first()
    val month = monthChoice?.let { YearMonth.parse(it) }?.takeIf { it in months } ?: defaultMonth
    val payments = remember(entries, month, zone) {
        entries.filter { it.kind == "Payment" && YearMonth.from(it.date(zone)) == month }
    }
    val breakdown = remember(payments) {
        payments.groupBy { it.category }.map { (category, rows) ->
            CategorySpend(category, rows.sumOf { -it.delta })
        }.sortedWith(compareByDescending<CategorySpend> { it.cents }.thenBy { it.category })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp)
    ) {
        item("analysis") {
            Text("Activity", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            SpendingSummary(month, months, { monthChoice = it.toString() }, payments.size, breakdown, categories)
            Spacer(Modifier.height(24.dp))
        }
        item("history-controls") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transaction history", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { focus.clearFocus(); editing = true }) {
                    Text(if (filters.count == 0) "Filters" else "Filters (${filters.count})")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search merchant or description") },
                trailingIcon = if (query.isEmpty()) null else { { TextButton(onClick = { query = "" }) { Text("Clear") } } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
            )
            val scope = buildList {
                filters.cardId?.let { add(cardCaption(it, cards)) }
                filters.kind?.let { add(it) }
                filters.category?.let { add(it) }
                filters.start?.let { add("From ${LocalDate.ofEpochDay(it)}") }
                filters.end?.let { add("Through ${LocalDate.ofEpochDay(it)}") }
            }.joinToString(" · ").ifEmpty { "All cards · All dates" }
            Text("${entries.size} ${if (entries.size == 1) "record" else "records"} · $scope",
                Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entries.isEmpty()) {
            item("empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                    val restricted = query.isNotBlank() || filters.count > 0
                    Text(if (restricted) "No matching transactions" else "No transactions yet",
                        style = MaterialTheme.typography.titleMedium)
                    Text(if (restricted) "Try another search or clear your filters."
                        else "Your simulated payments and top-ups will appear here.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (restricted) TextButton(onClick = { filters = LedgerFilter(); query = "" }) {
                        Text("Clear search and filters")
                    }
                }
            }
        }
        dates.forEach { (date, rows) ->
            item("day-$date") {
                Text(dayFormat.format(date), modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            items(rows, key = { "entry-${it.id}" }) { entry ->
                LedgerRow(entry, cards[entry.cardId]?.name ?: "Card #${entry.cardId}", zone) {
                    focus.clearFocus()
                    receiptId = entry.id
                }
            }
        }
    }

    if (editing) {
        LedgerFiltersDialog(wallet, cards, categories, filters, onDismiss = { editing = false }) {
            filters = it
            editing = false
        }
    }
    val receipt = receiptId?.let { id -> wallet.entries.firstOrNull { it.id == id } }
    if (receipt != null) key(receipt.id) {
        StoredReceipt(receipt, wallet, cards, zone, onDismiss = { receiptId = null })
    }
}

private data class CategorySpend(val category: String, val cents: Long)

@Composable
private fun SpendingSummary(
    month: YearMonth, months: List<YearMonth>, onMonth: (YearMonth) -> Unit,
    paymentCount: Int, breakdown: List<CategorySpend>, categories: List<String>
) {
    val total = remember(breakdown) { breakdown.sumOf { it.cents } }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.45f
    val colors = remember(categories, dark) {
        val base = if (dark) listOf(0xFF75D1C2, 0xFFFFAB83, 0xFFA8BDE9, 0xFFE3CD80, 0xFFD5A7D2)
            else listOf(0xFF116B63, 0xFFB54F26, 0xFF48688A, 0xFF9A761B, 0xFF895C82)
        categories.mapIndexed { i, name -> name to (
            if (i < base.size) Color(base[i])
            else Color.hsv((i * 137.5f) % 360f, if (dark) 0.4f else 0.65f, if (dark) 0.9f else 0.58f)
        ) }.toMap()
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Monthly spending", style = MaterialTheme.typography.titleMedium)
            ActivityMenu(monthFormat.format(month), months, { monthFormat.format(it) }, onMonth)
            Text(money(total), style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Text("$paymentCount ${if (paymentCount == 1) "payment" else "payments"}",
                style = MaterialTheme.typography.bodySmall)
            if (breakdown.isEmpty()) {
                Text("No payments match this month and your filters.", Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                val chartColors = remember(colors, breakdown) { breakdown.map { colors.getValue(it.category).toArgb() } }
                val chartValues = remember(breakdown) { breakdown.map { it.cents } }
                AndroidView(
                    factory = { SpendingCategoryView(it) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(16.dp),
                    update = { it.setSegments(chartValues, chartColors) }
                )
                breakdown.forEach { point ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(colors.getValue(point.category), CircleShape))
                        Spacer(Modifier.width(9.dp))
                        Text(point.category, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(money(point.cents), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// The adjacent text legend exposes the same amounts to accessibility services.
private class SpendingCategoryView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private var values = emptyList<Long>()
    private var colors = emptyList<Int>()
    private var total = 0.0

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setSegments(values: List<Long>, colors: List<Int>) {
        if (values == this.values && colors == this.colors) return
        this.values = values
        this.colors = colors
        total = values.sumOf { it.toDouble() }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clip.reset()
        clip.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (total <= 0.0) return
        val checkpoint = canvas.save()
        canvas.clipPath(clip)
        var cumulative = 0.0
        var left = 0f
        values.forEachIndexed { index, cents ->
            cumulative += cents
            val right = (width * cumulative / total).toFloat()
            paint.color = colors[index]
            canvas.drawRect(left, 0f, right, height.toFloat(), paint)
            left = right
        }
        canvas.restoreToCount(checkpoint)
    }
}

@Composable
private fun LedgerRow(entry: Entry, card: String, zone: ZoneId, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${entry.kind} · $card", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.amountText(), style = MaterialTheme.typography.titleSmall,
                    color = if (entry.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(timeFormat.format(Instant.ofEpochMilli(entry.at).atZone(zone)), style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun <T> ActivityMenu(label: String, options: List<T>, caption: (T) -> String, onSelect: (T) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            options.forEach { value ->
                DropdownMenuItem(text = { Text(caption(value)) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@Composable
private fun LedgerFiltersDialog(
    wallet: Wallet, cards: Map<Long, Card>, categories: List<String>, initial: LedgerFilter,
    onDismiss: () -> Unit, onApply: (LedgerFilter) -> Unit
) {
    var draft by rememberSaveable(stateSaver = LedgerFilter.saver) { mutableStateOf(initial) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val valid = (draft.start ?: Long.MIN_VALUE) <= (draft.end ?: Long.MAX_VALUE)
    val cardIds = remember(wallet.cards, wallet.entries) {
        (wallet.cards.map { it.id } + wallet.entries.map { it.cardId }).distinct()
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Filter activity") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Card", style = MaterialTheme.typography.labelLarge)
            val cardText = { id: Long? -> id?.let { cardCaption(it, cards) } ?: "All cards" }
            ActivityMenu(cardText(draft.cardId), listOf(null) + cardIds, cardText) { draft = draft.copy(cardId = it) }
            Text("Transaction type", style = MaterialTheme.typography.labelLarge)
            val kindText = { kind: String? -> kind ?: "All types" }
            ActivityMenu(kindText(draft.kind), listOf(null, "Payment", "Top-up", "Reload"), kindText) {
                draft = draft.copy(kind = it)
            }
            Text("Category", style = MaterialTheme.typography.labelLarge)
            val categoryText = { category: String? -> category ?: "All categories" }
            ActivityMenu(categoryText(draft.category), listOf(null) + categories, categoryText) {
                draft = draft.copy(category = it)
            }
            Text("Date range", style = MaterialTheme.typography.labelLarge)
            FilterDate("From", draft.start, { picking = "start" }, { draft = draft.copy(start = null) })
            FilterDate("Through", draft.end, { picking = "end" }, { draft = draft.copy(end = null) })
            if (!valid) Text("Choose an end date on or after the start date.", color = MaterialTheme.colorScheme.error)
            if (draft.count > 0) TextButton(onClick = { draft = LedgerFilter() }) { Text("Reset filters") }
        }
    }, confirmButton = { Button(enabled = valid, onClick = { onApply(draft) }) { Text("Apply filters") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })

    picking?.let { field ->
        val startField = field == "start"
        val initialDay = if (startField) draft.start ?: draft.end else draft.end ?: draft.start
        LedgerDatePicker(initialDay, onSelected = {
            draft = if (startField) draft.copy(start = it) else draft.copy(end = it)
        }, onDismiss = { picking = null })
    }
}

@Composable
private fun FilterDate(label: String, day: Long?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPick) { Text("$label · ${day?.let { LocalDate.ofEpochDay(it).toString() } ?: "Any date"}") }
        if (day != null) TextButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun LedgerDatePicker(initialDay: Long?, onSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onSelected)
    val dismiss by rememberUpdatedState(onDismiss)
    fun createPicker(date: LocalDate): DatePickerDialog =
        DatePickerDialog(context, { _, year, month, day ->
            select(LocalDate.of(year, month + 1, day).toEpochDay())
        }, date.year, date.monthValue - 1, date.dayOfMonth)
    val dialog = rememberSaveable(saver = Saver<DatePickerDialog, Long>(
        save = { picker ->
            with(picker.datePicker) { LocalDate.of(year, month + 1, dayOfMonth).toEpochDay() }
        },
        restore = { createPicker(LocalDate.ofEpochDay(it)) }
    )) { createPicker(initialDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()) }
    DisposableEffect(dialog) {
        dialog.setOnDismissListener { dismiss() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}

@Composable
private fun StoredReceipt(entry: Entry, wallet: Wallet, cards: Map<Long, Card>, zone: ZoneId, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var revealed by remember(entry.id, wallet.prefs.hidden) { mutableStateOf(false) }
    val visible = !wallet.prefs.hidden || revealed
    val reload = remember(entry, wallet.entries) {
        if (entry.kind == "Payment" && entry.operation.isNotBlank()) wallet.entries.firstOrNull {
            it.cardId == entry.cardId && it.operation == entry.operation && it.kind == "Reload"
        } else null
    }
    fun balance(cents: Long) = if (visible) money(cents) else "Hidden"
    val fields = buildList {
        add("Receipt" to "#${entry.id}")
        add("Card" to cardCaption(entry.cardId, cards))
        add("Date and time" to receiptFormat.format(Instant.ofEpochMilli(entry.at).atZone(zone)))
        add("Category" to entry.category)
        add("Balance after ${entry.kind.lowercase(Locale.ENGLISH)}" to balance(entry.after))
        if (reload != null) {
            add("Linked reload #${reload.id}" to reload.amountText())
            add("Balance after reload" to balance(reload.after))
        }
    }
    val title = "Simulated ${entry.kind.lowercase(Locale.ENGLISH)}"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("$title receipt") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(entry.label, style = MaterialTheme.typography.titleMedium)
            Text(entry.amountText(), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            fields.forEach { (name, value) ->
                Column(Modifier.padding(top = 9.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (wallet.prefs.hidden) TextButton(onClick = { revealed = !revealed }) {
                Text(if (revealed) "Hide receipt balances" else "Reveal receipt balances")
            }
        }
    }, confirmButton = { Button(onClick = {
        val text = buildString {
            appendLine("TapLink · $title receipt")
            appendLine(entry.label)
            appendLine("Amount: ${entry.amountText()}")
            fields.forEach { (name, value) -> appendLine("$name: $value") }
        }.trimEnd()
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "$title receipt #${entry.id}").putExtra(Intent.EXTRA_TEXT, text)
        try {
            context.startActivity(Intent.createChooser(intent, "Share simulated receipt"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No sharing app is available.", Toast.LENGTH_SHORT).show()
        }
    }) { Text("Share receipt") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
