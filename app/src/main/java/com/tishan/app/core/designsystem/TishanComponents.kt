package com.tishan.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tishan.app.core.designsystem.theme.TishanSemanticColors

data class TishanSegment(
    val key: String,
    val label: String,
)

@Composable
fun TishanCheckOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp),
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun TishanSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearDescription: String = "清空搜索",
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = { SearchGlyph() },
        trailingIcon = if (value.isNotEmpty()) {
            {
                ClearSearchButton(
                    onClick = { onValueChange("") },
                    contentDescription = clearDescription,
                )
            }
        } else null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun SearchGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.29f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.43f),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.64f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ClearSearchButton(
    onClick: () -> Unit,
    contentDescription: String,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = color,
    ) {
        Canvas(modifier = Modifier.padding(12.dp)) {
            val strokeWidth = 1.8.dp.toPx()
            drawLine(
                color,
                androidx.compose.ui.geometry.Offset.Zero,
                androidx.compose.ui.geometry.Offset(size.width, size.height),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                color,
                androidx.compose.ui.geometry.Offset(size.width, 0f),
                androidx.compose.ui.geometry.Offset(0f, size.height),
                strokeWidth,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TishanChoiceChips(
    items: List<TishanSegment>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val isSelected = item.key == selectedKey
            val shape = RoundedCornerShape(12.dp)
            Surface(
                onClick = { onSelected(item.key) },
                modifier = Modifier.semantics { selected = isSelected },
                shape = shape,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp).heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun TishanButtonChoices(
    items: List<TishanSegment>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val isSelected = item.key == selectedKey
            val itemModifier = Modifier
                .weight(1f)
                .semantics { selected = isSelected }
            val content: @Composable () -> Unit = {
                Text(
                    text = item.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (isSelected) {
                Button(
                    onClick = { onSelected(item.key) },
                    modifier = itemModifier,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    content = { content() },
                )
            } else {
                OutlinedButton(
                    onClick = { onSelected(item.key) },
                    modifier = itemModifier,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    content = { content() },
                )
            }
        }
    }
}

@Composable
fun TishanQuestionChoice(
    label: String,
    text: String,
    selected: Boolean,
    enabled: Boolean,
    singleSelection: Boolean,
    modifier: Modifier = Modifier,
    correct: Boolean = false,
    incorrect: Boolean = false,
    onClick: () -> Unit,
) {
    val semantic = TishanSemanticColors.current
    val shape = RoundedCornerShape(16.dp)
    val container = when {
        correct -> semantic.successContainer
        incorrect -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val outline = when {
        correct -> semantic.onSuccessContainer
        incorrect -> MaterialTheme.colorScheme.onErrorContainer
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val foreground = when {
        correct -> semantic.onSuccessContainer
        incorrect -> MaterialTheme.colorScheme.onErrorContainer
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, outline, shape)
            .then(if (singleSelection) Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                  else Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() }))
            .semantics { contentDescription = "$label $text" }
            .heightIn(min = 56.dp)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(27.dp).border(1.dp, outline, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = foreground, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text + when { correct -> " ✓"; incorrect -> " ✕"; else -> "" },
            modifier = Modifier.weight(1f),
            color = foreground,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
        )
    }
}

@Composable
fun TishanSegmentedControl(
    items: List<TishanSegment>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEach { item ->
            SegmentItem(
                item = item,
                selected = item.key == selectedKey,
                onClick = { onSelected(item.key) },
            )
        }
    }
}

@Composable
private fun RowScope.SegmentItem(
    item: TishanSegment,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(item.label, color = foreground, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TishanProgressBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val safeTotal = total.coerceAtLeast(1)
    val safeCurrent = current.coerceIn(0, safeTotal)
    val progress = safeCurrent.toFloat() / safeTotal
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLabel) {
            Text(
                "$safeCurrent / $total · ${(progress * 100).toInt()}%",
                modifier = Modifier.align(Alignment.End),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun TishanInfoCard(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = contentColor)
            content()
        }
    }
}

@Composable
fun TishanOutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick == null) {
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(20.dp),
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) { content() }
    } else {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = RoundedCornerShape(20.dp),
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) { content() }
    }
}

@Composable
fun TishanEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(modifier = Modifier.size(58.dp)) {
            val muted = Color(0x66566B82)
            val near = Path().apply {
                moveTo(size.width * 0.05f, size.height * 0.78f)
                lineTo(size.width * 0.42f, size.height * 0.25f)
                lineTo(size.width * 0.62f, size.height * 0.52f)
                lineTo(size.width * 0.76f, size.height * 0.36f)
                lineTo(size.width * 0.96f, size.height * 0.78f)
                close()
            }
            drawPath(near, muted)
            drawCircle(Color(0x99B58A3C), radius = size.minDimension * 0.07f, center = center.copy(y = size.height * 0.2f))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        action?.invoke()
    }
}

@Composable
fun TishanSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    )
}

@Composable
fun TishanFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(top = 20.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 20.sp),
        textAlign = TextAlign.Center,
    )
}

@Composable
fun TishanResourceTile(isExam: Boolean, modifier: Modifier = Modifier) {
    val background = if (isExam) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val foreground = if (isExam) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier.size(width = 44.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
            if (isExam) {
                drawCircle(foreground, radius = size.width * 0.36f, center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.58f), style = stroke)
                drawLine(foreground, androidx.compose.ui.geometry.Offset(size.width * 0.5f, 0f), androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.2f), stroke.width, StrokeCap.Round)
                drawLine(foreground, androidx.compose.ui.geometry.Offset(size.width * 0.35f, 0f), androidx.compose.ui.geometry.Offset(size.width * 0.65f, 0f), stroke.width, StrokeCap.Round)
                drawLine(foreground, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.58f), androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.38f), stroke.width, StrokeCap.Round)
            } else {
                drawRoundRect(foreground, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.12f), size = androidx.compose.ui.geometry.Size(size.width * 0.65f, size.height * 0.78f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = stroke)
                drawLine(foreground, androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.67f, size.height * 0.38f), stroke.width, StrokeCap.Round)
                drawLine(foreground, androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.56f), androidx.compose.ui.geometry.Offset(size.width * 0.59f, size.height * 0.56f), stroke.width, StrokeCap.Round)
            }
        }
    }
}
