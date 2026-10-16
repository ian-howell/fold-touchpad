package dev.ian.foldtouchpad

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Termux-style staging area: text is edited locally (with autocorrect, swipe,
 * voice input, paste...) and only sent to the PC when you hit Send.
 *  - Send button: types the text.
 *  - Keyboard's Enter/Send key: types the text followed by Enter.
 */
class StagingBar(context: Context, private val hid: HidController) : LinearLayout(context) {
    val input = EditText(context).apply {
        hint = "Type here, then Send"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        maxLines = 4
        setHorizontallyScrolling(false)
        setOnEditorActionListener { _, action, ev ->
            if (action == EditorInfo.IME_ACTION_SEND ||
                (ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) {
                send(withEnter = true); true
            } else false
        }
    }

    init {
        orientation = HORIZONTAL
        addView(input, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(context).apply {
            text = "Send"; isAllCaps = false
            setOnClickListener { send(withEnter = false) }
        })
    }

    private fun send(withEnter: Boolean) {
        val text = input.text.toString()
        if (text.isNotEmpty()) hid.typeText(text)
        if (withEnter) hid.tapKey(0, KeyMap.ENTER)
        input.text.clear()
    }

    fun showKeyboard() {
        input.requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(input, 0)
    }
}
