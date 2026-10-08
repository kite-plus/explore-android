package plus.kite.explore.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.launch
import plus.kite.explore.MainActivity
import plus.kite.explore.R
import plus.kite.explore.app.AppModel
import plus.kite.explore.app.LanguageFilter
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.ApiClient
import plus.kite.explore.data.Entry
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreTheme
import plus.kite.explore.design.FormSection
import plus.kite.explore.design.RowDivider
import plus.kite.explore.design.SettingsRow
import plus.kite.explore.design.Palette
import plus.kite.explore.discover.languageTitle

/** The latest posts on the Home Screen, optionally narrowed to Chinese or English blogs. */
class LatestPostsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val language = WidgetSettings.language(context, appWidgetId)
        val result = load(language)
        provideContent {
            GlanceTheme { WidgetContent(result) }
        }
    }

    private class Result(val posts: List<Entry>, val failed: Boolean)

    /** The widget always reads explore.kite.plus; the app's server choice stays inside the app. */
    private suspend fun load(language: LanguageFilter): Result = try {
        Result(ApiClient(AppModel.DEFAULT_SERVER).entries(null, language.code, null, limit = 8).data, false)
    } catch (_: Exception) {
        Result(emptyList(), true)
    }

    @androidx.compose.runtime.Composable
    private fun WidgetContent(result: Result) {
        val context = LocalContext.current
        val size = LocalSize.current
        Box(
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.White, Color(0xFF1C1C1E)))
                .cornerRadius(22.dp)
                .padding(14.dp),
        ) {
            when {
                result.posts.isEmpty() -> Column(
                    GlanceModifier.fillMaxSize().clickable(actionStartActivity(open(context, "explore://discover"))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        context.getString(if (result.failed) R.string.widget_failed else R.string.widget_empty),
                        style = TextStyle(color = secondary, fontSize = 12.sp),
                    )
                }
                size.width < MEDIUM.width -> Small(result.posts.first())
                else -> PostList(result.posts.take(if (size.height >= LARGE.height) 6 else 3), large = size.height >= LARGE.height)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Small(post: Entry) {
        val context = LocalContext.current
        Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(open(context, post)))) {
            post.blog?.let { blog ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Initial(blog.host, blog.name, 18)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(blog.name, maxLines = 1, style = TextStyle(color = secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            Text(post.title, maxLines = 4, style = TextStyle(color = primary, fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            Text(time(context, post), style = TextStyle(color = secondary, fontSize = 11.sp))
        }
    }

    @androidx.compose.runtime.Composable
    private fun PostList(posts: List<Entry>, large: Boolean) {
        val context = LocalContext.current
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open(context, "explore://discover"))), verticalAlignment = Alignment.CenterVertically) {
                Text("Kite", style = TextStyle(color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Text(context.getString(R.string.widget_latest), style = TextStyle(color = secondary, fontSize = 11.sp))
            }
            for (post in posts) {
                Spacer(GlanceModifier.height(if (large) 12.dp else 9.dp))
                Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open(context, post)))) {
                    post.blog?.let {
                        Initial(it.host, it.name, 22)
                        Spacer(GlanceModifier.width(10.dp))
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        Text(post.title, maxLines = if (large) 2 else 1, style = TextStyle(color = primary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        Text(
                            listOfNotNull(post.blog?.name, time(context, post).ifEmpty { null }).joinToString(" · "),
                            maxLines = 1,
                            style = TextStyle(color = secondary, fontSize = 11.sp),
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Initial(host: String, name: String, size: Int) {
        Box(
            GlanceModifier.size(size.dp).cornerRadius((size / 2).dp).background(Palette.color(host)),
            contentAlignment = Alignment.Center,
        ) {
            Text(Palette.initial(name), style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = (size * 0.48f).sp, fontWeight = FontWeight.Bold))
        }
    }

    private fun time(context: Context, post: Entry): String = post.publishedAt?.let { Formatting.relative(context, it) }.orEmpty()

    /** Opens a post through the app, which shows it on the author's site. */
    private fun open(context: Context, post: Entry): Intent = open(context, "explore://open?url=" + Uri.encode(post.url))

    private fun open(context: Context, link: String): Intent =
        Intent(Intent.ACTION_VIEW, link.toUri()).setComponent(ComponentName(context, MainActivity::class.java))

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
        private val primary = ColorProvider(Color.Black, Color.White)
        private val secondary = ColorProvider(Color(0x993C3C43), Color(0x99EBEBF5))
    }
}

class LatestPostsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestPostsWidget()
}

/** Each widget's blog language, chosen when it is added or later from its settings. */
object WidgetSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun language(context: Context, appWidgetId: Int): LanguageFilter =
        LanguageFilter.of(prefs(context).getString("language.$appWidgetId", null) ?: "all")

    fun setLanguage(context: Context, appWidgetId: Int, language: LanguageFilter) {
        prefs(context).edit { putString("language.$appWidgetId", language.key) }
    }
}

/** Picks a widget's blog language. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val current = WidgetSettings.language(this, appWidgetId)
        setContent {
            ExploreTheme {
                val scope = rememberCoroutineScope()
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .background(Explore.colors.groupedBackground)
                        .safeDrawingPadding()
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.widget_name), fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        FormSection(stringResource(R.string.filter_blog_language), stringResource(R.string.widget_description)) {
                            LanguageFilter.entries.forEachIndexed { index, option ->
                                SettingsRow(languageTitle(option), trailing = if (option == current) "✓" else null) {
                                    WidgetSettings.setLanguage(this@WidgetConfigActivity, appWidgetId, option)
                                    scope.launch {
                                        val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                                        LatestPostsWidget().update(this@WidgetConfigActivity, glanceId)
                                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                        finish()
                                    }
                                }
                                if (index < LanguageFilter.entries.lastIndex) RowDivider(inset = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
