package com.zkwokleung.backchannel.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zkwokleung.backchannel.R
import com.zkwokleung.backchannel.ui.channels.ChannelDetailScreen
import com.zkwokleung.backchannel.ui.channels.ChannelsScreen
import com.zkwokleung.backchannel.ui.player.NowPlayingScreen
import com.zkwokleung.backchannel.ui.player.VideoPlayerScreen
import com.zkwokleung.backchannel.ui.settings.SettingsScreen
import com.zkwokleung.backchannel.ui.watchlists.WatchlistDetailScreen
import com.zkwokleung.backchannel.ui.watchlists.WatchlistsScreen

sealed class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Channels : Tab("channels", R.string.tab_channels, Icons.Filled.Subscriptions)
    data object Watchlists : Tab("watchlists", R.string.tab_watchlists, Icons.Filled.VideoLibrary)
    data object NowPlaying : Tab("now_playing", R.string.tab_now_playing, Icons.Filled.PlayCircle)
    data object Settings : Tab("settings", R.string.tab_settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Channels, Tab.Watchlists, Tab.NowPlaying, Tab.Settings)

/**
 * Switches bottom-nav tabs, saving/restoring each tab's own back stack. Also used when a
 * screen sends the user to another tab (e.g. "play" jumping to Now Playing) — going through
 * [navigate] directly would nest that destination inside the current tab's stack, and the
 * saved-state restore would then bounce the user back to it.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private object Routes {
    const val CHANNEL_DETAIL = "channel/{channelId}"
    const val WATCHLIST_DETAIL = "watchlist/{watchlistId}"
    const val VIDEO = "video"

    fun channelDetail(channelId: String) = "channel/$channelId"
    fun watchlistDetail(watchlistId: Long) = "watchlist/$watchlistId"
}

@UnstableApi
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onVideoScreen = currentDestination?.route == Routes.VIDEO

    Scaffold(
        // This Scaffold has no topBar, so it would otherwise hand the status-bar inset to the
        // NavHost as content padding — and every screen's own Scaffold/TopAppBar applies that
        // inset again, leaving a dead band above each title. Zero here, consumed below, so each
        // inset is applied exactly once.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!onVideoScreen) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Channels.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Tab.Channels.route) {
                ChannelsScreen(
                    onOpenChannel = { navController.navigate(Routes.channelDetail(it)) },
                )
            }
            composable(Tab.Watchlists.route) {
                WatchlistsScreen(
                    onOpenWatchlist = { navController.navigate(Routes.watchlistDetail(it)) },
                )
            }
            composable(Tab.NowPlaying.route) {
                NowPlayingScreen(
                    onOpenVideo = { navController.navigate(Routes.VIDEO) },
                )
            }
            composable(Tab.Settings.route) { SettingsScreen() }

            composable(
                Routes.CHANNEL_DETAIL,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
            ) { entry ->
                val channelId = entry.arguments?.getString("channelId") ?: return@composable
                ChannelDetailScreen(
                    channelYoutubeId = channelId,
                    onBack = { navController.popBackStack() },
                    onOpenNowPlaying = { navController.switchTab(Tab.NowPlaying.route) },
                    onOpenVideo = { navController.navigate(Routes.VIDEO) },
                )
            }
            composable(
                Routes.WATCHLIST_DETAIL,
                arguments = listOf(navArgument("watchlistId") { type = NavType.LongType }),
            ) { entry ->
                val watchlistId = entry.arguments?.getLong("watchlistId") ?: return@composable
                WatchlistDetailScreen(
                    watchlistId = watchlistId,
                    onBack = { navController.popBackStack() },
                    onOpenNowPlaying = { navController.switchTab(Tab.NowPlaying.route) },
                    onOpenVideo = { navController.navigate(Routes.VIDEO) },
                )
            }
            composable(Routes.VIDEO) {
                VideoPlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
