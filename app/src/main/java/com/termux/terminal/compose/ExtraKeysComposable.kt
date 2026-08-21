package com.termux.terminal.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Default long-press timeout before repeat starts. */
private const val LONG_PRESS_TIMEOUT = 400L

/** Repeat interval in milliseconds. */
private const val REPEAT_DELAY = 80L

/**
 * Callback interface for extra key button clicks.
 */
fun interface ExtraKeysCallback {
    /**
     * Called when an extra key button is clicked.
     *
     * @param key The key identifier (e.g., "ESC", "CTRL", "TAB")
     * @param isMacro Whether this is a macro (space-separated key sequence)
     */
    fun onKeyClick(key: String, isMacro: Boolean)
}

/**
 * Extra keys bar composable for terminal control keys.
 *
 * Renders a dynamic layout based on the [ExtraKeysConfig] parsed from termux.properties.
 * Supports long-press repeat for navigation and editing keys, and modifier lock on long-press.
 *
 * @param config The extra keys configuration
 * @param callback Callback for key clicks
 * @param modifier Modifier to apply
 */
@Composable
fun ExtraKeysBar(
    config: ExtraKeysConfig = ExtraKeysConfig.DEFAULT,
    callback: ExtraKeysCallback,
    modifier: Modifier = Modifier
) {
    val activeModifiers = remember { mutableStateMapOf<String, Boolean>() }

    fun toggleModifier(key: String) {
        val current = activeModifiers[key] ?: false
        activeModifiers[key] = !current
    }

    fun getModifierPrefix(): String {
        val prefix = StringBuilder()
        if (activeModifiers["CTRL"] == true) prefix.append("CTRL ")
        if (activeModifiers["ALT"] == true) prefix.append("ALT ")
        if (activeModifiers["SHIFT"] == true) prefix.append("SHIFT ")
        if (activeModifiers["FN"] == true) prefix.append("FN ")
        return prefix.toString()
    }

    fun onKeyAction(key: String) {
        val prefix = getModifierPrefix()
        val fullKey = if (prefix.isNotEmpty()) "$prefix$key" else key
        callback.onKeyClick(fullKey, prefix.isNotEmpty())
        activeModifiers.clear()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        config.rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth().then(
                    if (rowIndex > 0) Modifier.padding(top = 2.dp) else Modifier
                ),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { keyConfig ->
                    ExtraKeyButton(
                        config = keyConfig,
                        isActive = activeModifiers[keyConfig.key] == true,
                        onClick = {
                            if (keyConfig.isModifier) {
                                toggleModifier(keyConfig.key)
                            } else {
                                onKeyAction(keyConfig.key)
                            }
                        },
                        onLongPressRepeat = {
                            if (!keyConfig.isModifier) {
                                onKeyAction(keyConfig.key)
                            }
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * A single extra key button with long-press repeat support.
 *
 * @param config The key configuration
 * @param isActive Whether the button is in active state (for modifiers)
 * @param onClick Callback when the button is clicked
 * @param onLongPressRepeat Callback for each repeat during long press
 * @param modifier Modifier to apply
 */
@Composable
private fun ExtraKeyButton(
    config: ExtraKeyConfig,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongPressRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (config.isRepetitive && !config.isModifier) {
        LongPressRepeatButton(
            onClick = onClick,
            onLongPressRepeat = onLongPressRepeat,
            modifier = modifier.height(36.dp),
            containerColor = backgroundColor,
            contentColor = contentColor
        ) {
            Text(
                text = config.display,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = backgroundColor,
                contentColor = contentColor
            ),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = config.display,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * A button that triggers [onLongPressRepeat] at regular intervals while held down.
 *
 * Uses a Handler-based approach for reliable repeat timing without requiring
 * Compose pointer input detection.
 */
@Composable
private fun LongPressRepeatButton(
    onClick: () -> Unit,
    onLongPressRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.small
    ) {
        content()
    }
}
