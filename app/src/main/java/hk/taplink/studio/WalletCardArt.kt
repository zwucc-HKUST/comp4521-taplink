package hk.taplink.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

internal enum class WalletGlyph { Cards, Pay, TopUp, Reveal, Conceal, Ticket, Forward }

@Composable
internal fun WalletIcon(glyph: WalletGlyph, tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        withTransform({
            translate((size.width - 24f * unit) / 2, (size.height - 24f * unit) / 2)
            scale(unit, unit, Offset.Zero)
        }) {
            fun line(x: Float, y: Float, endX: Float, endY: Float) {
                drawLine(tint, Offset(x, y), Offset(endX, endY), 1.8f, StrokeCap.Round)
            }
            val outline = Stroke(width = 1.7f)
            when (glyph) {
                WalletGlyph.Cards -> {
                    drawRoundRect(tint, Offset(5f, 8f), Size(16f, 12f), CornerRadius(2f), style = outline)
                    val back = Path().apply {
                        moveTo(2.5f, 15f)
                        lineTo(2.5f, 6f)
                        quadraticTo(2.5f, 4f, 4.5f, 4f)
                        lineTo(17f, 4f)
                    }
                    drawPath(back, tint, style = outline)
                    line(8f, 15.5f, 12f, 15.5f)
                }
                WalletGlyph.Pay -> {
                    drawRoundRect(tint, Offset(2f, 9f), Size(15f, 11f), CornerRadius(2f), style = outline)
                    line(2.8f, 12.5f, 16.2f, 12.5f)
                    line(14f, 7f, 19f, 2f)
                    line(15.5f, 2f, 20f, 2f)
                    line(20f, 2f, 20f, 6.5f)
                }
                WalletGlyph.TopUp -> {
                    drawRoundRect(tint, Offset(2f, 7f), Size(19f, 14f), CornerRadius(2f), style = outline)
                    line(5f, 4f, 16f, 4f)
                    line(8f, 14f, 15f, 14f)
                    line(11.5f, 10.5f, 11.5f, 17.5f)
                }
                WalletGlyph.Reveal, WalletGlyph.Conceal -> {
                    val eye = Path().apply {
                        moveTo(1.5f, 12f)
                        quadraticTo(12f, -1f, 22.5f, 12f)
                        quadraticTo(12f, 25f, 1.5f, 12f)
                        close()
                    }
                    drawPath(eye, tint, style = outline)
                    drawCircle(tint, 2.6f, Offset(12f, 12f), style = outline)
                    if (glyph == WalletGlyph.Reveal) line(3f, 3f, 21f, 21f)
                }
                WalletGlyph.Ticket -> {
                    val ticket = Path().apply {
                        moveTo(2f, 5f)
                        lineTo(22f, 5f)
                        lineTo(22f, 9f)
                        cubicTo(17f, 9f, 17f, 15f, 22f, 15f)
                        lineTo(22f, 19f)
                        lineTo(2f, 19f)
                        lineTo(2f, 15f)
                        cubicTo(7f, 15f, 7f, 9f, 2f, 9f)
                        close()
                    }
                    drawPath(ticket, tint, style = outline)
                    line(14f, 7f, 14f, 9f)
                    line(14f, 12f, 14f, 13f)
                    line(14f, 16f, 14f, 17f)
                }
                WalletGlyph.Forward -> {
                    line(9f, 6f, 15f, 12f)
                    line(15f, 12f, 9f, 18f)
                }
            }
        }
    }
}

/** A two-deck harbour ferry drawn in the lower right of the stored-value card. */
@Composable
internal fun WalletHarbourArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = minOf(size.width * 0.47f / 192f, size.height * 0.60f / 130f)
        val mist = Color(0xFF419486)
        val water = Color(0xFF245F5C)
        val cream = Color(0xFFF3DDB3)
        withTransform({
            translate(size.width - 192f * unit, size.height - 130f * unit)
            scale(unit, unit, Offset.Zero)
        }) {
            fun silhouette(color: Color, vararg points: Pair<Int, Int>) {
                val shape = Path().apply {
                    moveTo(points.first().first.toFloat(), points.first().second.toFloat())
                    for ((x, y) in points.drop(1)) lineTo(x.toFloat(), y.toFloat())
                    close()
                }
                drawPath(shape, color)
            }
            fun block(color: Color, x: Int, y: Int, w: Int, h: Int) {
                drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))
            }
            drawCircle(Color(0xFFF2AA73), 15f, Offset(165f, 26f))
            silhouette(water, 0 to 88, 42 to 70, 63 to 71, 95 to 43, 112 to 50,
                139 to 27, 164 to 51, 180 to 43, 192 to 52, 192 to 104, 0 to 104)
            block(mist, 117, 51, 8, 29)
            block(mist, 127, 59, 11, 21)
            block(mist, 151, 47, 12, 33)
            block(mist, 154, 42, 5, 6)
            block(mist, 174, 57, 18, 23)
            block(water, 11, 81, 181, 2)
            block(mist, 32, 106, 116, 2)
            block(mist, 76, 115, 67, 2)
            block(mist, 150, 111, 18, 2)
            block(water, 54, 121, 71, 1)

            silhouette(cream, 26 to 88, 146 to 88, 132 to 100, 48 to 100, 34 to 95)
            silhouette(Color(0xFFED915D), 34 to 94, 139 to 94, 132 to 100, 48 to 100)
            block(cream, 42, 75, 88, 13)
            block(cream, 52, 63, 66, 11)
            block(Color(0xFFED915D), 48, 60, 75, 3)
            block(water, 38, 73, 97, 3)
            block(cream, 74, 53, 5, 7)
            block(cream, 74, 51, 13, 2)
            for (i in 0 until 7) block(water, 55 + i * 9, 65, 6, 6)
            for (i in 0 until 9) block(water, 45 + i * 9, 78, 6, 7)
        }
    }
}
