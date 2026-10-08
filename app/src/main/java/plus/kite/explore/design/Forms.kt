package plus.kite.explore.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A grouped section, as iOS settings and forms have them: a header, a card of rows and a footer. */
@Composable
fun FormSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Explore.colors
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        if (header != null) {
            Text(header, style = ExploreType.footnote, color = colors.secondaryLabel, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card),
            content = content,
        )
        if (footer != null) {
            Text(footer, style = ExploreType.footnote, color = colors.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

/** The square gray tile a settings row's icon sits on. */
@Composable
fun IconTile(icon: ImageVector) {
    val colors = Explore.colors
    Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(colors.fill), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(17.dp), tint = colors.label)
    }
}

/** A settings row that does something, with optional trailing text or a count and a chevron when it opens a page. */
@Composable
fun SettingsRow(
    title: String,
    icon: ImageVector? = null,
    trailing: String? = null,
    chevron: Boolean = false,
    color: Color = Explore.colors.label,
    onClick: () -> Unit,
) {
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconTile(icon)
        Text(title, style = ExploreType.body, color = color, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, style = ExploreType.body, color = colors.secondaryLabel, maxLines = 1)
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.tertiaryLabel)
    }
}

@Composable
fun SettingsToggle(title: String, icon: ImageVector?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = Explore.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) IconTile(icon)
        Text(title, style = ExploreType.body, color = if (enabled) colors.label else colors.tertiaryLabel, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
        )
    }
}

/** A row's hairline, inset past the icon. */
@Composable
fun RowDivider(inset: Boolean = true) {
    Hairline(Modifier.padding(start = if (inset) 59.dp else 16.dp))
}

/** A plain text field for a form row. */
@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    minLines: Int = 1,
    singleLine: Boolean = true,
    label: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = Explore.colors
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = ExploreType.body, color = colors.tertiaryLabel) },
        label = label?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = ExploreType.body,
        trailingIcon = trailingIcon,
        visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secure) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = !secure && keyboardType == KeyboardType.Text,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }, onSearch = { onDone?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** What went wrong with a form, in red. */
@Composable
fun FormError(message: String, modifier: Modifier = Modifier) {
    val colors = Explore.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = colors.red)
        Text(message, style = ExploreType.subheadline, color = colors.red)
    }
}

/** A sheet that opens all the way, as the iOS app's sheets do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Explore.colors.groupedBackground,
        dragHandle = null,
        content = content,
    )
}
