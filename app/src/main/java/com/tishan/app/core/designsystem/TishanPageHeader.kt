package com.tishan.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TishanPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String = "返回",
    subtitle: String? = null,
    titleMeta: String? = null,
    backOnRight: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!backOnRight) onBack?.let {
            TishanBackButton(onClick = it, contentDescription = backDescription)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null && !backOnRight) 8.dp else 0.dp, end = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            (subtitle ?: titleMeta)?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
        if (backOnRight) onBack?.let {
            TishanBackButton(onClick = it, contentDescription = backDescription)
        }
    }
}

@Composable
fun TishanBackButton(
    onClick: () -> Unit,
    contentDescription: String = "返回",
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = color,
        tonalElevation = 0.dp,
    ) {
        Canvas(modifier = Modifier.padding(13.dp)) {
            val strokeWidth = 2.dp.toPx()
            val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val arrow = Path().apply {
                moveTo(size.width * 0.62f, size.height * 0.18f)
                lineTo(size.width * 0.30f, size.height * 0.50f)
                lineTo(size.width * 0.62f, size.height * 0.82f)
            }
            drawPath(arrow, color, style = stroke)
            drawLine(
                color,
                Offset(size.width * 0.31f, size.height * 0.50f),
                Offset(size.width * 0.82f, size.height * 0.50f),
                strokeWidth,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
fun TishanMoreButton(
    onClick: () -> Unit,
    contentDescription: String = "更多操作",
    selected: Boolean = false,
) {
    val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = color,
    ) {
        Canvas(modifier = Modifier.padding(12.dp)) {
            val radius = 1.7.dp.toPx()
            listOf(0.25f, 0.5f, 0.75f).forEach { ratio ->
                drawCircle(color, radius, Offset(size.width / 2f, size.height * ratio))
            }
        }
    }
}

@Composable
fun TishanSearchButton(
    onClick: () -> Unit,
    contentDescription: String = "搜索",
    selected: Boolean = false,
) {
    val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = color,
    ) {
        Canvas(modifier = Modifier.padding(11.dp)) {
            val strokeWidth = 2.dp.toPx()
            drawCircle(
                color = color,
                radius = size.minDimension * 0.30f,
                center = Offset(size.width * 0.43f, size.height * 0.43f),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.65f, size.height * 0.65f),
                end = Offset(size.width * 0.86f, size.height * 0.86f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
