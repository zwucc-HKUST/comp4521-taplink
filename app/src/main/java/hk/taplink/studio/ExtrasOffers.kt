package hk.taplink.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card as SurfaceCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal fun LazyListScope.extrasOfferItems(
    coupons: List<ExtrasCoupon>,
    today: LocalDate,
    savedOnly: Boolean,
    onFilter: (Boolean) -> Unit,
    onDetails: (String) -> Unit
) {
    item("filters") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !savedOnly, onClick = { onFilter(false) }, label = { Text("All offers") })
            FilterChip(selected = savedOnly, onClick = { onFilter(true) }, label = { Text("Favorites") })
        }
    }
    val visible = if (savedOnly) coupons.filter { it.favorite } else coupons
    if (visible.isEmpty()) {
        item("empty") {
            Text(if (savedOnly) "No saved demo offers yet. Open an offer to save it." else "No demo offers available.")
        }
    }
    items(visible, key = { it.offer.id }) { coupon ->
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Demo offer · ${coupon.offer.merchant}", style = MaterialTheme.typography.labelLarge)
                Text(coupon.offer.title, style = MaterialTheme.typography.titleLarge)
                Text("Valid through ${coupon.expires.extrasDate()} (HKT)")
                if (coupon.favorite) Text("Saved to favorites", style = MaterialTheme.typography.labelMedium)
                ExtrasRedemptionStatus(coupon, today)
                OutlinedButton(onClick = { onDetails(coupon.offer.id) }) { Text("Offer details") }
            }
        }
    }
}

@Composable
private fun ExtrasRedemptionStatus(coupon: ExtrasCoupon, today: LocalDate) {
    val at = coupon.redeemedAt
    when {
        at != null -> Text("Demo coupon redeemed · ${extrasTime(at)} HKT", style = MaterialTheme.typography.bodyMedium)
        today > coupon.expires -> Text("Demo coupon expired", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ExtrasOfferDialog(
    coupon: ExtrasCoupon,
    today: LocalDate,
    ready: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onRedeem: () -> Unit
) {
    val available = coupon.redeemedAt == null && today <= coupon.expires
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(coupon.offer.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Demo offer · ${coupon.offer.merchant}", style = MaterialTheme.typography.labelLarge)
                Text(coupon.offer.details)
                Text("Valid through ${coupon.expires.extrasDate()} (HKT). One simulated redemption per wallet.")
                ExtrasRedemptionStatus(coupon, today)
                OutlinedButton(onClick = onFavorite, enabled = ready) {
                    Text(if (coupon.favorite) "Remove from favorites" else "Save to favorites")
                }
            }
        },
        confirmButton = {
            if (available) {
                Button(onClick = onRedeem, enabled = ready) { Text("Redeem demo coupon") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (available) TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
