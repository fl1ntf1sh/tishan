package com.tishan.app.feature.practice.exam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

internal enum class ExamAction { Pause, Resume }

/** Decorative icon; each parent button provides a visible, accessible label. */
@Composable
internal fun ExamActionIcon(action: ExamAction) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        when (action) {
            ExamAction.Pause -> {
                drawRect(color, Offset(size.width * .22f, size.height * .15f), Size(size.width * .2f, size.height * .7f))
                drawRect(color, Offset(size.width * .58f, size.height * .15f), Size(size.width * .2f, size.height * .7f))
            }
            ExamAction.Resume -> drawPath(Path().apply {
                moveTo(size.width * .27f, size.height * .12f)
                lineTo(size.width * .86f, size.height * .5f)
                lineTo(size.width * .27f, size.height * .88f)
                close()
            }, color)
        }
    }
}
