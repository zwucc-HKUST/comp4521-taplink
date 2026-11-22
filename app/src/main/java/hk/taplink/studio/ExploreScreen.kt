package hk.taplink.studio

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Suppress("UNUSED_PARAMETER")
@Composable
fun ExploreScreen(controller: WalletController) {
    val context = LocalContext.current
    var districtName by rememberSaveable { mutableStateOf(ExploreCatalogue.districts.first().name) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var mapView by rememberSaveable { mutableStateOf(true) }
    var showDistricts by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Int?>(null) }
    var useDevice by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var fix by remember { mutableStateOf<Location?>(null) }
    var locationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val district = ExploreCatalogue.districts.first { it.name == districtName }
    val lifecycle = remember(context) { context.hostActivity()?.lifecycle }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        locationMessage = if (allowed) null else "Location access is off. Choose a district to set your starting point."
        useDevice = allowed
        attempt++
    }
    DisposableEffect(context, lifecycle, useDevice, attempt) {
        fix = null
        var cancel: (() -> Unit)? = null
        val observer = LifecycleEventObserver { _, event ->
            if (!useDevice) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    cancel = locateOnce(context) { result ->
                        fix = result
                        if (result == null) {
                            useDevice = false
                            locationMessage = "Couldn’t get a recent location. Choose a district or try again."
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    cancel?.invoke()
                    cancel = null
                    fix = null
                }
                else -> Unit
            }
        }
        if (useDevice && lifecycle == null) {
            useDevice = false
            locationMessage = "Device location is unavailable here. Choose a district."
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            cancel?.invoke()
        }
    }

    val originLat = fix?.latitude ?: district.latitude
    val originLon = fix?.longitude ?: district.longitude
    val originName = if (fix == null) "$districtName district centre" else "your approximate location"
    val ranked = remember(filter, originLat, originLon) {
        ExploreCatalogue.stops.asSequence()
            .filter { filter == "All" || it.type.plural == filter }
            .map { it to it.metresFrom(originLat, originLon) }.sortedBy { it.second }.toList()
    }
    var featured by remember { mutableStateOf(ExploreCatalogue.featured(context)) }
    DisposableEffect(context) {
        val prefs = ExploreCatalogue.preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ExploreCatalogue.EDITION) featured = ExploreCatalogue.featured(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        featured = ExploreCatalogue.featured(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Harbour guide", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Demo service points around Victoria Harbour.", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Measure from", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (fix != null) "Approximate device location" else "$districtName district centre",
                        style = MaterialTheme.typography.titleMedium)
                    if (useDevice && fix == null) {
                        Text("Looking for a location…", style = MaterialTheme.typography.bodySmall)
                    }
                    locationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showDistricts = true }) { Text("Choose district") }
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = {
                            locationMessage = null
                            if (context.canLocate()) {
                                useDevice = true
                                attempt++
                            } else {
                                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        }) { Text("Use my location") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("All") + ServiceType.entries.map { it.plural }).forEach { type ->
                    FilterChip(selected = type == filter, onClick = { filter = type }, label = { Text(type) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${ranked.size} places", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = mapView, onClick = { mapView = true }, label = { Text("Map") })
                    FilterChip(selected = !mapView, onClick = { mapView = false }, label = { Text("List") })
                }
            }
        }
        if (ranked.isEmpty()) {
            item { Text("No demo places match this filter.") }
        } else if (mapView) {
            item {
                HarbourMap(ranked.map { it.first }) { detailId = it.id }
                Text("Illustrated map · Tap a numbered place for details",
                    modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                val pick = ranked.find { it.first == featured }
                val (stop, distance) = pick ?: ranked.first()
                Text(if (pick != null) "Catalogue pick" else "Nearest match",
                    modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
                StopRow(stop, distance) { detailId = stop.id }
            }
        } else {
            item {
                Text("Straight-line distance from $originName", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(ranked, key = { it.first.id }) { (stop, distance) ->
                StopRow(stop, distance) { detailId = stop.id }
                HorizontalDivider()
            }
        }
    }

    if (showDistricts) {
        AlertDialog(onDismissRequest = { showDistricts = false }, title = { Text("Choose a district") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Distances use the district centre.")
                    ExploreCatalogue.districts.forEach { choice ->
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                            useDevice = false
                            fix = null
                            locationMessage = null
                            districtName = choice.name
                            showDistricts = false
                        }) {
                            Text(if (!useDevice && choice == district) "${choice.name} · Selected" else choice.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDistricts = false }) { Text("Close") } }
        )
    }
    val detail = ExploreCatalogue.stops.find { it.id == detailId }
    if (detail != null) {
        StopDetails(context, detail, formatDistance(detail.metresFrom(originLat, originLon)), originName) {
            detailId = null
        }
    }
}

@Composable
private fun StopRow(stop: DemoStop, distance: Float, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text(stop.id.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stop.name, style = MaterialTheme.typography.titleMedium)
                Text("${stop.type.title} · ${stop.district.name}", style = MaterialTheme.typography.bodySmall)
            }
            Text(formatDistance(distance), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StopDetails(context: Context, stop: DemoStop, distance: String, origin: String, dismiss: () -> Unit) {
    var mapError by rememberSaveable(stop.id) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stop.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Demo ${stop.type.title.lowercase()} · ${stop.district.name}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stop.description)
            Text(stop.landmark, fontWeight = FontWeight.SemiBold)
            Text("$distance from $origin, straight line.", style = MaterialTheme.typography.bodySmall)
            mapError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        Button(onClick = {
            val coordinates = "${stop.latitude},${stop.longitude}"
            val geo = Uri.parse("geo:$coordinates?q=$coordinates")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                mapError = null
            } catch (_: ActivityNotFoundException) {
                mapError = "No map app is available on this device."
            } catch (_: SecurityException) {
                mapError = "This device could not open the map app."
            }
        }) { Text("Open in Maps") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

private tailrec fun Context.hostActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
