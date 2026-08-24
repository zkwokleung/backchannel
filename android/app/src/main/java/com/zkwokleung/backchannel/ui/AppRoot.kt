package com.zkwokleung.backchannel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.remember
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
    data object Settings : Tab("settings", R.string.tab_settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Channels, Tab.Watchlists, Tab.Settings)

/**
 * Opening the player is one gesture, not two animations that happen to overlap: the bottom bar
 * collapses while the player rises into the space it frees. Both sides use these values so they
 * stay in step — the bar's height is what the Scaffold turns into the player's content padding,
 * so a mismatch shows up as the player resizing after it has arrived.
 */
private const val PLAYER_MOTION_MS = 300
private const val TAB_FADE_MS = 140

/** Material's emphasized curve: leaves quickly, settles slowly. */
private val PlayerEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

/**
 * The player sits over whatever you were doing rather than beside it. As a tab it needed a guess
 * about which tab to return to, and it answered wrongly — a tab's back stack pops to the start
 * destination, so leaving it from Watchlists landed on Channels. Pushed, the chevron, the drag
 * and the system Back button are one pop, back to where you actually were.
 */
private fun NavHostController.openPlayer() {
    navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
}

private object Routes {
    const val CHANNEL_DETAIL = "channel/{channelId}"
    const val WATCHLIST_DETAIL = "watchlist/{watchlistId}"
    const val NOW_PLAYING = "now_playing"
    const val VIDEO = "video"

    fun channelDetail(channelId: String) = "channel/$channelId"
    fun watchlistDetail(watchlistId: Long) = "watchlist/$watchlistId"
}

@UnstableApi
@Composable
fun AppRoot(openPlayerRequests: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onVideoScreen = currentDestination?.route == Routes.VIDEO
    val onPlayer = currentDestination?.route == Routes.NOW_PLAYING

    val playerViewModel = sharedPlayerViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    // Route-based hiding belongs to the bar as a whole (below); repeating it here ran a
    // second, competing animation on a subtree that was being removed anyway.
    val showMiniPlayer = playerState.hasItem

    val liveTab = tabs.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }?.route
    // Held a frame behind, so the bar keeps whatever selection it had while it collapses —
    // including no selection at all, which is what the detail screens show.
    val frozenTab = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(onPlayer, liveTab) {
        if (!onPlayer) frozenTab.value = liveTab
    }
    val selectedTab = if (onPlayer) frozenTab.value else liveTab

    // Tapping the media notification lands on the player — the one job the Playing tab was
    // there for. Keyed on the counter so a second tap re-opens it after you have collapsed it.
    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0) navController.openPlayer()
    }

    Scaffold(
        // This Scaffold has no topBar, so it would otherwise hand the status-bar inset to the
        // NavHost as content padding — and every screen's own Scaffold/TopAppBar applies that
        // inset again, leaving a dead band above each title. Zero here, consumed below, so each
        // inset is applied exactly once.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Nothing on the video screen, and nothing under the player either: the full
            // player owns that screen edge to edge. Animated rather than removed outright —
            // dropping it from composition snapped the Scaffold's content padding from ~150dp
            // to zero in one frame, which is what made every screen jolt on the way in.
            AnimatedVisibility(
                visible = !onVideoScreen && !onPlayer,
                enter = expandVertically(tween(PLAYER_MOTION_MS, easing = PlayerEasing)) +
                    fadeIn(tween(PLAYER_MOTION_MS, easing = PlayerEasing)),
                exit = shrinkVertically(tween(PLAYER_MOTION_MS, easing = PlayerEasing)) +
                    fadeOut(tween(PLAYER_MOTION_MS / 2)),
            ) {
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
                                onOpen = { navController.openPlayer() },
                            )
                            Spacer(Modifier.height(Spacing.sm + 2.dp))
                        }
                    }
                    FloatingTabBar(
                        isSelected = { tab -> tab.route == selectedTab },
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
            enterTransition = { fadeIn(tween(TAB_FADE_MS)) },
            exitTransition = {
                // The player rises over whatever you were looking at, so that screen has to
                // outlast a 140ms fade or you see bare background through the gap.
                if (targetState.destination.route == Routes.NOW_PLAYING) {
                    fadeOut(tween(PLAYER_MOTION_MS, easing = PlayerEasing))
                } else {
                    fadeOut(tween(TAB_FADE_MS))
                }
            },
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
            composable(
                Routes.NOW_PLAYING,
                // A crossfade reads as a flicker for a whole-screen surface. Rising from the
                // mini-player's edge matches what the user just tapped, and mirrors the video
                // route's vertical-modal move.
                enterTransition = { playerRise() },
                exitTransition = { playerSettle() },
                popEnterTransition = { playerRise() },
                popExitTransition = { playerSettle() },
                // No size animation: the content area is still growing as the bar collapses,
                // and letting AnimatedContent animate that made the player render small and
                // inflate to full size after it had already arrived.
                sizeTransform = { null },
            ) {
                NowPlayingScreen(
                    onOpenVideo = { navController.navigate(Routes.VIDEO) },
                    onBrowseChannels = { navController.switchTab(Tab.Channels.route) },
                    onCollapse = { navController.popBackStack() },
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
                    onOpenNowPlaying = { navController.openPlayer() },
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
                    onOpenNowPlaying = { navController.openPlayer() },
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
 * The player rising into place, and settling back down. Offset by a fifth of the screen rather
 * than a full height: the mini-player it grows out of already sits near the bottom, so a
 * full-height slide overshoots what the user just touched. The fade finishes early so the
 * surface is opaque for most of the travel.
 */
private fun playerRise() =
    slideInVertically(
        initialOffsetY = { it / 5 },
        animationSpec = tween(PLAYER_MOTION_MS, easing = PlayerEasing),
    ) + fadeIn(tween(PLAYER_MOTION_MS / 2, easing = PlayerEasing))

private fun playerSettle() =
    slideOutVertically(
        targetOffsetY = { it / 5 },
        animationSpec = tween(PLAYER_MOTION_MS, easing = PlayerEasing),
    ) + fadeOut(tween(PLAYER_MOTION_MS, easing = PlayerEasing))

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
