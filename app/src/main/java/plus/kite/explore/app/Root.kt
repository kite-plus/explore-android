package plus.kite.explore.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import plus.kite.explore.R
import plus.kite.explore.account.AboutScreen
import plus.kite.explore.account.ChangePasswordSheet
import plus.kite.explore.account.ClaimSheet
import plus.kite.explore.account.DeleteAccountSheet
import plus.kite.explore.account.EditNameSheet
import plus.kite.explore.account.MeScreen
import plus.kite.explore.account.OwnedBlogsScreen
import plus.kite.explore.account.ReportSheet
import plus.kite.explore.account.ServerScreen
import plus.kite.explore.account.SignInSheet
import plus.kite.explore.account.SubscriptionsScreen
import plus.kite.explore.blogs.BlogScreen
import plus.kite.explore.blogs.BlogsScreen
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ToastView
import plus.kite.explore.discover.DiscoverScreen
import plus.kite.explore.discover.FilterSheet
import plus.kite.explore.feed.HandoffOverlay
import plus.kite.explore.feed.NoticeScreen
import plus.kite.explore.search.SearchScreen
import plus.kite.explore.search.TopicScreen
import plus.kite.explore.share.ShareSheet
import plus.kite.explore.submit.SubmissionScreen
import plus.kite.explore.submit.SubmissionsScreen
import plus.kite.explore.submit.SubmitSheet

/** The id of the screen being drawn, so it can keep its state in the app model while on its stack. */
val LocalScreenId = staticCompositionLocalOf { 0L }

/**
 * The tab bar: Discover, Blogs and Me, each with its own stack of screens, as on iOS. Discover
 * holds the latest, recommended and following streams, as the website does.
 */
@Composable
fun Root() {
    val app = LocalApp.current
    val colors = Explore.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { TabBar() },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                key(app.tab) { TabStack(app.tab) }
            }
        }
        AnimatedVisibility(
            visible = app.toast != null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            app.toast?.let { ToastView(it, Modifier.clickable { app.dismissToast() }) }
        }
        AnimatedVisibility(app.handoff != null, enter = fadeIn(), exit = fadeOut()) {
            app.handoff?.let { HandoffOverlay(it) }
        }
    }
    Sheets()
}

@Composable
private fun TabBar() {
    val app = LocalApp.current
    val colors = Explore.colors
    NavigationBar(containerColor = colors.card.copy(alpha = 0.97f)) {
        for (tab in AppTab.entries) {
            val (icon, label) = when (tab) {
                AppTab.Discover -> Icons.Rounded.Explore to R.string.tab_discover
                AppTab.Blogs -> Icons.Rounded.GridView to R.string.tab_blogs
                AppTab.Me -> Icons.Rounded.AccountCircle to R.string.tab_me
            }
            NavigationBarItem(
                selected = app.tab == tab,
                onClick = { app.select(tab) },
                icon = { Icon(icon, null) },
                label = { Text(stringResource(label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    indicatorColor = colors.fill,
                    unselectedIconColor = colors.label,
                    unselectedTextColor = colors.label,
                ),
            )
        }
    }
}

@Composable
private fun TabStack(tab: AppTab) {
    val app = LocalApp.current
    NavDisplay(
        backStack = app.stacks.getValue(tab),
        onBack = { app.back() },
        entryProvider = { screen ->
            NavEntry(screen, contentKey = screen.id) {
                CompositionLocalProvider(LocalScreenId provides screen.id) {
                    Destination(tab, screen.route)
                }
            }
        },
    )
}

@Composable
private fun Destination(tab: AppTab, route: Route) {
    when (route) {
        Route.Root -> when (tab) {
            AppTab.Discover -> DiscoverScreen()
            AppTab.Blogs -> BlogsScreen()
            AppTab.Me -> MeScreen()
        }
        is Route.BlogPage -> BlogScreen(route.ref)
        is Route.TopicPage -> TopicScreen(route.slug)
        Route.Search -> SearchScreen()
        is Route.NoticePage -> NoticeScreen(route.id, route.preview)
        is Route.SubmissionPage -> SubmissionScreen(route.id)
        Route.Submissions -> SubmissionsScreen()
        Route.Subscriptions -> SubscriptionsScreen()
        Route.OwnedBlogs -> OwnedBlogsScreen()
        Route.Server -> ServerScreen()
        Route.About -> AboutScreen()
    }
}

@Composable
private fun Sheets() {
    val app = LocalApp.current
    val close = { app.sheet = null }
    when (val sheet = app.sheet) {
        null -> Unit
        is AppSheet.SignIn -> key(sheet) { SignInSheet(sheet.mode, close) }
        is AppSheet.Submit -> key(sheet) { SubmitSheet(sheet.site, sheet.feed, close) }
        is AppSheet.Report -> ReportSheet(sheet.target, close)
        is AppSheet.Claim -> ClaimSheet(sheet.blog, close)
        is AppSheet.Share -> key(sheet) { ShareSheet(sheet.subject, close) }
        AppSheet.EditName -> EditNameSheet(close)
        is AppSheet.ChangePassword -> ChangePasswordSheet(sheet.signedInWith, close)
        AppSheet.DeleteAccount -> DeleteAccountSheet(close)
        AppSheet.Filter -> FilterSheet(close)
    }
}
