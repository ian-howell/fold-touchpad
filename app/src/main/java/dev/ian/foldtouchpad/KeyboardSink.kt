package dev.ian.foldtouchpad

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Invisible focusable view that receives soft-keyboard input and forwards
 * each character/key to the HID host instead of editing text locally.
 */
class KeyboardSink(context: Context, private val hid: HidController) : View(context) {
    /** Extra modifiers latched by the on-screen Ctrl/Alt/Win toggles. */
    var stickyModifiers = 0

    init { isFocusable = true; isFocusableInTouchMode = true }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                typeText(text); return true
            }
            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                // Composing disabled via VISIBLE_PASSWORD; commit anything that slips through.
                typeText(text); return true
            }
            override fun deleteSurroundingText(before: Int, after: Int): Boolean {
                repeat(before) { send(KeyMap.BACKSPACE) }
                repeat(after) { send(KeyMap.DELETE) }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event)
                return true
            }
            override fun performEditorAction(actionCode: Int): Boolean {
                send(KeyMap.ENTER); return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(event) || super.onKeyDown(keyCode, event)

    private fun handleKey(event: KeyEvent): Boolean {
        KeyMap.forKeyCode(event.keyCode)?.let { send(it); return true }
        val ch = event.unicodeChar
        if (ch != 0) { typeChar(ch.toChar()); return true }
        return false
    }

    private fun typeText(t: CharSequence) {
        if (t.length == 1) typeChar(t[0]) else hid.typeText(t, consumeSticky())
    }

    private fun typeChar(c: Char) {
        val (mod, usage) = KeyMap.forChar(c) ?: return
        hid.tapKey(mod or consumeSticky(), usage)
    }

    fun send(usage: Int, mods: Int = 0) = hid.tapKey(mods or consumeSticky(), usage)

    private fun consumeSticky(): Int = stickyModifiers.also { stickyModifiers = 0; onStickyCleared?.invoke() }

    var onStickyCleared: (() -> Unit)? = null

    fun showKeyboard() {
        requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(this, 0)
    }

    fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
    }
}
