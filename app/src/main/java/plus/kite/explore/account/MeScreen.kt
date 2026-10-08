package plus.kite.explore.account

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import plus.kite.explore.BuildConfig
import plus.kite.explore.R
import plus.kite.explore.app.AppSheet
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.Route
import plus.kite.explore.app.SignInMode
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.FormSection
import plus.kite.explore.design.MarkTile
import plus.kite.explore.design.Pill
import plus.kite.explore.design.PrimaryButton
import plus.kite.explore.design.RowDivider
import plus.kite.explore.design.SettingsRow
import plus.kite.explore.design.SettingsToggle
import plus.kite.explore.design.Palette

/** The reader's corner: account, contributions, reading preferences and what Explore offers to take away. */
@Composable
fun MeScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    val colors = Explore.colors
    var confirmingSignOut by remember { mutableStateOf(false) }
    LazyColumn(
        // The list starts below the status bar, so rows never scroll under the clock.
        modifier = Modifier.fillMaxSize().background(colors.groupedBackground).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                stringResource(R.string.tab_me),
                style = ExploreType.largeTitle,
                color = colors.label,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 8.dp),
            )
        }
        item { ProfileHeader() }
        if (app.user?.temporaryPassword == true) {
            item {
                FormSection {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { app.sheet = AppSheet.ChangePassword() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LockReset, null, tint = colors.orange)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.password_temporary_title), style = ExploreType.body.copy(fontWeight = FontWeight.SemiBold), color = colors.label)
                            Text(stringResource(R.string.password_temporary_reset), style = ExploreType.footnote, color = colors.secondaryLabel)
                        }
                    }
                }
            }
        }
        if (app.isSignedIn) {
            item {
                FormSection(stringResource(R.string.me_account)) {
                    SettingsRow(stringResource(R.string.stream_following), Icons.Rounded.Favorite, trailing = app.followedHosts.size.toString(), chevron = true) {
                        app.navigate(Route.Subscriptions)
                    }
                    RowDivider()
                    SettingsRow(stringResource(R.string.me_my_blogs), Icons.Rounded.Verified, chevron = true) { app.navigate(Route.OwnedBlogs) }
                    RowDivider()
                    SettingsRow(stringResource(R.string.account_change_name), Icons.Rounded.Badge) { app.sheet = AppSheet.EditName }
                    RowDivider()
                    SettingsRow(stringResource(R.string.account_change_password), Icons.Rounded.Key) { app.sheet = AppSheet.ChangePassword() }
                }
            }
        }
        item {
            FormSection(stringResource(R.string.me_contribute), stringResource(R.string.me_contribute_footer)) {
                SettingsRow(stringResource(R.string.submit_title), Icons.Rounded.AddBox) { app.sheet = AppSheet.Submit() }
                RowDivider()
                SettingsRow(stringResource(R.string.me_my_submissions), Icons.Rounded.Inventory, trailing = app.submissionIds.size.toString(), chevron = true) {
                    app.navigate(Route.Submissions)
                }
            }
        }
        item {
            FormSection(stringResource(R.string.me_reading), stringResource(R.string.me_reading_footer)) {
                SettingsToggle(stringResource(R.string.me_open_in_browser), Icons.Rounded.Public, app.prefs.openInBrowser) { app.prefs.openInBrowser = it }
                RowDivider()
                SettingsToggle(stringResource(R.string.me_show_transition), Icons.AutoMirrored.Rounded.ArrowForward, app.prefs.showsHandoff) { app.prefs.showsHandoff = it }
                RowDivider()
                SettingsToggle(stringResource(R.string.me_dim_read), Icons.AutoMirrored.Rounded.PlaylistAddCheck, app.prefs.dimsReadPosts) {
                    app.prefs.dimsReadPosts = it
                    if (!it) app.forgetReadPosts()
                }
            }
        }
        item {
            FormSection("Explore") {
                SettingsRow(stringResource(R.string.about_explore), Icons.Rounded.Info, chevron = true) { app.navigate(Route.About) }
                RowDivider()
                SettingsRow(stringResource(R.string.me_rss_feed), Icons.Rounded.RssFeed) { shareText(context, app.client.feedUrl) }
                RowDivider()
                SettingsRow(stringResource(R.string.me_export_every_blog), Icons.Rounded.IosShare) { shareText(context, app.client.opmlUrl) }
                RowDivider()
                SettingsRow(stringResource(R.string.me_server), Icons.Rounded.Dns, trailing = app.client.host, chevron = true) { app.navigate(Route.Server) }
            }
        }
        if (app.isSignedIn) {
            item {
                FormSection {
                    SettingsRow(stringResource(R.string.account_sign_out), color = colors.red) { confirmingSignOut = true }
                    RowDivider(inset = false)
                    SettingsRow(stringResource(R.string.account_delete), color = colors.red) { app.sheet = AppSheet.DeleteAccount }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kite ${BuildConfig.VERSION_NAME}", style = ExploreType.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.secondaryLabel)
                Text(stringResource(R.string.me_privacy_line), style = ExploreType.footnote, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            }
        }
    }
    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(R.string.account_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSignOut = false
                    app.signOut()
                }) { Text(stringResource(R.string.account_sign_out), color = colors.red) }
            },
            dismissButton = { TextButton(onClick = { confirmingSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ProfileHeader() {
    val app = LocalApp.current
    val colors = Explore.colors
    val user = app.user
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(colors.card)
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (user != null) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(colors.fill), contentAlignment = Alignment.Center) {
                Text(Palette.initial(user.displayName), style = ExploreType.largeTitle.copy(fontWeight = FontWeight.SemiBold), color = colors.secondaryLabel)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.displayName, style = ExploreType.title2, color = colors.label)
                Text(user.email, style = ExploreType.subheadline, color = colors.secondaryLabel)
            }
            if (user.isAdmin) Pill(stringResource(R.string.me_admin), Icons.Rounded.Shield)
        } else {
            MarkTile(76.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kite", style = ExploreType.title2, color = colors.label)
                Text(stringResource(R.string.me_signed_out_description), style = ExploreType.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            }
            PrimaryButton(onClick = { app.sheet = AppSheet.SignIn(SignInMode.SignIn) }, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.account_sign_in), style = ExploreType.headline)
            }
            if (app.registrationEnabled) {
                TextButton(onClick = { app.sheet = AppSheet.SignIn(SignInMode.Register) }) {
                    Text(stringResource(R.string.account_create), style = ExploreType.headline, color = colors.secondaryLabel)
                }
            }
        }
    }
}

/** Hands a link to the system share sheet. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
