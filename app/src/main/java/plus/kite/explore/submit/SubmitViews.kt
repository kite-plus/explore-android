package plus.kite.explore.submit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import plus.kite.explore.R
import plus.kite.explore.account.ButtonSpinner
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.Route
import plus.kite.explore.core.Formatting
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.CheckReport
import plus.kite.explore.data.Problem
import plus.kite.explore.data.Severity
import plus.kite.explore.data.Submission
import plus.kite.explore.data.SubmissionPreview
import plus.kite.explore.data.SubmissionStatus
import plus.kite.explore.data.isCancellation
import plus.kite.explore.design.EmptyState
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreSheet
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.FormError
import plus.kite.explore.design.FormField
import plus.kite.explore.design.FormSection
import plus.kite.explore.design.Hairline
import plus.kite.explore.design.LoadFailed
import plus.kite.explore.design.Palette
import plus.kite.explore.design.PrimaryButton
import plus.kite.explore.design.PushedScreen
import plus.kite.explore.design.RowDivider
import plus.kite.explore.design.SecondaryButton
import plus.kite.explore.design.SettingsRow
import plus.kite.explore.design.SheetBar

private sealed interface Stage {
    data object Form : Stage
    data class Preview(val preview: SubmissionPreview) : Stage
    data class Done(val submission: Submission) : Stage
}

private data class Failure(val message: String, val report: CheckReport? = null, val listed: BlogRef? = null, val pendingId: String? = null)

/**
 * Submits a blog: Explore checks its feed on the spot, the author reviews what was found, and
 * a maintainer reviews the submission.
 */
@Composable
fun SubmitSheet(site: String, feed: String, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<Stage>(Stage.Form) }
    var siteAddress by remember { mutableStateOf(site) }
    var feedAddress by remember { mutableStateOf(feed) }
    var note by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Failure?>(null) }

    fun describe(error: Exception, siteUrl: String?): Failure {
        val apiError = error as? ApiError ?: return Failure(app.message(error))
        val host = siteUrl?.let { Formatting.hostOf(it) }.orEmpty()
        return when (apiError.code) {
            "invalid_url" -> Failure(app.string(R.string.server_error_invalid))
            "already_listed" -> Failure(
                app.string(R.string.submit_error_listed),
                listed = if (host.isEmpty()) null else BlogRef(host, host, siteUrl.orEmpty(), ""),
            )
            "already_pending" -> Failure(app.message(apiError), pendingId = (apiError as? ApiError.Server)?.submissionId)
            "excluded" -> Failure(app.string(R.string.submit_error_excluded))
            "check_failed" -> Failure(app.string(R.string.submit_error_check_failed), report = (apiError as? ApiError.Server)?.report)
            "rate_limited" -> Failure(app.string(R.string.submit_error_rate_limited))
            "feature_disabled" -> Failure(app.string(R.string.submit_error_closed))
            else -> Failure(app.message(apiError))
        }
    }

    fun check() {
        val siteUrl = Formatting.normalizedAddress(siteAddress)
        if (!Formatting.isWeb(siteUrl)) {
            failure = Failure(app.string(R.string.server_error_invalid))
            return
        }
        val feedUrl = if (feedAddress.isBlank()) "" else Formatting.normalizedAddress(feedAddress)
        checking = true
        failure = null
        scope.launch {
            try {
                val preview = app.client.previewSubmission(siteUrl, feedUrl)
                name = preview.title
                summary = preview.description
                stage = Stage.Preview(preview)
            } catch (error: Exception) {
                if (error.isCancellation) throw error
                failure = describe(error, siteUrl)
            } finally {
                checking = false
            }
        }
    }

    fun submit(preview: SubmissionPreview) {
        submitting = true
        failure = null
        scope.launch {
            try {
                val submission = app.client.submit(preview.siteUrl, preview.feedUrl, note.trim(), name.trim(), summary.trim())
                app.rememberSubmission(submission.id)
                stage = Stage.Done(submission)
            } catch (error: Exception) {
                if (error.isCancellation) throw error
                failure = describe(error, preview.siteUrl)
            } finally {
                submitting = false
            }
        }
    }

    /** Leaves the sheet for a page in the app. */
    fun go(route: Route) {
        onDismiss()
        app.navigate(route)
    }

    ExploreSheet(onDismiss = { if (!checking && !submitting) onDismiss() }) {
        SheetBar(stringResource(R.string.submit_title), onDismiss)
        when (val current = stage) {
            Stage.Form -> Column(
                Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                FormSection {
                    Text(stringResource(R.string.submit_intro), style = ExploreType.subheadline, color = colors.secondaryLabel, modifier = Modifier.padding(16.dp))
                }
                FormSection(stringResource(R.string.submit_blog_address)) {
                    FormField(siteAddress, { siteAddress = it }, "https://blog.example.com", keyboardType = KeyboardType.Uri)
                }
                FormSection(stringResource(R.string.submit_feed_address), stringResource(R.string.submit_feed_footer)) {
                    FormField(feedAddress, { feedAddress = it }, "https://blog.example.com/feed.xml", keyboardType = KeyboardType.Uri)
                }
                FormSection(stringResource(R.string.submit_note), "${note.length} / 500") {
                    FormField(note, { note = it }, stringResource(R.string.submit_note_prompt), singleLine = false, minLines = 3, imeAction = ImeAction.Default)
                }
                failure?.let { FailureSection(it, ::go) }
                PrimaryButton(
                    onClick = ::check,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !checking && siteAddress.isNotBlank() && note.length <= 500,
                    large = true,
                ) {
                    if (checking) ButtonSpinner()
                    Text((if (checking) "  " else "") + stringResource(if (checking) R.string.submit_checking else R.string.submit_check), style = ExploreType.headline)
                }
                Text(stringResource(R.string.submit_check_time), style = ExploreType.caption, color = colors.secondaryLabel, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            is Stage.Preview -> PreviewForm(
                preview = current.preview,
                name = name,
                onName = { name = it },
                summary = summary,
                onSummary = { summary = it },
                failure = failure,
                submitting = submitting,
                onEdit = {
                    failure = null
                    stage = Stage.Form
                },
                onSubmit = { submit(current.preview) },
                go = ::go,
            )
            is Stage.Done -> Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.size(128.dp).clip(CircleShape).background(colors.green.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(76.dp), tint = colors.green)
                }
                Text(stringResource(R.string.submit_done_title), style = ExploreType.title, color = colors.label)
                Text(stringResource(R.string.submit_done_description, current.submission.host), style = ExploreType.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                SecondaryButton(onClick = { go(Route.SubmissionPage(current.submission.id)) }, modifier = Modifier.fillMaxWidth(), large = true) {
                    Text(stringResource(R.string.submit_view_status), style = ExploreType.headline)
                }
                PrimaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), large = true) {
                    Text(stringResource(R.string.action_done), style = ExploreType.headline)
                }
            }
        }
    }
}

@Composable
private fun PreviewForm(
    preview: SubmissionPreview,
    name: String,
    onName: (String) -> Unit,
    summary: String,
    onSummary: (String) -> Unit,
    failure: Failure?,
    submitting: Boolean,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
    go: (Route) -> Unit,
) {
    val context = LocalContext.current
    val colors = Explore.colors
    Column(
        Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        FormSection(stringResource(R.string.submit_preview), stringResource(R.string.submit_preview_footer)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val color = Palette.color(preview.host)
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Palette.initial(name.ifEmpty { preview.host }), style = ExploreType.title2.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                }
                Column {
                    Text(name.ifEmpty { preview.host }, style = ExploreType.headline, color = colors.label)
                    Text(preview.host, style = ExploreType.subheadline, color = colors.secondaryLabel)
                }
            }
            RowDivider(inset = false)
            FormField(name, onName, stringResource(R.string.submit_blog_name))
            RowDivider(inset = false)
            FormField(summary, onSummary, stringResource(R.string.submit_description), singleLine = false, minLines = 2, imeAction = ImeAction.Default)
        }
        FormSection(stringResource(R.string.submit_feed)) {
            Fact(stringResource(R.string.submit_feed_url), preview.feedUrl)
            preview.latestEntryTitle?.takeIf { it.isNotEmpty() }?.let {
                RowDivider(inset = false)
                Fact(stringResource(R.string.submit_latest_post), it)
            }
            RowDivider(inset = false)
            Fact(stringResource(R.string.submit_usable_posts), "${preview.itemsValid} / ${preview.itemsTotal}")
            Formatting.generatorName(context, preview.generator)?.let {
                RowDivider(inset = false)
                Fact(stringResource(R.string.submit_blog_system), it)
            }
            Formatting.languageName(preview.language)?.let {
                RowDivider(inset = false)
                Fact(stringResource(R.string.submit_language), it)
            }
        }
        FormSection(stringResource(R.string.submit_check_results)) {
            val problems = preview.checkReport?.problems.orEmpty()
            if (problems.isEmpty()) NoProblems() else CheckProblemsList(problems)
        }
        failure?.let { FailureSection(it, go) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(onClick = onEdit, enabled = !submitting, large = true) { Text(stringResource(R.string.action_edit), style = ExploreType.headline) }
            PrimaryButton(onClick = onSubmit, modifier = Modifier.weight(1f), enabled = !submitting && name.isNotBlank(), large = true) {
                if (submitting) ButtonSpinner()
                Text((if (submitting) "  " else "") + stringResource(if (submitting) R.string.submit_submitting else R.string.submit_confirm), style = ExploreType.headline, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Fact(title: String, value: String) {
    val colors = Explore.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = ExploreType.body, color = colors.label)
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = ExploreType.footnote, color = colors.secondaryLabel, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NoProblems() {
    val colors = Explore.colors
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Verified, null, tint = colors.green)
        Text(stringResource(R.string.submit_no_problems), style = ExploreType.body, color = colors.green)
    }
}

@Composable
private fun FailureSection(failure: Failure, go: (Route) -> Unit) {
    val colors = Explore.colors
    FormSection {
        FormError(failure.message)
        failure.listed?.let {
            RowDivider(inset = false)
            SettingsRow(stringResource(R.string.submit_view_blog), color = colors.accent) { go(Route.BlogPage(it)) }
        }
        failure.pendingId?.let {
            RowDivider(inset = false)
            SettingsRow(stringResource(R.string.submit_view_submission), color = colors.accent) { go(Route.SubmissionPage(it)) }
        }
        val problems = failure.report?.problems.orEmpty()
        if (problems.isNotEmpty()) {
            RowDivider(inset = false)
            CheckProblemsList(problems)
        }
    }
}

/** The problems a feed check found, worst first. Hints come from the server in the interface language. */
@Composable
fun CheckProblemsList(problems: List<Problem>) {
    val colors = Explore.colors
    val sorted = problems.sortedBy { it.severity.ordinal }
    Column {
        sorted.forEachIndexed { index, problem ->
            val (icon, tint) = severityStyle(problem.severity)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(22.dp), tint = tint)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(problemTitle(problem.code), style = ExploreType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label, modifier = Modifier.weight(1f, fill = false))
                        problem.count?.takeIf { it > 1 }?.let {
                            Text(" ×$it", style = ExploreType.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.secondaryLabel)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(severityLabel(problem.severity), style = ExploreType.caption2.copy(fontWeight = FontWeight.Bold), color = tint)
                    }
                    problem.hint?.takeIf { it.isNotEmpty() }?.let { Text(it, style = ExploreType.footnote, color = colors.secondaryLabel) }
                    problem.detail?.takeIf { it.isNotEmpty() }?.let {
                        SelectionContainer {
                            Text(it, style = ExploreType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.tertiaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (index < sorted.lastIndex) Hairline(Modifier.padding(start = 50.dp))
        }
    }
}

@Composable
private fun severityStyle(severity: Severity): Pair<ImageVector, Color> {
    val colors = Explore.colors
    return when (severity) {
        Severity.Error -> Icons.Rounded.Cancel to colors.red
        Severity.Warning -> Icons.Rounded.Warning to colors.orange
        Severity.Info -> Icons.Rounded.Info to colors.accent
    }
}

@Composable
private fun severityLabel(severity: Severity): String = stringResource(
    when (severity) {
        Severity.Error -> R.string.severity_error
        Severity.Warning -> R.string.severity_warning
        Severity.Info -> R.string.severity_note
    },
)

@Composable
private fun problemTitle(code: String): String {
    val id = when (code) {
        "feed_not_found" -> R.string.problem_feed_not_found
        "robots_disallowed" -> R.string.problem_robots_disallowed
        "http_error" -> R.string.problem_http_error
        "too_large" -> R.string.problem_too_large
        "parse_error" -> R.string.problem_parse_error
        "links_off_domain" -> R.string.problem_links_off_domain
        "links_elsewhere" -> R.string.problem_links_elsewhere
        "site_moved" -> R.string.problem_site_moved
        "no_valid_items" -> R.string.problem_no_valid_items
        "stale" -> R.string.problem_stale
        "some_links_off_domain" -> R.string.problem_some_links_off_domain
        "no_dates" -> R.string.problem_no_dates
        "dates_untrusted" -> R.string.problem_dates_untrusted
        "includes_non_posts" -> R.string.problem_includes_non_posts
        "no_conditional_get" -> R.string.problem_no_conditional_get
        "redirected" -> R.string.problem_redirected
        else -> return code
    }
    return stringResource(id)
}

@Composable
private fun statusStyle(status: SubmissionStatus): Triple<ImageVector, Color, String> {
    val colors = Explore.colors
    return when (status) {
        SubmissionStatus.Pending -> Triple(Icons.Rounded.HourglassTop, colors.orange, stringResource(R.string.submission_pending))
        SubmissionStatus.Approved -> Triple(Icons.Rounded.Verified, colors.green, stringResource(R.string.submission_approved))
        SubmissionStatus.Rejected -> Triple(Icons.Rounded.Cancel, colors.red, stringResource(R.string.submission_rejected))
    }
}

/** One submission's progress through review. */
@Composable
fun SubmissionScreen(id: String) {
    val app = LocalApp.current
    val colors = Explore.colors
    var submission by remember(id) { mutableStateOf<Submission?>(null) }
    var failure by remember(id) { mutableStateOf<Throwable?>(null) }
    suspend fun load() {
        try {
            submission = app.client.submission(id)
            failure = null
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            if (submission == null) failure = error
        }
    }
    LaunchedEffect(id, app.server) { load() }
    PushedScreen(stringResource(R.string.submission_title), background = colors.groupedBackground) { padding ->
        val current = submission
        val error = failure
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when {
                current != null -> {
                    val (icon, tint, title) = statusStyle(current.status)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(76.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(34.dp), tint = tint)
                        }
                        Text(title, style = ExploreType.title2, color = colors.label)
                        Text(current.host, style = ExploreType.subheadline, color = colors.secondaryLabel)
                    }
                    FormSection {
                        Fact(stringResource(R.string.submit_blog_address), current.siteUrl)
                        if (current.feedUrl.isNotEmpty()) {
                            RowDivider(inset = false)
                            Fact(stringResource(R.string.submit_feed_url), current.feedUrl)
                        }
                        current.createdAt?.let {
                            RowDivider(inset = false)
                            Fact(stringResource(R.string.submission_submitted), Formatting.dateTime(it))
                        }
                        current.reviewedAt?.let {
                            RowDivider(inset = false)
                            Fact(stringResource(R.string.submission_reviewed), Formatting.dateTime(it))
                        }
                    }
                    current.reviewNote?.takeIf { it.isNotEmpty() }?.let {
                        FormSection(stringResource(R.string.submission_review_note)) {
                            Text(it, style = ExploreType.body, color = colors.label, modifier = Modifier.padding(16.dp))
                        }
                    }
                    if (current.status == SubmissionStatus.Approved) {
                        FormSection {
                            SettingsRow(stringResource(R.string.submit_view_blog), color = colors.accent) {
                                app.navigate(
                                    Route.BlogPage(
                                        BlogRef(current.host, current.checkReport?.title ?: current.host, current.siteUrl, current.checkReport?.language.orEmpty()),
                                    ),
                                )
                            }
                        }
                    }
                    FormSection(stringResource(R.string.submit_check_results)) {
                        val problems = current.checkReport?.problems.orEmpty()
                        if (problems.isEmpty()) NoProblems() else CheckProblemsList(problems)
                    }
                }
                error != null -> LoadFailed(app.message(error)) { load() }
                else -> CircularProgressIndicator(Modifier.padding(40.dp))
            }
        }
    }
}

/** Submissions made from this device. Explore links them to no account, so the app remembers their ids. */
@Composable
fun SubmissionsScreen() {
    val app = LocalApp.current
    val colors = Explore.colors
    val submissions = remember { mutableStateMapOf<String, Submission>() }
    LaunchedEffect(app.submissionIds, app.server) {
        for (id in app.submissionIds) {
            try {
                submissions[id] = app.client.submission(id)
            } catch (error: Exception) {
                if (error.isCancellation) throw error
            }
        }
    }
    PushedScreen(
        stringResource(R.string.me_my_submissions),
        background = colors.groupedBackground,
        actions = {
            IconButton(onClick = { app.sheet = AppSheet.Submit() }) { Icon(Icons.Rounded.Add, stringResource(R.string.submit_title)) }
        },
    ) { padding ->
        if (app.submissionIds.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                EmptyState(Icons.Rounded.Inbox, stringResource(R.string.submissions_none_title), stringResource(R.string.submissions_none_description)) {
                    PrimaryButton(onClick = { app.sheet = AppSheet.Submit() }) { Text(stringResource(R.string.submit_title)) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    FormSection {
                        app.submissionIds.forEachIndexed { index, id ->
                            SubmissionLine(id, submissions[id])
                            if (index < app.submissionIds.lastIndex) Hairline(Modifier.padding(start = 58.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionLine(id: String, submission: Submission?) {
    val app = LocalApp.current
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .clickable { app.navigate(Route.SubmissionPage(id)) }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (submission != null) {
            val (icon, tint, title) = statusStyle(submission.status)
            Icon(icon, null, Modifier.size(28.dp), tint = tint)
            Column(Modifier.weight(1f)) {
                Text(submission.checkReport?.title ?: submission.host, style = ExploreType.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    title + (submission.createdAt?.let { " · " + Formatting.day(it) } ?: ""),
                    style = ExploreType.subheadline,
                    color = colors.secondaryLabel,
                )
            }
        } else {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            Text(id.take(8) + "…", style = ExploreType.subheadline.copy(fontFamily = FontFamily.Monospace), color = colors.secondaryLabel, modifier = Modifier.weight(1f))
        }
        IconButton(onClick = { app.forgetSubmission(id) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_remove), tint = colors.tertiaryLabel) }
    }
}
