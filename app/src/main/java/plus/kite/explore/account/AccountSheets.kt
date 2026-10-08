package plus.kite.explore.account

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import plus.kite.explore.R
import plus.kite.explore.app.LocalApp
import plus.kite.explore.app.ReportTarget
import plus.kite.explore.app.SignInMode
import plus.kite.explore.data.ApiError
import plus.kite.explore.data.BlogRef
import plus.kite.explore.data.ClaimChallenge
import plus.kite.explore.design.BlogAvatar
import plus.kite.explore.design.Explore
import plus.kite.explore.design.ExploreSheet
import plus.kite.explore.design.ExploreType
import plus.kite.explore.design.FormError
import plus.kite.explore.design.FormField
import plus.kite.explore.design.FormSection
import plus.kite.explore.design.Hairline
import plus.kite.explore.design.MarkTile
import plus.kite.explore.design.PrimaryButton
import plus.kite.explore.design.RowDivider
import plus.kite.explore.design.SettingsRow
import plus.kite.explore.design.SheetBar

/** A spinner sized for a button's label. */
@Composable
fun ButtonSpinner() {
    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Explore.colors.background)
}

/**
 * Sign in with an Explore account, or create one. Explore asks for nothing beyond an email,
 * a name and a password. A password an admin reset turns the sheet into Change Password.
 */
@Composable
fun SignInSheet(initial: SignInMode, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(initial) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showsPassword by remember { mutableStateOf(false) }
    var mustChangePassword by remember { mutableStateOf(false) }
    val registering = mode == SignInMode.Register

    if (mustChangePassword) {
        ChangePasswordSheet(signedInWith = password, onDismiss = onDismiss)
        return
    }

    fun validate(): String? {
        if (registering && (name.trim().isEmpty() || name.trim().length > 80)) return app.string(R.string.sign_in_error_name)
        val trimmed = email.trim()
        if (trimmed.isEmpty() || '@' !in trimmed || '.' !in trimmed) return app.string(R.string.sign_in_error_email)
        if (password.isEmpty()) return app.string(R.string.sign_in_error_password)
        if (registering) {
            val bytes = password.encodeToByteArray().size
            if (bytes < 12) return app.string(R.string.password_error_short)
            if (bytes > 72) return app.string(R.string.password_error_long)
            if (password != confirmation) return app.string(R.string.password_error_mismatch)
        }
        return null
    }

    fun submit() {
        if (working) return
        validate()?.let {
            error = it
            return
        }
        working = true
        error = null
        scope.launch {
            try {
                if (registering) {
                    app.register(email.trim(), password, name.trim())
                    app.showToast(app.string(R.string.toast_welcome, name.trim()), Icons.Rounded.Celebration)
                } else {
                    app.signIn(email.trim(), password)
                    if (app.user?.temporaryPassword == true) {
                        mustChangePassword = true
                        return@launch
                    }
                    app.showToast(app.string(R.string.toast_signed_in), Icons.Rounded.HowToReg)
                }
                onDismiss()
            } catch (failure: Exception) {
                error = when ((failure as? ApiError)?.code) {
                    "conflict" -> app.string(R.string.sign_in_error_exists)
                    "feature_disabled" -> app.string(R.string.sign_in_error_closed)
                    "invalid_request" -> if (registering) app.string(R.string.sign_in_error_check) else app.message(failure)
                    else -> app.message(failure)
                }
            } finally {
                working = false
            }
        }
    }

    ExploreSheet(onDismiss) {
        SheetBar("", onDismiss)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MarkTile(84.dp)
                Text(stringResource(if (registering) R.string.sign_in_create_title else R.string.sign_in_welcome_back), style = ExploreType.title, color = colors.label)
                Text(stringResource(R.string.sign_in_tagline), style = ExploreType.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            }
            if (app.registrationEnabled) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SignInMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = {
                                mode = option
                                error = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            icon = {},
                        ) { Text(stringResource(if (option == SignInMode.SignIn) R.string.account_sign_in else R.string.account_create)) }
                    }
                }
            }
            FormSection {
                AnimatedVisibility(registering) {
                    Column {
                        FormField(name, { name = it }, stringResource(R.string.sign_in_name_prompt))
                        RowDivider(inset = false)
                    }
                }
                FormField(email, { email = it }, stringResource(R.string.sign_in_email), keyboardType = KeyboardType.Email)
                RowDivider(inset = false)
                FormField(
                    password, { password = it }, stringResource(R.string.sign_in_password),
                    secure = !showsPassword,
                    imeAction = if (registering) ImeAction.Next else ImeAction.Go,
                    onDone = { if (!registering) submit() },
                    trailingIcon = {
                        IconButton(onClick = { showsPassword = !showsPassword }) {
                            Icon(
                                if (showsPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                stringResource(if (showsPassword) R.string.sign_in_hide_password else R.string.sign_in_show_password),
                                tint = colors.secondaryLabel,
                            )
                        }
                    },
                )
                AnimatedVisibility(registering) {
                    Column {
                        RowDivider(inset = false)
                        FormField(confirmation, { confirmation = it }, stringResource(R.string.sign_in_confirm_password), secure = true, imeAction = ImeAction.Go, onDone = ::submit)
                    }
                }
            }
            if (registering) {
                Text(stringResource(R.string.password_rule), style = ExploreType.footnote, color = colors.secondaryLabel, modifier = Modifier.fillMaxWidth())
            }
            error?.let { FormError(it) }
            PrimaryButton(onClick = ::submit, modifier = Modifier.fillMaxWidth(), enabled = !working, large = true) {
                if (working) ButtonSpinner() else Text(stringResource(if (registering) R.string.account_create else R.string.account_sign_in), style = ExploreType.headline)
            }
            Text(stringResource(R.string.sign_in_privacy), style = ExploreType.footnote, color = colors.secondaryLabel, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun EditNameSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(app.user?.displayName.orEmpty()) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmed = name.trim()
    fun save() {
        if (trimmed.isEmpty() || trimmed.length > 80 || working) return
        working = true
        scope.launch {
            try {
                app.updateName(trimmed)
                app.showToast(app.string(R.string.toast_name_updated))
                onDismiss()
            } catch (failure: Exception) {
                app.handleAuthError(failure)
                error = app.message(failure)
            } finally {
                working = false
            }
        }
    }
    ExploreSheet(onDismiss) {
        SheetBar(stringResource(R.string.account_change_name), onDismiss) {
            TextButton(onClick = ::save, enabled = trimmed.isNotEmpty() && trimmed.length <= 80 && !working) { Text(stringResource(R.string.action_save)) }
        }
        Column(Modifier.imePadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormSection(footer = stringResource(R.string.account_name_footer)) {
                FormField(name, { name = it }, stringResource(R.string.account_name), imeAction = ImeAction.Done, onDone = ::save)
            }
            error?.let { FormError(it) }
        }
    }
}

/**
 * Changes the password. Right after signing in with a password an admin reset, that password
 * is filled in, so the reader only chooses the new one.
 */
@Composable
fun ChangePasswordSheet(signedInWith: String? = null, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(signedInWith.orEmpty()) }
    var new by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val temporary = app.user?.temporaryPassword == true
    fun save() {
        val bytes = new.encodeToByteArray().size
        error = when {
            bytes < 12 -> app.string(R.string.password_error_short)
            bytes > 72 -> app.string(R.string.password_error_long)
            new != confirmation -> app.string(R.string.password_error_mismatch)
            else -> null
        }
        if (error != null || working) return
        working = true
        scope.launch {
            try {
                app.changePassword(current, new)
                app.showToast(app.string(R.string.toast_password_changed), Icons.Rounded.Key)
                onDismiss()
            } catch (failure: Exception) {
                app.handleAuthError(failure)
                error = if ((failure as? ApiError)?.code == "wrong_password") app.string(R.string.password_error_wrong) else app.message(failure)
            } finally {
                working = false
            }
        }
    }
    ExploreSheet(onDismiss) {
        SheetBar(stringResource(R.string.account_change_password), onDismiss) {
            TextButton(onClick = ::save, enabled = current.isNotEmpty() && new.isNotEmpty() && !working) { Text(stringResource(R.string.action_save)) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (temporary) {
                FormSection {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LockReset, null, tint = colors.orange)
                        Text(stringResource(R.string.password_temporary_reset), style = ExploreType.subheadline, color = colors.label)
                    }
                }
            }
            if (signedInWith == null) {
                FormSection {
                    FormField(current, { current = it }, stringResource(if (temporary) R.string.password_temporary else R.string.password_current), secure = true)
                }
            }
            FormSection(footer = stringResource(R.string.password_change_footer)) {
                FormField(new, { new = it }, stringResource(R.string.password_new), secure = true)
                RowDivider(inset = false)
                FormField(confirmation, { confirmation = it }, stringResource(R.string.password_confirm_new), secure = true, imeAction = ImeAction.Done, onDone = ::save)
            }
            error?.let { FormError(it) }
        }
    }
}

@Composable
fun DeleteAccountSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    val email = app.user?.email.orEmpty()
    var typed by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ExploreSheet(onDismiss) {
        SheetBar(stringResource(R.string.account_delete), onDismiss)
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FormSection {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.NoAccounts, null, Modifier.size(40.dp), tint = colors.red)
                    Text(stringResource(R.string.account_delete_explanation), style = ExploreType.subheadline, color = colors.label)
                }
            }
            FormSection(stringResource(R.string.account_delete_type_email)) {
                FormField(typed, { typed = it }, email, keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
            }
            error?.let { FormError(it) }
            FormSection {
                SettingsRow(stringResource(R.string.account_delete), color = if (typed.trim().equals(email, ignoreCase = true) && !working) colors.red else colors.tertiaryLabel) {
                    if (!typed.trim().equals(email, ignoreCase = true) || working) return@SettingsRow
                    working = true
                    scope.launch {
                        try {
                            app.deleteAccount()
                            app.showToast(app.string(R.string.toast_account_deleted))
                            onDismiss()
                        } catch (failure: Exception) {
                            app.handleAuthError(failure)
                            error = app.message(failure)
                        } finally {
                            working = false
                        }
                    }
                }
            }
        }
    }
}

/** Reports a blog or a post to Explore's maintainers. */
@Composable
fun ReportSheet(target: ReportTarget, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmed = reason.trim()
    fun send() {
        if (trimmed.length !in 5..1000 || working) return
        working = true
        error = null
        scope.launch {
            try {
                app.client.report(target.blogHost, target.entryId, trimmed)
                app.showToast(app.string(R.string.toast_report_sent), Icons.Rounded.Flag)
                onDismiss()
            } catch (failure: Exception) {
                app.handleAuthError(failure)
                error = app.message(failure)
            } finally {
                working = false
            }
        }
    }
    ExploreSheet(onDismiss) {
        SheetBar(stringResource(R.string.action_report), onDismiss) {
            TextButton(onClick = ::send, enabled = trimmed.length in 5..1000 && !working) { Text(stringResource(R.string.action_send)) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FormSection(stringResource(if (target.entryId == null) R.string.report_blog else R.string.report_post)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    target.entryTitle?.let { Text(it, style = ExploreType.headline, color = colors.label) }
                    Text(
                        target.blogName,
                        style = if (target.entryTitle == null) ExploreType.headline else ExploreType.subheadline,
                        color = if (target.entryTitle == null) colors.label else colors.secondaryLabel,
                    )
                    Text(target.blogHost, style = ExploreType.footnote, color = colors.secondaryLabel)
                }
            }
            FormSection(stringResource(R.string.report_what_happened), stringResource(R.string.report_footer)) {
                FormField(reason, { reason = it }, stringResource(R.string.report_prompt), singleLine = false, minLines = 4, imeAction = ImeAction.Default)
            }
            error?.let { FormError(it) }
        }
    }
}

/** Claims a blog by publishing a DNS TXT record Explore asks for. */
@Composable
fun ClaimSheet(blog: BlogRef, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val colors = Explore.colors
    val scope = rememberCoroutineScope()
    var challenge by remember { mutableStateOf<ClaimChallenge?>(null) }
    var expiresAt by remember { mutableLongStateOf(0L) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var claimed by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(challenge) {
        while (challenge != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    fun message(failure: Exception): String = when ((failure as? ApiError)?.code) {
        "verification_failed" -> app.string(R.string.claim_error_not_found)
        "conflict" -> app.string(R.string.claim_error_claimed)
        "not_found" -> app.string(R.string.claim_error_expired)
        else -> app.message(failure)
    }
    fun start() {
        working = true
        error = null
        scope.launch {
            try {
                val next = app.client.startClaim(blog.host)
                challenge = next
                expiresAt = System.currentTimeMillis() + next.expiresInSeconds * 1000L
            } catch (failure: Exception) {
                app.handleAuthError(failure)
                error = message(failure)
            } finally {
                working = false
            }
        }
    }
    fun verify() {
        working = true
        error = null
        scope.launch {
            try {
                app.client.verifyClaim(blog.host)
                claimed = true
            } catch (failure: Exception) {
                app.handleAuthError(failure)
                error = message(failure)
            } finally {
                working = false
            }
        }
    }
    ExploreSheet(onDismiss) {
        SheetBar(stringResource(R.string.claim_title), onDismiss)
        if (claimed) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(Icons.Rounded.Verified, null, Modifier.size(64.dp), tint = colors.teal)
                Text(stringResource(R.string.claim_done_title), style = ExploreType.title, color = colors.label)
                Text(stringResource(R.string.claim_done_description, blog.name), style = ExploreType.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
                PrimaryButton(onClick = onDismiss, large = true) { Text(stringResource(R.string.action_done)) }
            }
            return@ExploreSheet
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FormSection {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        BlogAvatar(blog.host, blog.name, 48.dp)
                        Column {
                            Text(blog.name, style = ExploreType.headline, color = colors.label)
                            Text(blog.host, style = ExploreType.subheadline, color = colors.secondaryLabel)
                        }
                    }
                    Text(stringResource(R.string.claim_explanation), style = ExploreType.subheadline, color = colors.secondaryLabel)
                }
            }
            val current = challenge
            if (current != null) {
                val remaining = ((expiresAt - now) / 1000).coerceAtLeast(0)
                FormSection(
                    stringResource(R.string.claim_add_record),
                    if (remaining > 0) stringResource(R.string.claim_valid_for, "%d:%02d".format(remaining / 60, remaining % 60)) else stringResource(R.string.claim_expired),
                ) {
                    CopyRow(stringResource(R.string.claim_type), "TXT", copyable = false)
                    Hairline(Modifier.padding(start = 16.dp))
                    CopyRow(stringResource(R.string.claim_name), current.record)
                    Hairline(Modifier.padding(start = 16.dp))
                    CopyRow(stringResource(R.string.claim_value), current.value)
                }
                FormSection {
                    SettingsRow(stringResource(R.string.claim_verify), color = if (working) colors.tertiaryLabel else colors.accent) { if (!working) verify() }
                    RowDivider(inset = false)
                    SettingsRow(stringResource(R.string.claim_new_record), color = if (working) colors.tertiaryLabel else colors.accent) { if (!working) start() }
                }
            } else {
                FormSection {
                    SettingsRow(stringResource(R.string.claim_create_record), color = if (working) colors.tertiaryLabel else colors.accent) { if (!working) start() }
                }
            }
            error?.let { FormError(it) }
        }
    }
}

/** A value with a button that copies it. */
@Composable
private fun CopyRow(title: String, value: String, copyable: Boolean = true) {
    val app = LocalApp.current
    val context = LocalContext.current
    val colors = Explore.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ExploreType.caption, color = colors.secondaryLabel)
            Text(value, style = ExploreType.callout.copy(fontFamily = FontFamily.Monospace), color = colors.label)
        }
        if (copyable) {
            IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(title, value))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) app.showToast(app.string(R.string.toast_copied), Icons.Rounded.ContentCopy)
            }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy), tint = colors.accent) }
        }
    }
}
