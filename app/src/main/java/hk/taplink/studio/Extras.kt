package hk.taplink.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card as SurfaceCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val extrasDateFormat = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
private val extrasMonthFormat = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH)
private val extrasTimeFormat = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.ENGLISH).withZone(extrasZone)
internal fun LocalDate.extrasDate(): String = format(extrasDateFormat)
internal fun extrasTime(millis: Long): String = extrasTimeFormat.format(Instant.ofEpochMilli(millis))

@Composable
fun ExtrasScreen(controller: WalletController) {
    val wallet = controller.wallet ?: return
    val card = wallet.cards.firstOrNull { it.id == wallet.prefs.selected }
    if (card == null) {
        Text("Select a wallet card to open passes and offers.", Modifier.padding(16.dp))
        return
    }
    var offersTab by rememberSaveable { mutableStateOf(false) }
    key(card.id) {
        ExtrasContent(controller, wallet, card, offersTab) { offersTab = it }
    }
}

@Composable
private fun ExtrasContent(
    controller: WalletController,
    wallet: Wallet,
    selectedCard: Card,
    offersTab: Boolean,
    selectOffers: (Boolean) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val busy = controller.busy
    var records by remember { mutableStateOf<ExtrasRecords?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var loadedRevision by remember { mutableIntStateOf(-1) }
    var readError by remember { mutableStateOf<String?>(null) }
    var message by rememberSaveable(stateSaver = walletDraftSaver) { mutableStateOf("") }
    var review by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewEpochDay by rememberSaveable { mutableLongStateOf(0L) }
    var intentId by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var offerId by rememberSaveable { mutableStateOf<String?>(null) }
    var today by remember { mutableStateOf(LocalDate.now(extrasZone)) }

    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now(extrasZone)
            val midnight = today.plusDays(1).atStartOfDay(extrasZone).toInstant().toEpochMilli()
            delay((midnight - System.currentTimeMillis()).coerceIn(1L, 60_000L))
        }
    }
    // An independent read also recovers the committed result when rotation replaces the
    // composable that launched an asynchronous controller action.
    val requestedRevision = revision
    val month = YearMonth.from(today)
    LaunchedEffect(context, selectedCard.id, busy, requestedRevision, month, wallet) {
        if (busy) return@LaunchedEffect
        readError = null
        try {
            records = withContext(Dispatchers.IO) {
                Store(context).use { it.readExtras(selectedCard.id) }
            }
            loadedRevision = requestedRevision
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            readError = error.localizedMessage ?: "Could not read your passes and offers."
        }
    }

    val data = records?.takeIf { it.month == month }
    val card = data?.card ?: selectedCard
    val spend = data?.transportSpend ?: 0L
    val ready = !busy && data != null && loadedRevision == revision && readError == null
    val passOnCard = data?.pass?.let { it.endExclusive > today } == true
    val claimed = data?.claims?.any { it.month == month } == true
    val mayBuy = ready && !passOnCard && !card.frozen && card.cents >= extrasPassPrice
    val mayClaim = ready && !claimed && !card.frozen && spend >= extrasClaimThreshold

    fun beginReview(type: String) {
        message = ""
        reviewEpochDay = today.toEpochDay()
        intentId = UUID.randomUUID().toString()
        review = type
    }
    fun submit(success: String? = null, action: Store.() -> Unit) {
        if (!ready || controller.busy) return
        revision++
        controller.run(action) {
            if (success != null) message = success
        }
    }

    // The ledger survives rotation and process recreation, including loss of the
    // composable that submitted the review. Keep its operation until we see the receipt.
    val reviewOperation = when (review) {
        "pass" -> "extras.pass.${card.id}.$intentId"
        "claim" -> "extras.claim.${card.id}.${YearMonth.from(LocalDate.ofEpochDay(reviewEpochDay))}"
        else -> null
    }
    LaunchedEffect(reviewOperation, wallet.entries) {
        if (reviewOperation != null && wallet.entries.any { it.operation == reviewOperation && it.kind != "Reload" }) {
            message = if (review == "pass") "Demo monthly pass purchased for ${card.name}."
                else "${money(extrasClaimCredit)} in demo subsidy credited to ${card.name}."
            review = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Passes & offers", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.headlineSmall)
        TabRow(selectedTabIndex = if (offersTab) 1 else 0) {
            Tab(selected = !offersTab, onClick = { selectOffers(false); message = "" },
                text = { Text("Transport") })
            Tab(selected = offersTab, onClick = { selectOffers(true); message = "" },
                text = { Text("Offers") })
        }
        val error = readError
        if (error != null) {
            Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { revision++ }, enabled = !busy) { Text("Retry") }
        } else if (data == null || loadedRevision != revision) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (message.isNotEmpty()) {
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (data != null) {
            key(offersTab) {
                LazyColumn(modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (offersTab) {
                        extrasOfferItems(data.coupons, today, savedOnly,
                            onFilter = { savedOnly = it }, onDetails = { offerId = it; message = "" })
                    } else {
                        item("pass") {
                            ExtrasPassPanel(card, data.pass, today, mayBuy) { beginReview("pass") }
                        }
                        item("subsidy") {
                            ExtrasSubsidyPanel(card, month, spend, claimed, mayClaim) { beginReview("claim") }
                        }
                        item("history") {
                            Text("Claim history", style = MaterialTheme.typography.titleMedium)
                            if (data.claims.isEmpty()) Text("No demo subsidy claims for this card yet.")
                        }
                        items(data.claims, key = { it.month.toString() }) { claim ->
                            Column {
                                Text("${claim.month.format(extrasMonthFormat)} · ${money(claim.credit)} credited",
                                    style = MaterialTheme.typography.titleSmall)
                                Text("${money(claim.spend)} in transport payments at claim time")
                                Text("Claimed ${extrasTime(claim.at)} HKT", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    val reviewType = review
    if (reviewType != null && data != null) {
        val isPass = reviewType == "pass"
        val day = LocalDate.ofEpochDay(reviewEpochDay)
        val reviewedMonth = YearMonth.from(day)
        val samePeriod = if (isPass) day == today else reviewedMonth == month
        val canConfirm = samePeriod && if (isPass) mayBuy else mayClaim
        AlertDialog(
            onDismissRequest = { if (!controller.busy) review = null },
            title = { Text(if (isPass) "Buy a demo monthly pass?" else "Claim the fictional subsidy?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium)
                    if (isPass) {
                        Text("Pay ${money(extrasPassPrice)} from this card’s demo balance.")
                        Text("Valid ${day.extrasDate()} through ${day.plusMonths(1).minusDays(1).extrasDate()} (HKT).")
                    } else {
                        Text("Credit ${money(extrasClaimCredit)} to this card for ${reviewedMonth.format(extrasMonthFormat)}.")
                        Text("One fictional credit per card per month. For this simulation only.")
                    }
                    if (!samePeriod) Text("The date changed. Close this review and check the new period.")
                    else if (isPass && passOnCard) Text("This card already has a pass for this period.")
                    else if (!isPass && claimed) Text("This month’s credit is already in the claim history.")
                }
            },
            confirmButton = {
                Button(enabled = canConfirm, onClick = {
                    if (isPass) {
                        val token = intentId
                        submit { buyExtrasPass(card.id, day, token) }
                    } else {
                        submit {
                            claimExtrasSubsidy(card.id, reviewedMonth)
                        }
                    }
                }) { Text(if (isPass) "Pay ${money(extrasPassPrice)}" else "Claim credit") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { review = null }) { Text("Close") } }
        )
    }
    val coupon = data?.coupons?.firstOrNull { it.offer.id == offerId }
    if (coupon != null) {
        ExtrasOfferDialog(coupon, today, ready, onDismiss = { offerId = null },
            onFavorite = {
                val favorite = !coupon.favorite
                submit(if (favorite) "Demo offer saved." else "Demo offer removed from favorites.") {
                    favoriteExtrasOffer(coupon.offer.id, favorite)
                }
            },
            onRedeem = {
                submit("Demo coupon redeemed for ${coupon.offer.title}.") { redeemExtrasCoupon(coupon.offer.id) }
            })
    }
}

@Composable
private fun ExtrasPassPanel(card: Card, pass: ExtrasPass?, today: LocalDate, mayBuy: Boolean, onBuy: () -> Unit) {
    SurfaceCard(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.name, style = MaterialTheme.typography.labelLarge)
            Text("Demo monthly pass", style = MaterialTheme.typography.headlineSmall)
            when {
                pass == null -> Text("No monthly pass on this card.")
                pass.covers(today) -> Text("Active · ${pass.start.extrasDate()} through ${pass.endExclusive.minusDays(1).extrasDate()} (HKT)")
                today < pass.start -> Text("Upcoming · ${pass.start.extrasDate()} through ${pass.endExclusive.minusDays(1).extrasDate()} (HKT)")
                else -> Text("Expired · last valid on ${pass.endExclusive.minusDays(1).extrasDate()} (HKT)")
            }
            if (pass == null || today >= pass.endExclusive) {
                Text("${money(extrasPassPrice)} for one month, starting on the purchase date.")
                if (card.frozen) Text("Unfreeze this card to buy a pass.")
                else if (card.cents < extrasPassPrice) Text("This card needs a top-up before purchase.")
                Button(onClick = onBuy, enabled = mayBuy) { Text("Review pass purchase") }
            }
        }
    }
}

@Composable
private fun ExtrasSubsidyPanel(
    card: Card, month: YearMonth, spend: Long, claimed: Boolean, mayClaim: Boolean, onClaim: () -> Unit
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(month.format(extrasMonthFormat), style = MaterialTheme.typography.labelLarge)
            Text("${money(spend)} in transport payments", style = MaterialTheme.typography.titleLarge)
            Text("Fictional subsidy", style = MaterialTheme.typography.titleMedium)
            Text("Spend ${money(extrasClaimThreshold)} on Transport payments, including passes, to claim one " +
                "${money(extrasClaimCredit)} demo credit per card each Hong Kong calendar month.")
            Text("This simulation has no connection to a government benefit.")
            when {
                claimed -> Text("Claimed for this month.")
                card.frozen -> Text("Unfreeze this card to claim.")
                spend < extrasClaimThreshold -> Text("${money(extrasClaimThreshold - spend)} more transport spending needed.")
            }
            if (!claimed) Button(onClick = onClaim, enabled = mayClaim) { Text("Review subsidy claim") }
        }
    }
}
