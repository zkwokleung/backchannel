package com.zkwokleung.backchannel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zkwokleung.backchannel.ui.player.MiniPlayer
import com.zkwokleung.backchannel.ui.player.NowPlayingScreen
import com.zkwokleung.backchannel.ui.player.sharedPlayerViewModel
import com.zkwokleung.backchannel.ui.player.VideoPlayerScreen
import com.zkwokleung.backchannel.ui.settings.SettingsScreen
import com.zkwokleung.backchannel.ui.theme.Spacing
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
    val onPlayingTab = currentDestination?.hierarchy?.any { it.route == Tab.NowPlaying.route } == true

    val playerViewModel = sharedPlayerViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val showMiniPlayer = playerState.hasItem && !onVideoScreen && !onPlayingTab

    // The Playing tab is a full-screen player with no nav; its collapse chevron returns to
    // whichever browsing tab the user came from rather than a hardcoded one.
    var lastBrowseTab by rememberSaveable { mutableStateOf(Tab.Channels.route) }
    LaunchedEffect(currentDestination) {
        val tabRoute = tabs.firstOrNull { tab ->
            currentDestination?.hierarchy?.any { it.route == tab.route } == true
        }?.route
        if (tabRoute != null && tabRoute != Tab.NowPlaying.route) lastBrowseTab = tabRoute
    }

    Scaffold(
        // This Scaffold has no topBar, so it would otherwise hand the status-bar inset to the
        // NavHost as content padding — and every screen's own Scaffold/TopAppBar applies that
        // inset again, leaving a dead band above each title. Zero here, consumed below, so each
        // inset is applied exactly once.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Nothing on the video screen, and nothing on the Playing tab either: the full
            // player owns that screen edge to edge.
            if (!onVideoScreen && !onPlayingTab) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                ) {
                    AnimatedVisibility(
                        visible = showMiniPlayer,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            MiniPlayer(
                                state = playerState,
                                onPlayPause = playerViewModel::playPause,
                                onSkipForward = { playerViewModel.seekBy(30_000) },
                                onOpen = { navController.switchTab(Tab.NowPlaying.route) },
                            )
                            Spacer(Modifier.height(Spacing.sm + 2.dp))
                        }
                    }
                    FloatingTabBar(
                        isSelected = { tab ->
                            currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        },
                        onSelect = { navController.switchTab(it.route) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Channels.route,
            // Tabs crossfade: a horizontal slide between siblings reads as broken, because tab
            // order carries no direction and the bottom bar stays put. Detail routes below
            // override this with a slide, which does have a direction.
            enterTransition = { fadeIn(tween(140)) },
            exitTransition = { fadeOut(tween(140)) },
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
                    onBrowseChannels = { navController.switchTab(Tab.Channels.route) },
                    onCollapse = { navController.switchTab(lastBrowseTab) },
                )
            }
            composable(Tab.Settings.route) { SettingsScreen() }

            composable(
                Routes.CHANNEL_DETAIL,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(260)) +
                        fadeIn(tween(180))
                },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it / 6 }, animationSpec = tween(220)) +
                        fadeOut(tween(160))
                },
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
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(260)) +
                        fadeIn(tween(180))
                },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it / 6 }, animationSpec = tween(220)) +
                        fadeOut(tween(160))
                },
            ) { entry ->
                val watchlistId = entry.arguments?.getLong("watchlistId") ?: return@composable
                WatchlistDetailScreen(
                    watchlistId = watchlistId,
                    onBack = { navController.popBackStack() },
                    onOpenNowPlaying = { navController.switchTab(Tab.NowPlaying.route) },
                    onOpenVideo = { navController.navigate(Routes.VIDEO) },
                )
            }
            composable(
                Routes.VIDEO,
                enterTransition = {
                    slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(240)) +
                        fadeIn(tween(160))
                },
                popExitTransition = {
                    slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = tween(220)) +
                        fadeOut(tween(160))
                },
            ) {
                VideoPlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * The floating pill tab bar. The active tab is an icon-and-label pill on the violet container;
 * the rest are icon-only targets, so the bar stays narrow enough to float with margins.
 */
@Composable
private fun FloatingTabBar(
    isSelected: (Tab) -> Boolean,
    onSelect: (Tab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            tabs.forEach { tab ->
                val label = stringResource(tab.labelRes)
                val selected = isSelected(tab)
                // Selecting the tab you are already on is not a no-op: switchTab pops that tab
                // back to its start destination, which is how you get from a channel's uploads
                // back to the channel list.
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        )
                        .padding(horizontal = if (selected) Spacing.lg else Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = if (selected) null else label,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    if (selected) {
                        Spacer(Modifier.size(Spacing.sm))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}
