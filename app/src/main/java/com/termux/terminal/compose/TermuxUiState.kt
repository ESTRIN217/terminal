package com.termux.terminal.compose

import com.termux.terminal.TerminalSession

/**
 * UI state for the Termux main screen.
 *
 * @param sessions List of active terminal sessions
 * @param activeSessionIndex Index of the currently active session
 * @param isDrawerOpen Whether the navigation drawer is open
 * @param isExtraKeysVisible Whether the extra keys bar is visible
 * @param isSoftKeyboardVisible Whether the soft keyboard is visible
 * @param fontSize Font size for the terminal, in density-independent pixels
 */
data class TermuxUiState(
    val sessions: List<TerminalSessionUiModel> = emptyList(),
    val activeSessionIndex: Int = 0,
    val isDrawerOpen: Boolean = false,
    val isExtraKeysVisible: Boolean = true,
    val isSoftKeyboardVisible: Boolean = false,
    val fontSize: Float = 14f,
    val extraKeysConfig: ExtraKeysConfig = ExtraKeysConfig(rows = emptyList())
) {
    /**
     * Get the currently active session, or null if no sessions exist.
     */
    val activeSession: TerminalSession?
        get() = sessions.getOrNull(activeSessionIndex)?.session

    /**
     * Whether there are any sessions.
     */
    val hasSessions: Boolean
        get() = sessions.isNotEmpty()
}

/**
 * UI model for a terminal session.
 *
 * @param session The underlying terminal session
 * @param name Display name for the session
 * @param title Current terminal title
 */
data class TerminalSessionUiModel(
    val session: TerminalSession,
    val name: String,
    val title: String = ""
)
