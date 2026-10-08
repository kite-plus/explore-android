package plus.kite.explore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.http.HttpResponseCache
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import java.io.File
import java.lang.ref.WeakReference
import plus.kite.explore.app.AppModel
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.DeepLink
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.Root
import plus.kite.explore.app.ShareSubject
import plus.kite.explore.app.SignInMode
import plus.kite.explore.core.LinkOpener
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.Entry
import plus.kite.explore.data.ExploreJson
import plus.kite.explore.design.ExploreTheme

class ExploreApplication : android.app.Application() {
    /** One app model for the life of the process, shared by the activity and the receivers. */
    val app: AppModel by lazy { AppModel(this) }

    override fun onCreate() {
        super.onCreate()
        // Thumbnails and icons are kept on disk between launches.
        HttpResponseCache.install(File(cacheDir, "http"), 160L shl 20)
    }
}

val Context.exploreApp: AppModel get() = (applicationContext as ExploreApplication).app

class MainActivity : ComponentActivity() {
    private val app: AppModel get() = exploreApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app.activity = WeakReference(this)
        if (savedInstanceState == null) {
            app.bootstrap()
            handle(intent)
        }
        setContent {
            ExploreTheme {
                CompositionLocalProvider(LocalApp provides app) { Root() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        app.activity = WeakReference(this)
        app.catchUp()
    }

    /** Links into the app, and the items of a post's browser tab menu. */
    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.action == LinkOpener.ACTION_POST) {
            val entry = intent.getStringExtra(LinkOpener.EXTRA_ENTRY)?.let { ExploreJson.decodeFromString(Entry.serializer(), it) }
            val blog = intent.getStringExtra(LinkOpener.EXTRA_BLOG)?.let { ExploreJson.decodeFromString(BlogRef.serializer(), it) }
            when (intent.getStringExtra(LinkOpener.EXTRA_KIND)) {
                "card" -> if (entry != null) app.sheet = AppSheet.Share(ShareSubject.Post(entry, blog))
                "blog" -> if (blog != null) app.showBlog(blog)
                "sign-in" -> app.sheet = AppSheet.SignIn(SignInMode.SignIn)
            }
            return
        }
        intent.dataString?.let { app.open(DeepLink.parse(it)) }
    }
}

/** Follows or unfollows from a post's browser tab menu without leaving the tab. */
class PostActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val blog = intent.getStringExtra(LinkOpener.EXTRA_BLOG)?.let { ExploreJson.decodeFromString(BlogRef.serializer(), it) } ?: return
        context.exploreApp.toggleFollow(blog) { message ->
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
