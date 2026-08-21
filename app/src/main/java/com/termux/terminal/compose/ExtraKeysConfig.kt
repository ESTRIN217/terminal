package com.termux.terminal.compose

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parsed extra keys configuration from termux.properties.
 *
 * @param rows The rows of extra key buttons
 */
data class ExtraKeysConfig(
    val rows: List<List<ExtraKeyConfig>>
) {
    companion object {
        /** Empty extra keys configuration (fallback when parsing fails). */
        val EMPTY = ExtraKeysConfig(rows = emptyList())

        /** Default extra keys configuration. */
        val DEFAULT = parse(
            "[[\"ESC\",\"/\",{\"key\":\"-\",\"popup\":\"|\"},\"HOME\",\"UP\",\"END\",\"PGUP\"]," +
            "[\"TAB\",\"CTRL\",\"ALT\",\"LEFT\",\"DOWN\",\"RIGHT\",\"PGDN\"]]"
        )

        /** Keys that support long-press repeat. */
        val REPETITIVE_KEYS = setOf(
            "UP", "DOWN", "LEFT", "RIGHT",
            "BKSP", "DEL",
            "PGUP", "PGDN"
        )

        /** Display text mapping for common keys. */
        private val DISPLAY_MAP = mapOf(
            "ESC" to "ESC",
            "TAB" to "TAB",
            "ENTER" to "ENTER",
            "BKSP" to "BKSP",
            "DEL" to "DEL",
            "SPACE" to "SPC",
            "HOME" to "HOME",
            "END" to "END",
            "PGUP" to "PGU",
            "PGDN" to "PGD",
            "INS" to "INS",
            "UP" to "\u2191",
            "DOWN" to "\u2193",
            "LEFT" to "\u2190",
            "RIGHT" to "\u2192",
            "CTRL" to "CTRL",
            "ALT" to "ALT",
            "SHIFT" to "SHIFT",
            "FN" to "FN",
            "F1" to "F1",
            "F2" to "F2",
            "F3" to "F3",
            "F4" to "F4",
            "F5" to "F5",
            "F6" to "F6",
            "F7" to "F7",
            "F8" to "F8",
            "F9" to "F9",
            "F10" to "F10",
            "F11" to "F11",
            "F12" to "F12"
        )

        /**
         * Parse an extra keys JSON string into a config.
         *
         * @param jsonString The JSON string from termux.properties
         * @return The parsed config, or a partial config / [EMPTY] on parse error
         */
        fun parse(jsonString: String): ExtraKeysConfig {
            val rows = mutableListOf<List<ExtraKeyConfig>>()
            return try {
                val outerArray = JSONArray(jsonString)

                for (i in 0 until outerArray.length()) {
                    val rowArray = outerArray.getJSONArray(i)
                    val row = mutableListOf<ExtraKeyConfig>()

                    for (j in 0 until rowArray.length()) {
                        val element = rowArray.get(j)
                        val config = parseKeyElement(element)
                        if (config != null) {
                            row.add(config)
                        }
                    }

                    if (row.isNotEmpty()) {
                        rows.add(row)
                    }
                }

                ExtraKeysConfig(rows)
            } catch (e: Exception) {
                if (rows.isNotEmpty()) ExtraKeysConfig(rows) else ExtraKeysConfig(rows = emptyList())
            }
        }

        private fun parseKeyElement(element: Any?): ExtraKeyConfig? {
            return when (element) {
                is String -> ExtraKeyConfig(
                    key = resolveAlias(element),
                    display = DISPLAY_MAP[resolveAlias(element)] ?: element,
                    isMacro = false
                )
                is JSONObject -> {
                    val key = element.optString("key", "")
                    val macro = element.optString("macro", "")
                    val display = element.optString("display", "")
                    val popupElement = element.opt("popup")

                    val actualKey = when {
                        key.isNotEmpty() -> resolveAlias(key)
                        macro.isNotEmpty() -> macro
                        else -> return null
                    }

                    val actualDisplay = display.ifEmpty {
                        when {
                            key.isNotEmpty() -> DISPLAY_MAP[resolveAlias(key)] ?: key
                            macro.isNotEmpty() -> macro.split(" ").firstOrNull()?.let { 
                                DISPLAY_MAP[resolveAlias(it)] ?: it 
                            } ?: macro
                            else -> actualKey
                        }
                    }

                    val popup = if (popupElement != null && popupElement != org.json.JSONObject.NULL) {
                        parseKeyElement(popupElement)
                    } else null

                    ExtraKeyConfig(
                        key = actualKey,
                        display = actualDisplay,
                        isMacro = macro != null,
                        popup = popup
                    )
                }
                else -> null
            }
        }

        private fun resolveAlias(key: String): String {
            return when (key.uppercase()) {
                "ESCAPE" -> "ESC"
                "CONTROL" -> "CTRL"
                "FUNCTION" -> "FN"
                "RETURN" -> "ENTER"
                "DELETE" -> "DEL"
                "BACKSPACE" -> "BKSP"
                "PAGEUP", "PAGE_UP", "PAGE-UP" -> "PGUP"
                "PAGEDOWN", "PAGE_DOWN", "PAGE-DOWN" -> "PGDN"
                "LT" -> "LEFT"
                "RT" -> "RIGHT"
                "DN" -> "DOWN"
                else -> key
            }
        }
    }
}

/**
 * Configuration for a single extra key button.
 *
 * @param key The key identifier sent to the terminal
 * @param display The text to display on the button
 * @param isMacro Whether this is a macro (space-separated key sequence)
 * @param popup Optional popup configuration (triggered by swipe up)
 */
data class ExtraKeyConfig(
    val key: String,
    val display: String,
    val isMacro: Boolean = false,
    val popup: ExtraKeyConfig? = null
) {
    val isRepetitive: Boolean
        get() = key in ExtraKeysConfig.REPETITIVE_KEYS

    val isModifier: Boolean
        get() = key in listOf("CTRL", "ALT", "SHIFT", "FN")
}
