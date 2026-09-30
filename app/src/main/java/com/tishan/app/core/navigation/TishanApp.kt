package com.tishan.app.core.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.tishan.app.core.designsystem.LocalQuestionMode
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.tishan.app.feature.manage.ManageRoute
import com.tishan.app.feature.manage.SettingsRoute
import com.tishan.app.feature.practice.PracticeRoute
import com.tishan.app.feature.practice.exam.ExamRoute
import com.tishan.app.feature.review.ReviewRoute

@Composable
fun TishanApp() {
    val navController = rememberNavController()
    val destinations = TishanDestination.entries
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val navigationItemHeight = (39f + 18f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp

    fun navigateTo(destination: TishanDestination) {
        if (currentRoute == destination.route) return
        if (destination == TishanDestination.Practice) {
            if (!navController.popBackStack(TishanDestination.Practice.route, inclusive = false)) {
                navController.navigate(TishanDestination.Practice.route) { launchSingleTop = true }
            }
        } else {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    var questionMode by remember { mutableStateOf(false) }
    val setQuestionMode: (Boolean) -> Unit = remember { { questionMode = it } }
    CompositionLocalProvider(LocalQuestionMode provides setQuestionMode) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            if (!questionMode) Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.align(Alignment.Center).widthIn(max = 390.dp).fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        destinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            val itemColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = navigationItemHeight)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .selectable(selected = selected, role = Role.Tab, onClick = { navigateTo(destination) })
                                    .semantics { contentDescription = "${destination.label}导航" }
                                    .padding(vertical = 7.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                            ) {
                                CompositionLocalProvider(LocalContentColor provides itemColor) { TishanNavigationIcon(destination) }
                                Text(destination.label, color = itemColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = TishanDestination.Practice.route,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 390.dp).fillMaxWidth().fillMaxHeight(),
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                composable(TishanDestination.Practice.route) { PracticeRoute(onImport = { navigateTo(TishanDestination.Manage) }) }
                composable(TishanDestination.Exam.route) { ExamRoute() }
                composable(TishanDestination.Review.route) { ReviewRoute() }
                composable(TishanDestination.Manage.route) { ManageRoute() }
                composable(TishanDestination.Settings.route) { SettingsRoute() }
            }
        }
    }
}
}
