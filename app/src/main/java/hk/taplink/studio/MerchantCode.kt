package hk.taplink.studio

data class Merchant(val code: String, val name: String, val category: String)
val merchants = listOf(
    Merchant("PIER", "Star Pier Coffee", "Food"),
    Merchant("FERRY", "Harbour Ferry", "Transport"),
    Merchant("BOOKS", "Paper Lantern Books", "Shopping")
)

fun parseMerchantCode(text: String): Pair<Merchant, String> {
    val fields = text.split('|')
    require(fields.size == 3 && fields[0] == "TL1") { "Choose a TapLink merchant QR." }
    val merchant = merchants.firstOrNull { it.code == fields[1] }
        ?: throw IllegalArgumentException("Unknown demo merchant.")
    parseCents(fields[2])
    return merchant to fields[2].trim()
}
