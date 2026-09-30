package com.tishan.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val LocalQuestionMode = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/** Keeps question actions reachable while long prompts and explanations scroll. */
@Composable
fun TishanQuestionLayout(
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val setQuestionMode = LocalQuestionMode.current
    DisposableEffect(setQuestionMode) {
        setQuestionMode(true)
        onDispose { setQuestionMode(false) }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) { content() }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), content = actions)
            }
        }
    }
}
