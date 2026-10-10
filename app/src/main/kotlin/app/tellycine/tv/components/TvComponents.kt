package app.tellycine.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedIconButton
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import app.tellycine.ui.components.formatTime
import kotlinx.coroutines.delay

/** Room around screen content: TVs may crop the edges of the picture (overscan). */
object TvSpacing {
    val screenEnd = 48.dp
    val screenTop = 28.dp
    val screenBottom = 24.dp
    val screenStart = 24.dp
    val content = PaddingValues(start = screenStart, end = screenEnd, top = 8.dp, bottom = screenBottom)
}

/** Asks for focus once [target] is on screen; safe to call before it is attached. */
@Composable
fun RequestFocusOnStart(target: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        // Wait a frame or two so the target has been laid out.
        repeat(3) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

/** The current time, updated every minute, for screen headers. */
@Composable
fun TvClock(modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    Text(
        formatTime(now),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Screen title row: title and optional subtitle on the left, actions and the clock on the right. */
@Composable
fun TvHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = TvSpacing.screenStart, end = TvSpacing.screenEnd, top = TvSpacing.screenTop, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
        TvClock()
    }
}

/** A compact button with an optional icon, for header and panel actions. */
@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    /** For a [primary] button, its colour while it isn't focused, and its text's. */
    accent: Pair<Color, Color>? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    if (primary) {
        val colors = accent?.let { (container, text) -> ButtonDefaults.colors(containerColor = container, contentColor = text) } ?: ButtonDefaults.colors()
        Button(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding, colors = colors, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding, content = content)
    }
}

/** An icon-only version of [TvActionButton], where space is short; [label] is read out and searchable. */
@Composable
fun TvIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedIconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, label, Modifier.size(20.dp))
    }
}

@Composable
fun TvEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val action = remember { FocusRequester() }
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.size(72.dp),
            ) {
                Icon(icon, null, Modifier.align(Alignment.Center).size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp).focusRequester(action)) { Text(actionLabel) }
            }
        }
    }
}

/** A panel sliding over the right of the screen, for options and details. Back closes it. */
@Composable
fun TvSidePanel(
    onDismiss: () -> Unit,
    width: Dp = 460.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().ignoreStrayOkRelease(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.width(width).fillMaxHeight(),
            ) {
                Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 40.dp, top = 28.dp, bottom = 20.dp), content = content)
            }
        }
    }
}

/** A centred dialog with a title, free content and a row of buttons. */
@Composable
fun TvDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    width: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(width).ignoreStrayOkRelease(),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), content = buttons)
            }
        }
    }
}

/** Yes/no confirmation; focus starts on the safe choice. */
@Composable
fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    val cancel = remember { FocusRequester() }
    TvDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancel)) { Text(dismissLabel) }
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    RequestFocusOnStart(cancel)
}

/** Shows a message with a single OK button. */
@Composable
fun TvMessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    val ok = remember { FocusRequester() }
    TvDialog(title = title, onDismiss = onDismiss, buttons = {
        Button(onClick = onDismiss, modifier = Modifier.focusRequester(ok)) { Text("OK") }
    }) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    RequestFocusOnStart(ok)
}

/** Pick one of [options]; the current choice is focused first. */
@Composable
fun <T> TvChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    supporting: (T) -> String? = { null },
) {
    val focus = remember { FocusRequester() }
    TvDialog(title = title, onDismiss = onDismiss, buttons = {
        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
    }) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            val sub = supporting(value)
            ListItem(
                selected = isSelected,
                onClick = { onSelect(value) },
                headlineContent = { Text(label) },
                supportingContent = sub?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                modifier = if (isSelected || (selected == null && index == 0)) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
    RequestFocusOnStart(focus)
}

/** A settings or form row: title, current value underneath, opens something when clicked. */
@Composable
fun TvValueRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier,
    )
}

/** A row with a switch; the whole row toggles. */
@Composable
fun TvSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvValueRow(
        title = title,
        value = summary,
        onClick = { onChange(!checked) },
        modifier = modifier,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/** Section label in settings-style lists. */
@Composable
fun TvSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** Shows a link as a QR code, so it can be opened or copied on a phone. */
@Composable
fun TvQrDialog(title: String, url: String, onDismiss: () -> Unit, note: String? = null) {
    val done = remember { FocusRequester() }
    TvDialog(title = title, onDismiss = onDismiss, width = 760.dp, buttons = {
        Button(onClick = onDismiss, modifier = Modifier.focusRequester(done)) { Text("Done") }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            QrCode(url, size = 220.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                Text("Scan with your phone's camera to open or copy it.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    RequestFocusOnStart(done)
}
