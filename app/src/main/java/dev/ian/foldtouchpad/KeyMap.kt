package dev.ian.foldtouchpad

import android.view.KeyEvent

/** Maps characters and Android key codes to HID usage IDs (US layout). */
object KeyMap {
    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_GUI = 0x08

    const val ENTER = 0x28; const val ESC = 0x29; const val BACKSPACE = 0x2A
    const val TAB = 0x2B; const val DELETE = 0x4C
    const val RIGHT = 0x4F; const val LEFT = 0x50; const val DOWN = 0x51; const val UP = 0x52

    private val unshifted = mapOf(
        ' ' to 0x2C, '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30, '\\' to 0x31,
        ';' to 0x33, '\'' to 0x34, '`' to 0x35, ',' to 0x36, '.' to 0x37, '/' to 0x38,
        '\n' to ENTER, '\t' to TAB,
    )
    private val shifted = mapOf(
        '!' to 0x1E, '@' to 0x1F, '#' to 0x20, '$' to 0x21, '%' to 0x22, '^' to 0x23,
        '&' to 0x24, '*' to 0x25, '(' to 0x26, ')' to 0x27, '_' to 0x2D, '+' to 0x2E,
        '{' to 0x2F, '}' to 0x30, '|' to 0x31, ':' to 0x33, '"' to 0x34, '~' to 0x35,
        '<' to 0x36, '>' to 0x37, '?' to 0x38,
    )

    /** Returns (modifiers, usage) or null if unmappable. */
    fun forChar(c: Char): Pair<Int, Int>? = when (c) {
        in 'a'..'z' -> 0 to (0x04 + (c - 'a'))
        in 'A'..'Z' -> MOD_SHIFT to (0x04 + (c - 'A'))
        in '1'..'9' -> 0 to (0x1E + (c - '1'))
        '0' -> 0 to 0x27
        else -> unshifted[c]?.let { 0 to it } ?: shifted[c]?.let { MOD_SHIFT to it }
    }

    fun forKeyCode(code: Int): Int? = when (code) {
        KeyEvent.KEYCODE_DEL -> BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> DELETE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> ENTER
        KeyEvent.KEYCODE_TAB -> TAB
        KeyEvent.KEYCODE_ESCAPE -> ESC
        KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
        else -> null
    }
}
