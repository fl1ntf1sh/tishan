package com.tishan.app.core.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.dp

@Composable
internal fun TishanNavigationIcon(destination: TishanDestination) {
    val color = LocalContentColor.current

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.9.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (destination) {
            TishanDestination.Practice -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.18f, h * 0.08f),
                    size = Size(w * 0.64f, h * 0.84f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.32f, h * 0.31f), Offset(w * 0.68f, h * 0.31f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.49f), Offset(w * 0.61f, h * 0.49f), strokeWidth, StrokeCap.Round)
                val check = Path().apply {
                    moveTo(w * 0.31f, h * 0.70f)
                    lineTo(w * 0.43f, h * 0.80f)
                    lineTo(w * 0.68f, h * 0.61f)
                }
                drawPath(check, color, style = stroke)
            }

            TishanDestination.Review -> {
                val unit = w / 24f
                val arrows = Path().apply {
                    moveTo(19f * unit, 8f * unit)
                    cubicTo(17f * unit, 3.8f * unit, 11f * unit, 2f * unit, 6f * unit, 6f * unit)
                    lineTo(3f * unit, 9f * unit)
                    moveTo(3f * unit, 4f * unit)
                    lineTo(3f * unit, 9f * unit)
                    lineTo(8f * unit, 9f * unit)
                    moveTo(5f * unit, 16f * unit)
                    cubicTo(7f * unit, 20.2f * unit, 13f * unit, 22f * unit, 18f * unit, 18f * unit)
                    lineTo(21f * unit, 15f * unit)
                    moveTo(21f * unit, 20f * unit)
                    lineTo(21f * unit, 15f * unit)
                    lineTo(16f * unit, 15f * unit)
                }
                drawPath(arrows, color, style = stroke)
            }

            TishanDestination.Exam -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.18f, h * 0.14f),
                    size = Size(w * 0.64f, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.09f),
                    style = stroke,
                )
                drawCircle(color, w * 0.18f, Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.37f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.61f, h * 0.56f), strokeWidth, StrokeCap.Round)
            }

            TishanDestination.Manage -> {
                drawRoundRect(color, Offset(w * 0.14f, h * 0.22f), Size(w * 0.72f, h * 0.60f), CornerRadius(w * 0.08f), style = stroke)
                drawLine(color, Offset(w * 0.14f, h * 0.40f), Offset(w * 0.86f, h * 0.40f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.13f), Offset(w * 0.64f, h * 0.13f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(w * 0.27f, h * 0.58f), Offset(w * 0.73f, h * 0.58f), strokeWidth, StrokeCap.Round)
            }

            TishanDestination.Settings -> {
                val rows = listOf(0.28f to 0.36f, 0.50f to 0.66f, 0.72f to 0.45f)
                rows.forEach { (yRatio, knobRatio) ->
                    val y = h * yRatio
                    drawLine(color, Offset(w * 0.16f, y), Offset(w * 0.84f, y), strokeWidth, StrokeCap.Round)
                    drawCircle(color, w * 0.085f, Offset(w * knobRatio, y), style = stroke)
                }
            }
        }
    }
}
