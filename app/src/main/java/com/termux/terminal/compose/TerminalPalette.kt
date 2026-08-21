package com.termux.terminal.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.termux.terminal.TerminalColors

/**
 * Color palette applied to a terminal session emulator.
 *
 * Derived from the active Material color scheme so that the terminal follows the system
 * light/dark mode and Material You dynamic colors.
 *
 * @property background Terminal background color (ARGB)
 * @property foreground Default text color (ARGB)
 * @property cursor Cursor color with guaranteed contrast against [background] (ARGB)
 */
data class TerminalPalette(
    val background: Int,
    val foreground: Int,
    val cursor: Int
) {
    companion object {

        /** Backgrounds perceived darker than this threshold get a white cursor. */
        private const val CURSOR_BRIGHTNESS_THRESHOLD = 130

        /**
         * Build a palette from the current {@link MaterialTheme} color scheme.
         *
         * @return A palette using the surface/onSurface colors of the active scheme
         */
        @Composable
        fun fromTheme(): TerminalPalette {
            val background = MaterialTheme.colorScheme.surface.toArgb()
            val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
            return TerminalPalette(
                background = background,
                foreground = foreground,
                cursor = cursorColorForBackground(background)
            )
        }

        /**
         * Pick a cursor color visible on top of the given background, mirroring
         * {@link com.termux.terminal.TerminalColorScheme#setCursorColorForBackground()}.
         *
         * @param background The terminal background color
         * @return White on dark backgrounds, black on bright ones
         */
        fun cursorColorForBackground(background: Int): Int {
            val brightness = TerminalColors.getPerceivedBrightnessOfColor(background)
            return if (brightness < CURSOR_BRIGHTNESS_THRESHOLD) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
    }
}
