package com.tishan.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun TishanActionDialog(
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit = onSecondary,
    danger: Boolean = false,
    primaryOnLeft: Boolean = false,
    primaryEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).widthIn(max = 370.dp)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (primaryOnLeft) {
                        DialogPrimaryButton(primaryLabel, onPrimary, danger, primaryEnabled, Modifier.weight(1f))
                        OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(secondaryLabel) }
                    } else {
                        OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(secondaryLabel) }
                        DialogPrimaryButton(primaryLabel, onPrimary, danger, primaryEnabled, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogPrimaryButton(
    label: String,
    onClick: () -> Unit,
    danger: Boolean,
    enabled: Boolean,
    modifier: Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
    ) { Text(label) }
}
