package com.zkwokleung.backchannel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zkwokleung.backchannel.R

sealed class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Channels : Tab("channels", R.string.tab_channels, Icons.Filled.Subscriptions)
    data object Watchlists : Tab("watchlists", R.string.tab_watchlists, Icons.Filled.VideoLibrary)
    data object NowPlaying : Tab("now_playing", R.string.tab_now_playing, Icons.Filled.PlayCircle)
    data object Settings : Tab("settings", R.string.tab_settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Channels, Tab.Watchlists, Tab.NowPlaying, Tab.Settings)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Channels.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.Channels.route) { PlaceholderScreen("Channels") }
            composable(Tab.Watchlists.route) { PlaceholderScreen("Watchlists") }
            composable(Tab.NowPlaying.route) { PlaceholderScreen("Now Playing") }
            composable(Tab.Settings.route) { PlaceholderScreen("Settings") }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
