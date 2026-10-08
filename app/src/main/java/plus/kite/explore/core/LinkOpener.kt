package plus.kite.explore.core

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import plus.kite.explore.MainActivity
import plus.kite.explore.PostActionReceiver
import plus.kite.explore.R
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.Entry
import plus.kite.explore.data.ExploreJson

/**
 * Opens links that leave Explore. Posts and blog homepages are read on the author's own
 * site, in a browser tab inside the app unless the reader prefers the browser itself; the
 * app never sees what happens there.
 */
object LinkOpener {
    const val ACTION_POST = "plus.kite.explore.action.POST"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ENTRY = "entry"
    const val EXTRA_BLOG = "blog"

    /** What the tab's menu offers while a post is open: its card, following its blog, and the blog's page. */
    data class PostMenu(val entry: Entry, val blog: BlogRef?, val following: Boolean, val signedIn: Boolean)

    fun open(context: Context, url: String, source: String? = null, inBrowser: Boolean = false, menu: PostMenu? = null) {
        if (!Formatting.isWeb(url)) return
        val target = (source?.let { SourceTag.tagged(url, it) } ?: url).toUri()
        if (inBrowser) {
            openInBrowser(context, target)
            return
        }
        val builder = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setUrlBarHidingEnabled(true)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(Color.WHITE).build())
            .setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_DARK,
                CustomTabColorSchemeParams.Builder().setToolbarColor(Color.BLACK).build(),
            )
        if (menu != null) addMenu(context, builder, menu)
        try {
            builder.build().launchUrl(context, target)
        } catch (_: ActivityNotFoundException) {
            openInBrowser(context, target)
        }
    }

    fun openInBrowser(context: Context, url: String, source: String? = null) {
        if (!Formatting.isWeb(url)) return
        openInBrowser(context, (source?.let { SourceTag.tagged(url, it) } ?: url).toUri())
    }

    private fun openInBrowser(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No browser on the device; nothing else can show the page.
        }
    }

    private fun addMenu(context: Context, builder: CustomTabsIntent.Builder, menu: PostMenu) {
        val entry = ExploreJson.encodeToString(Entry.serializer(), menu.entry)
        builder.addMenuItem(context.getString(R.string.post_action_card), activityIntent(context, 1, "card", entry, menu.blog))
        val blog = menu.blog ?: return
        val follow = if (menu.signedIn) {
            val intent = Intent(context, PostActionReceiver::class.java)
                .putExtra(EXTRA_BLOG, ExploreJson.encodeToString(BlogRef.serializer(), blog))
            PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            activityIntent(context, 2, "sign-in", entry, blog)
        }
        val followTitle = if (menu.following) R.string.post_action_unfollow else R.string.post_action_follow
        builder.addMenuItem(context.getString(followTitle, blog.name), follow)
        builder.addMenuItem(context.getString(R.string.post_action_blog), activityIntent(context, 3, "blog", entry, blog))
    }

    /** Brings the app back over the tab and does what the item names. */
    private fun activityIntent(context: Context, code: Int, kind: String, entry: String, blog: BlogRef?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_POST)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_ENTRY, entry)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (blog != null) intent.putExtra(EXTRA_BLOG, ExploreJson.encodeToString(BlogRef.serializer(), blog))
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
