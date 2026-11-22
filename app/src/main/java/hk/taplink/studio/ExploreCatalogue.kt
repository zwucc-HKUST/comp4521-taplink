package hk.taplink.studio

import android.content.Context
import android.location.Location
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

internal enum class ServiceType(val title: String, val plural: String) {
    TOP_UP("Top-up", "Top-up"),
    MERCHANT("Merchant", "Merchants"),
    TRANSPORT("Transport", "Transport")
}

internal data class District(val name: String, val latitude: Double, val longitude: Double)

internal data class DemoStop(
    val id: Int,
    val name: String,
    val type: ServiceType,
    val district: District,
    val latitude: Double,
    val longitude: Double,
    val landmark: String,
    val description: String
)

internal object ExploreCatalogue {
    private val sheungWan = District("Sheung Wan", 22.2865, 114.1515)
    private val central = District("Central", 22.2825, 114.1580)
    private val wanChai = District("Wan Chai", 22.2786, 114.1730)
    private val tsimShaTsui = District("Tsim Sha Tsui", 22.2976, 114.1720)
    private val hungHom = District("Hung Hom", 22.3050, 114.1870)
    val districts = listOf(central, sheungWan, wanChai, tsimShaTsui, hungHom)

    val stops = listOf(
        DemoStop(1, "Market Arcade", ServiceType.TOP_UP, sheungWan, 22.2869, 114.1502,
            "Western Market area", "A sample service counter for the western end of the island walk."),
        DemoStop(2, "Pier Steps", ServiceType.TRANSPORT, central, 22.2876, 114.1608,
            "Central waterfront", "An island-side meeting point for the demo ferry route across the harbour."),
        DemoStop(3, "Tramway Pantry", ServiceType.MERCHANT, wanChai, 22.2777, 114.1743,
            "Johnston Road area", "A fictional neighbourhood grocer on the tram corridor."),
        DemoStop(4, "Clocktower Landing", ServiceType.TRANSPORT, tsimShaTsui, 22.2942, 114.1694,
            "Tsim Sha Tsui promenade", "The Kowloon stop on the demo ferry route, beside the waterfront walk."),
        DemoStop(5, "Observatory Desk", ServiceType.TOP_UP, tsimShaTsui, 22.3019, 114.1736,
            "Observatory Road area", "A sample top-up desk for the streets north of the promenade."),
        DemoStop(6, "Dockyard Bakery", ServiceType.MERCHANT, hungHom, 22.3027, 114.1871,
            "Hung Hom waterfront area", "A fictional bakery near the eastern end of the Kowloon harbour walk.")
    )

    val hongKong: ZoneId = ZoneId.of("Asia/Hong_Kong")
    private const val FILE = "tap_explore_catalogue"
    const val EDITION = "featured_day"
    fun preferences(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun refresh(context: Context, day: LocalDate) {
        check(preferences(context).edit().putLong(EDITION, day.toEpochDay()).commit())
    }

    // All editions use bundled stops. The last published pick survives refresh failures.
    fun featured(context: Context): DemoStop {
        val day = preferences(context).getLong(EDITION, 0L)
        return stops[Math.floorMod(day, stops.size.toLong()).toInt()]
    }
}

internal fun DemoStop.metresFrom(latitude: Double, longitude: Double): Float {
    val result = FloatArray(1)
    Location.distanceBetween(latitude, longitude, this.latitude, this.longitude, result)
    return result[0]
}

internal fun formatDistance(metres: Float): String = when {
    metres < 50f -> "< 50 m"
    metres < 975f -> "≈ ${(metres / 50f).roundToInt() * 50} m"
    metres < 9950f -> String.format(Locale.ENGLISH, "≈ %.1f km", metres / 1000f)
    else -> "≈ ${(metres / 1000f).roundToInt()} km"
}
