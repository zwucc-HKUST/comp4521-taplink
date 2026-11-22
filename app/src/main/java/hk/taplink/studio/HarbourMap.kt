package hk.taplink.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HarbourMap(stops: List<DemoStop>, onStop: (DemoStop) -> Unit) {
    val water = Color(0xFFBDDAD4)
    val waterLine = Color(0xFF96C1BA)
    val land = Color(0xFFEFE8D7)
    val block = Color(0xFFDECEB6)
    val park = Color(0xFFBECCA9)
    val ink = Color(0xFF254E4B)
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(242.dp).clip(shape)
            .background(water).border(1.dp, waterLine, shape)
    ) {
        val chartWidth = maxWidth
        val chartHeight = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun shore(vertices: List<Pair<Float, Float>>) = Path().apply {
                vertices.forEachIndexed { idx, (x, y) ->
                    if (idx == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h)
                }
                close()
            }
            val north = shore(listOf(0f to 0f, 1f to 0f, 1f to .26f, .92f to .28f,
                .84f to .22f, .75f to .23f, .68f to .29f, .64f to .38f, .55f to .40f,
                .51f to .445f, .46f to .425f, .43f to .34f, .35f to .27f,
                .27f to .26f, .22f to .19f, .12f to .22f, 0f to .17f))
            val south = shore(listOf(0f to .60f, .11f to .56f, .17f to .57f, .24f to .54f,
                .33f to .53f, .39f to .56f, .47f to .62f, .55f to .615f, .61f to .63f,
                .69f to .64f, .75f to .68f, .83f to .67f, .91f to .62f, 1f to .60f,
                1f to 1f, 0f to 1f))
            for (row in 0 until 12) {
                for (column in 0 until 9) {
                    val x = (column + if (row % 2 == 0) .12f else .61f) * w / 9f
                    val y = (row + .5f) * h / 12f
                    drawLine(waterLine, Offset(x, y), Offset(x + w * .022f, y), 1.dp.toPx())
                }
            }
            for (coast in listOf(north, south)) {
                drawPath(coast, land)
                clipPath(coast) {
                    for (row in 0 until 12) {
                        for (column in -1 until 13) {
                            val x = (column + if (row % 2 == 0) .12f else .48f) * w / 12f
                            val y = row * h / 11f
                            drawRoundRect(
                                if ((row + column + 2) % 8 == 0) park else block,
                                topLeft = Offset(x, y),
                                size = Size(w * .063f, h * .062f), cornerRadius = CornerRadius(3f)
                            )
                        }
                    }
                    val road = Path().apply {
                        moveTo(-.1f * w, .87f * h)
                        cubicTo(.22f * w, .47f * h, .43f * w, .75f * h, 1.1f * w, .79f * h)
                    }
                    drawPath(road, Color(0xFFFAF5E8), style = Stroke(4.dp.toPx()))
                    drawLine(Color(0xFFFAF5E8), Offset(.46f * w, .46f * h),
                        Offset(.62f * w, -.04f * h), 4.dp.toPx())
                }
                drawPath(coast, Color(0xFFA9B3A4), style = Stroke(1.dp.toPx()))
            }
            val crossing = Path().apply {
                moveTo(.336f * w, .563f * h)
                cubicTo(.39f * w, .48f * h, .45f * w, .50f * h, .508f * w, .420f * h)
            }
            drawPath(crossing, ink.copy(alpha = .58f), style = Stroke(
                width = 1.4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            ))
        }
        Text("KOWLOON", Modifier.offset(chartWidth * .04f, chartHeight * .06f),
            color = ink, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Text("Victoria Harbour", Modifier.offset(chartWidth * .58f, chartHeight * .44f),
            color = ink, fontSize = 12.sp)
        Text("HONG KONG ISLAND", Modifier.align(Alignment.BottomStart).padding(10.dp),
            color = ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("↑ N", Modifier.align(Alignment.TopEnd).padding(10.dp),
            color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        stops.forEach { stop ->
            val x = ((stop.longitude - 114.144) / .050).toFloat()
            val y = ((22.309 - stop.latitude) / .037).toFloat()
            val pinColor = when (stop.type) {
                ServiceType.TOP_UP -> Color(0xFF125956)
                ServiceType.MERCHANT -> Color(0xFF97441F)
                ServiceType.TRANSPORT -> Color(0xFF3C4D70)
            }
            IconButton(
                onClick = { onStop(stop) },
                modifier = Modifier.offset(chartWidth * x - 24.dp, chartHeight * y - 24.dp)
                    .size(48.dp).semantics { contentDescription = "${stop.name}, ${stop.type.title}, details" }
            ) {
                Box(Modifier.size(29.dp).background(pinColor, CircleShape)
                    .border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text(stop.id.toString(), Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium, color = Color.White,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
