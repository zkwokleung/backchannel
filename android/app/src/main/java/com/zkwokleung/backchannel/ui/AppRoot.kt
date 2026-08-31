package com.zkwokleung.backchannel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
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
import com.zkwokleung.backchannel.ui.player.MiniPlayer
import com.zkwokleung.backchannel.ui.player.NowPlayingScreen
import com.zkwokleung.backchannel.ui.downloads.DownloadsScreen
import com.zkwokleung.backchannel.ui.player.sharedPlayerViewModel
import com.zkwokleung.backchannel.ui.player.VideoPlayerScreen
import com.zkwokleung.backchannel.ui.settings.SettingsScreen
import com.zkwokleung.backchannel.ui.theme.Spacing
import com.zkwokleung.backchannel.ui.watchlists.WatchlistDetailScreen
import com.zkwokleung.backchannel.ui.watchlists.WatchlistsScreen
import kotlinx.coroutines.launch

sealed class Tab(val labelRes: Int, val icon: ImageVector) {
    data object Channels : Tab(R.string.tab_channels, Icons.Filled.Subscriptions)
    data object Watchlists : Tab(R.string.tab_watchlists, Icons.Filled.VideoLibrary)
    data object Settings : Tab(R.string.tab_settings, Icons.Filled.Settings)
}

/** Page order of the home pager; a tab's index here is its page. */
private val tabs = listOf(Tab.Channels, Tab.Watchlists, Tab.Settings)

/**
 * Opening the player is one gesture, not two animations that happen to overlap: the bottom bar
 * collapses while the player rises into the space it frees. Both sides use these values so they
 * stay in step — the bar's height is what the Scaffold turns into the player's content padding,
 * so a mismatch shows up as the player resizing after it has arrived.
 */
private const val PLAYER_MOTION_MS = 300
private const val ROUTE_FADE_MS = 140

/**
 * The selection moving between tabs. Slower than the route fade on purpose: the pill is a small
 * thing travelling a short distance, and matching the 140ms made it look like it had simply
 * teleported.
 */
private const val TAB_MOTION_MS = 220

/** Press feedback: quick enough to feel immediate, slow enough to survive a fast tap. */
private const val TAB_PRESS_IN_MS = 80
private const val TAB_PRESS_OUT_MS = 220

/** Material's emphasized curve: leaves quickly, settles slowly. */
private val PlayerEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The player sits over whatever you were doing rather than beside it. As a tab it needed a guess
 * about which tab to return to, and it answered wrongly — a tab's back stack pops to the start
 * destination, so leaving it from Watchlists landed on Channels. Pushed, the chevron, the drag
 * and the system Back button are one pop, back to where you actually were.
 */
private fun NavHostController.openPlayer() {
    navigate(Routes.NOW_PLAYING) {
        launchSingleTop = true
        // launchSingleTop alone only dedupes when the player is already the top entry. From the
        // video screen the stack is [.., now_playing, video], so a notification tap would stack
        // a second player and collapsing would drop the user back onto the video.
        popUpTo(Routes.NOW_PLAYING) { inclusive = true }
    }
}

private object Routes {
    const val HOME = "home"
    const val CHANNEL_DETAIL = "channel/{channelId}"
    const val WATCHLIST_DETAIL = "watchlist/{watchlistId}"
    const val NOW_PLAYING = "now_playing"
    const val VIDEO = "video"
    const val DOWNLOADS = "downloads"

    fun channelDetail(channelId: String) = "channel/$channelId"
    fun watchlistDetail(watchlistId: Long) = "watchlist/$watchlistId"
}

/** Pushed sub-screens keep their tab highlighted; add any new sub-screen here. */
private val subscreenParents = mapOf(
    Routes.CHANNEL_DETAIL to Tab.Channels,
    Routes.WATCHLIST_DETAIL to Tab.Watchlists,
    Routes.DOWNLOADS to Tab.Settings,
)

@UnstableApi
@Composable
fun AppRoot(openPlayerRequests: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onVideoScreen = currentDestination?.route == Routes.VIDEO
    val onPlayer = currentDestination?.route == Routes.NOW_PLAYING
    val onHome = currentDestination?.route == Routes.HOME

    // The three tabs are pages of one pager on the home route, so the page is the selection.
    // Hoisted here because the bar and switchTab both drive it, and so it outlives the home
    // entry's composition while a sub-screen or the player is on top.
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    /**
     * Shows a tab's root. From a pushed route the pager is not on screen, so the page changes
     * instantly under the pop transition — which is also how tapping a sub-screen's own tab gets
     * back to its list. On home it scrolls, the same motion a swipe produces.
     */
    fun switchTab(tab: Tab) {
        val page = tabs.indexOf(tab)
        if (onHome) {
            scope.launch { pagerState.animateScrollToPage(page) }
        } else {
            navController.popBackStack(Routes.HOME, inclusive = false)
            scope.launch { pagerState.scrollToPage(page) }
        }
    }

    val playerViewModel = sharedPlayerViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    // Route-based hiding belongs to the bar as a whole (below); repeating it here ran a
    // second, competing animation on a subtree that was being removed anyway.
    val showMiniPlayer = playerState.hasItem

    val liveTab =
        if (onHome) tabs[pagerState.currentPage] else subscreenParents[currentDestination?.route]
    // Held a frame behind, so the bar keeps whatever selection it had while it collapses.
    val frozenTab = remember { mutableStateOf<Tab?>(null) }
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
                        isSelected = { tab -> tab == selectedTab },
                        onSelect = ::switchTab,
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // Tabs are pages inside home and move with the finger, so nothing here animates a
            // tab switch. This fade is home going under a pushed route and coming back; the
            // detail routes below override it with a slide of their own.
            enterTransition = { fadeIn(tween(ROUTE_FADE_MS)) },
            exitTransition = {
                // The player rises over whatever you were looking at, so that screen has to
                // outlast a 140ms fade or you see bare background through the gap.
                if (targetState.destination.route == Routes.NOW_PLAYING) {
                    fadeOut(tween(PLAYER_MOTION_MS, easing = PlayerEasing))
                } else {
                    fadeOut(tween(ROUTE_FADE_MS))
                }
            },
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomePager(
                    pagerState = pagerState,
                    onOpenChannel = { navController.navigate(Routes.channelDetail(it)) },
                    onOpenWatchlist = { navController.navigate(Routes.watchlistDetail(it)) },
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
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
                    onBrowseChannels = { switchTab(Tab.Channels) },
                    // Addressed to the player rather than "whatever is on top": the screen
                    // stays hit-testable through its 300ms exit, and two quick taps on the
                    // chevron were enough to pop the screen behind it as well.
                    onCollapse = {
                        navController.popBackStack(Routes.NOW_PLAYING, inclusive = true)
                    },
                )
            }
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
                Routes.DOWNLOADS,
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
            ) {
                DownloadsScreen(
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
 * The three tabs side by side, swiped or scrolled to by the bar. Neighbours are kept composed
 * so the first frame of a swipe is not spent building the next screen's view model; with three
 * pages that means every tab is composed once and stays. Settings' effects therefore run while
 * it is off screen, which is harmless — its only outward one, the install prompt, needs a
 * download the user started from that page.
 *
 * Back from a later page returns to Channels rather than leaving the app, as the per-tab stacks
 * did before. It also catches the edge swipe that gesture navigation turns into Back, which is
 * otherwise indistinguishable from a swipe toward the first page.
 */
@Composable
private fun HomePager(
    pagerState: PagerState,
    onOpenChannel: (String) -> Unit,
    onOpenWatchlist: (Long) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = pagerState.settledPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (tabs[page]) {
            Tab.Channels -> ChannelsScreen(onOpenChannel = onOpenChannel)
            Tab.Watchlists -> WatchlistsScreen(onOpenWatchlist = onOpenWatchlist)
            Tab.Settings -> SettingsScreen(onOpenDownloads = onOpenDownloads)
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
        // At the largest accessibility font sizes the label cannot fit its share and was
        // silently clipping mid-word ("Watchlist"). Past that point the pills go icon-only —
        // the fill still shows which tab is selected, and the label moves to the content
        // description so a screen reader is not left with an unlabelled button.
        val labelled = LocalDensity.current.fontScale <= 1.3f

        Row(
            Modifier.padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            tabs.forEach { tab ->
                val label = stringResource(tab.labelRes)
                val selected = isSelected(tab)
                // Three things change on selection — fill, content colour and width — and all
                // three have to move together, or the ones that snap give the whole switch away.
                // Width is the one that matters most: the label arriving used to jump the
                // neighbouring tabs sideways in a single frame.
                val motion = tween<Color>(TAB_MOTION_MS, easing = PlayerEasing)
                val fill by animateColorAsState(
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = motion,
                    label = "tabFill",
                )
                val contentColor by animateColorAsState(
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = motion,
                    label = "tabContent",
                )
                // Every tab holds a share of the bar rather than shrinking to its icon. An
                // icon-only tab was a 44dp button adrift in a slot three times that wide: the
                // space around it looked tappable and was not, and the press effect came back as
                // a small circle instead of the pill the selected state uses. The selected share
                // is larger to fit the label, and animating the weight is what grows the pill.
                // A bounded ripple grows outward from the touch point, and this pill is wide
                // enough (173dp when selected) that a normal tap ends long before the ripple
                // reaches either end — so the feedback read as a small circle adrift in a big
                // button. Measured: the ripple needed a ~2s held press to span the pill. A flat
                // state layer covers the whole thing at once instead.
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val pressLayer by animateColorAsState(
                    if (pressed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(
                        durationMillis = if (pressed) TAB_PRESS_IN_MS else TAB_PRESS_OUT_MS,
                        easing = PlayerEasing,
                    ),
                    label = "tabPress",
                )

                val share by animateFloatAsState(
                    if (selected && labelled) 1.9f else 1f,
                    animationSpec = tween(TAB_MOTION_MS, easing = PlayerEasing),
                    label = "tabShare",
                )

                // From a sub-screen, selecting its highlighted tab pops back to the tab's root —
                // how you get from a channel's uploads back to the channel list. On home, the
                // tab you are already on is a no-op.
                Row(
                    Modifier
                        .weight(share)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(fill)
                        .background(pressLayer)
                        .selectable(
                            selected = selected,
                            interactionSource = interactions,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        )
                        .padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = if (selected && labelled) null else label,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    AnimatedVisibility(
                        visible = selected && labelled,
                        // Expanding rather than fading alone: the width has to come in over the
                        // same 220ms, so the pill grows into place instead of popping wider.
                        // Anchored at the start: the default reveals the label from its right
                        // edge, so it read as "...atchlists" jammed against the icon on the way in.
                        enter = expandHorizontally(
                            tween(TAB_MOTION_MS, easing = PlayerEasing),
                            expandFrom = Alignment.Start,
                        ) + fadeIn(tween(TAB_MOTION_MS, easing = PlayerEasing)),
                        exit = shrinkHorizontally(
                            tween(TAB_MOTION_MS, easing = PlayerEasing),
                            shrinkTowards = Alignment.Start,
                        ) + fadeOut(tween(TAB_MOTION_MS / 2)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(Spacing.sm))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                                maxLines = 1,
                                // Backstop for narrow screens, where the label can run out of
                                // room below the cutoff above.
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
