package app.tellycine.ui.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tellycine.data.db.DownloadEntity
import app.tellycine.data.db.DownloadStatus

/** 0–1 progress, or null while the size is unknown. */
fun DownloadEntity.fraction(): Float? =
    if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal).coerceIn(0f, 1f) else null

fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0))

/** One line describing where a download is up to. [fileOk] is false when a finished file has gone missing. */
fun downloadStatus(context: Context, d: DownloadEntity, fileOk: Boolean): String {
    val percent = d.fraction()?.let { "${(it * 100).toInt()}%" }
    return when (d.status) {
        DownloadStatus.QUEUED -> "Waiting to download"
        DownloadStatus.RUNNING -> listOfNotNull(
            "Downloading",
            percent,
            if (d.bytesTotal > 0) "${formatBytes(context, d.bytesDownloaded)} of ${formatBytes(context, d.bytesTotal)}"
            else formatBytes(context, d.bytesDownloaded),
        ).joinToString(" · ")
        DownloadStatus.PAUSED -> listOfNotNull(d.error ?: "Paused", percent).joinToString(" · ")
        DownloadStatus.COMPLETED ->
            if (fileOk) "Downloaded · ${formatBytes(context, d.bytesTotal)}" else "The downloaded file is missing. Download it again."
        DownloadStatus.FAILED -> "Download failed. ${d.error ?: ""}".trim()
    }
}

/**
 * Download control for a movie or episode: download, progress with cancel, done (tap to delete),
 * or failed (tap to retry). Cancel and delete ask first.
 */
@Composable
fun DownloadButton(
    download: DownloadEntity?,
    fileOk: Boolean,
    onDownload: () -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onRemove: (DownloadEntity) -> Unit,
    filled: Boolean = false,
) {
    var confirm by remember { mutableStateOf(false) }
    val failed = download != null && (download.status == DownloadStatus.FAILED || (download.status == DownloadStatus.COMPLETED && !fileOk))
    val onClick: () -> Unit = {
        when {
            download == null -> onDownload()
            failed -> onRetry(download)
            else -> {
                confirm = true
            }
        }
    }
    val content = @Composable {
        when {
            download == null -> Icon(Icons.Outlined.Download, "Download")
            failed -> Icon(Icons.Filled.ErrorOutline, "Download failed, retry", tint = MaterialTheme.colorScheme.error)
            download.status == DownloadStatus.COMPLETED -> Icon(Icons.Filled.DownloadDone, "Downloaded", tint = MaterialTheme.colorScheme.primary)
            else -> Box(contentAlignment = Alignment.Center) {
                val fraction = download.fraction()
                if (fraction != null && download.status == DownloadStatus.RUNNING) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                Icon(Icons.Filled.Close, "Cancel download", Modifier.size(16.dp))
            }
        }
    }
    if (filled) FilledTonalIconButton(onClick = onClick) { content() } else IconButton(onClick = onClick) { content() }

    if (confirm && download != null) {
        val finished = download.status == DownloadStatus.COMPLETED
        RemoveDownloadDialog(
            title = if (finished) "Delete download?" else "Cancel download?",
            message = if (finished) "${label(download)} will be removed from this device. You can still stream it."
            else "${label(download)} will stop downloading.",
            confirmLabel = if (finished) "Delete" else "Cancel download",
            onConfirm = {
                confirm = false
                onRemove(download)
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun RemoveDownloadDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

private fun label(d: DownloadEntity) = listOfNotNull(d.title, d.subtitle).joinToString(" · ")
