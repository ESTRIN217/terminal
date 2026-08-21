package com.termux.terminal.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.termux.R
import com.termux.shared.data.DataUtils
import com.termux.shared.interact.ShareUtils
import com.termux.shared.shell.ShellUtils
import com.termux.shared.termux.data.TermuxUrlUtils
import com.termux.terminal.TerminalSession

/**
 * The "more options" (overflow) menu for the terminal screen, mirroring the context menu shown by
 * the overflow button of the classic {@link com.termux.app.TermuxActivity}.
 *
 * @param session The active terminal session the actions operate on, or null when no session exists
 * @param isKeepScreenOnEnabled Whether the keep-screen-on preference is currently enabled
 * @param onSetKeepScreenOn Callback to persist and apply a new keep-screen-on state
 * @param onOpenHelp Callback to open the help activity
 * @param onOpenSettings Callback to open the settings activity
 * @param modifier Modifier to apply to the anchor icon button
 */
@Composable
fun MoreOptionsMenu(
    session: TerminalSession?,
    isKeepScreenOnEnabled: Boolean,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showKillConfirmDialog by remember { mutableStateOf(false) }
    var showNoUrlsFoundDialog by remember { mutableStateOf(false) }
    var urlSelection by remember { mutableStateOf<List<String>?>(null) }

    val view = TerminalViewRegistry.activeView
    val selectedText = view?.storedSelectedText

    IconButton(onClick = { menuExpanded = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More Options",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }

    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(context.getString(R.string.action_select_url)) },
            onClick = {
                menuExpanded = false
                if (session != null) {
                    val text = ShellUtils.getTerminalSessionTranscriptText(session, true, true)
                    val urls = TermuxUrlUtils.extractUrls(text)
                        .mapNotNull { it?.toString() }
                        .toTypedArray()
                    // Latest first.
                    urls.reverse()
                    if (urls.isEmpty()) {
                        showNoUrlsFoundDialog = true
                    } else {
                        urlSelection = urls.toList()
                    }
                }
            }
        )
        DropdownMenuItem(
            text = { Text(context.getString(R.string.action_share_transcript)) },
            onClick = {
                menuExpanded = false
                if (session != null) {
                    var transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true)
                    if (transcriptText == null) return@DropdownMenuItem

                    // See https://github.com/termux/termux-app/issues/1166.
                    transcriptText = DataUtils.getTruncatedCommandOutput(
                        transcriptText, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, true, false
                    ).trim()
                    ShareUtils.shareText(
                        context, context.getString(R.string.title_share_transcript),
                        transcriptText, context.getString(R.string.title_share_transcript_with)
                    )
                }
            }
        )
        if (!selectedText.isNullOrEmpty()) {
            DropdownMenuItem(
                text = { Text(context.getString(R.string.action_share_selected_text)) },
                onClick = {
                    menuExpanded = false
                    ShareUtils.shareText(
                        context, context.getString(R.string.title_share_selected_text),
                        selectedText, context.getString(R.string.title_share_selected_text_with)
                    )
                }
            )
        }
        if (view?.isAutoFillEnabled == true) {
            DropdownMenuItem(
                text = { Text(context.getString(R.string.action_autofill_username)) },
                onClick = {
                    menuExpanded = false
                    view.requestAutoFillUsername()
                }
            )
            DropdownMenuItem(
                text = { Text(context.getString(R.string.action_autofill_password)) },
                onClick = {
                    menuExpanded = false
                    view.requestAutoFillPassword()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(context.getString(R.string.action_reset_terminal)) },
            onClick = {
                menuExpanded = false
                session?.reset()
                Toast.makeText(context, R.string.msg_terminal_reset, Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (session != null) context.getString(R.string.action_kill_process, session.pid)
                    else context.getString(R.string.action_kill_process, 0),
                    color = if (session?.isRunning == true) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            },
            onClick = {
                menuExpanded = false
                if (session?.isRunning == true) showKillConfirmDialog = true
            }
        )
        DropdownMenuItem(
            text = { Text(context.getString(R.string.action_toggle_keep_screen_on)) },
            trailingIcon = {
                Text(if (isKeepScreenOnEnabled) "\u2713" else "")
            },
            onClick = {
                menuExpanded = false
                onSetKeepScreenOn(!isKeepScreenOnEnabled)
            }
        )
        DropdownMenuItem(
            text = { Text(context.getString(R.string.action_open_help)) },
            onClick = {
                menuExpanded = false
                onOpenHelp()
            }
        )
        DropdownMenuItem(
            text = { Text(context.getString(R.string.action_open_settings)) },
            onClick = {
                menuExpanded = false
                onOpenSettings()
            }
        )
    }

    if (showNoUrlsFoundDialog) {
        AlertDialog(
            onDismissRequest = { showNoUrlsFoundDialog = false },
            confirmButton = {},
            text = { Text(context.getString(R.string.title_select_url_none_found)) }
        )
    }

    urlSelection?.let { urls ->
        UrlSelectionDialog(urls = urls, onDismiss = { urlSelection = null })
    }

    if (showKillConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showKillConfirmDialog = false },
            title = { Text(context.getString(R.string.title_confirm_kill_process)) },
            confirmButton = {
                TextButton(onClick = {
                    showKillConfirmDialog = false
                    session?.finishIfRunning()
                }) {
                    Text(context.getString(com.termux.shared.R.string.action_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirmDialog = false }) {
                    Text(context.getString(com.termux.shared.R.string.action_no))
                }
            }
        )
    }
}

/**
 * Dialog listing URLs extracted from the terminal transcript. Clicking an entry copies it to the
 * clipboard; long pressing it opens it with the default app.
 *
 * @param urls The URLs found in the transcript, latest first
 * @param onDismiss Callback when the dialog is dismissed
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UrlSelectionDialog(
    urls: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(context.getString(R.string.title_select_url_dialog)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                urls.forEachIndexed { index, url ->
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .combinedClickable(
                                onClick = {
                                    ShareUtils.copyTextToClipboard(
                                        context, url,
                                        context.getString(R.string.msg_select_url_copied_to_clipboard)
                                    )
                                    onDismiss()
                                },
                                onLongClick = {
                                    ShareUtils.openUrl(context, url)
                                    onDismiss()
                                }
                            )
                    )
                }
            }
        }
    )
}
