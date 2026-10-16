package dev.ian.foldtouchpad

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton

@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : Activity() {
    private lateinit var hid: HidController
    private lateinit var status: TextView
    private lateinit var touchpad: TouchpadView
    private lateinit var keys: KeyboardSink
    private lateinit var staging: StagingBar
    private var stagedMode = true
    private val modToggles = mutableListOf<Pair<ToggleButton, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hid = HidController(this) { msg -> runOnUiThread { status.text = msg; touchpad.invalidate() } }
        status = TextView(this).apply {
            text = "Starting…"; setTextColor(Color.LTGRAY); setPadding(24, 16, 24, 8)
        }
        touchpad = TouchpadView(this, hid)
        keys = KeyboardSink(this, hid)
        staging = StagingBar(this, hid)
        stagedMode = prefs.getBoolean(KEY_STAGED, true)
        hid.linkMode = prefs.getInt(KEY_LINK, HidController.LINK_BEST_EFFORT)
        applySettings()
        keys.onStickyCleared = { runOnUiThread { modToggles.forEach { it.first.isChecked = false } } }

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun btn(label: String, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { action() }
        }.also { bar.addView(it) }
        fun mod(label: String, mask: Int) = ToggleButton(this).apply {
            textOn = label; textOff = label; text = label
            setOnCheckedChangeListener { _, on ->
                keys.stickyModifiers = if (on) keys.stickyModifiers or mask else keys.stickyModifiers and mask.inv()
            }
        }.also { bar.addView(it); modToggles += it to mask }

        btn("Connect") { pickHost() }
        btn("⚙") { showSettings() }
        btn("⌨") { if (stagedMode) staging.showKeyboard() else keys.showKeyboard() }
        val modeBtn = btn("") {}
        fun renderMode() {
            modeBtn.text = if (stagedMode) "Mode: Staged" else "Mode: Live"
            staging.visibility = if (stagedMode) android.view.View.VISIBLE else android.view.View.GONE
        }
        modeBtn.setOnClickListener {
            stagedMode = !stagedMode
            prefs.edit().putBoolean(KEY_STAGED, stagedMode).apply()
            keys.hideKeyboard(); renderMode()
            if (stagedMode) staging.showKeyboard() else keys.showKeyboard()
        }
        renderMode()
        mod("Ctrl", KeyMap.MOD_CTRL); mod("Alt", KeyMap.MOD_ALT)
        mod("Shift", KeyMap.MOD_SHIFT); mod("Win", KeyMap.MOD_GUI)
        btn("Esc") { keys.send(KeyMap.ESC) }
        btn("Tab") { keys.send(KeyMap.TAB) }
        btn("←") { keys.send(KeyMap.LEFT) }; btn("↑") { keys.send(KeyMap.UP) }
        btn("↓") { keys.send(KeyMap.DOWN) }; btn("→") { keys.send(KeyMap.RIGHT) }
        btn("Del") { keys.send(KeyMap.DELETE) }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply {
                text = "Left"; setOnTouchListener { _, e -> mouseButton(e, HidController.BTN_LEFT) }
            }, LinearLayout.LayoutParams(0, 160, 1f))
            addView(Button(context).apply {
                text = "Right"; setOnTouchListener { _, e -> mouseButton(e, HidController.BTN_RIGHT) }
            }, LinearLayout.LayoutParams(0, 160, 1f))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(HorizontalScrollView(context).apply { addView(bar) }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(keys, LinearLayout.LayoutParams(1, 1))
            addView(staging, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(touchpad, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(root)
        ensurePermissions()
    }

    private fun mouseButton(e: android.view.MotionEvent, mask: Int): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> hid.setButton(mask, true)
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> hid.setButton(mask, false)
        }
        return false
    }

    private val perms = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_SCAN,
    )

    private fun ensurePermissions() {
        if (perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) hid.start()
        else requestPermissions(perms, 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        if (r.isNotEmpty() && r.all { it == PackageManager.PERMISSION_GRANTED }) hid.start()
        else status.text = "Bluetooth permissions are required"
    }

    private fun pickHost() {
        val devices = hid.bondedDevices()
        val labels = devices.map { it.name ?: it.address }.toMutableList()
        labels += "Make discoverable (pair new PC)…"
        AlertDialog.Builder(this)
            .setTitle("Connect to host")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < devices.size) hid.connect(devices[i])
                else startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
            }
            .show()
    }

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private fun applySettings() {
        touchpad.sensitivity = prefs.getFloat(KEY_POINTER, DEFAULT_POINTER)
        touchpad.scrollDivisor = BASE_SCROLL_DIVISOR / prefs.getFloat(KEY_SCROLL, DEFAULT_SCROLL)
        hid.keyGapMs = prefs.getInt(KEY_GAP2, HidController.DEFAULT_KEY_GAP_MS.toInt()).toLong()
    }

    /** Settings dialog: sliders for pointer and scroll speed (0.2x–4.0x), saved immediately. */
    private fun showSettings() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad * 2, pad, pad * 2, 0) }
        fun slider(label: String, key: String, def: Float) {
            val title = TextView(this)
            fun show(v: Float) { title.text = "%s: %.1f×".format(label, v) }
            val current = prefs.getFloat(key, def)
            show(current)
            val bar = SeekBar(this).apply {
                max = 38 // steps of 0.1 from 0.2 to 4.0
                progress = ((current - 0.2f) * 10).toInt().coerceIn(0, 38)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        val v = 0.2f + p / 10f
                        show(v); prefs.edit().putFloat(key, v).apply(); applySettings()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            box.addView(title); box.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        slider("Pointer speed", KEY_POINTER, DEFAULT_POINTER)
        slider("Scroll speed", KEY_SCROLL, DEFAULT_SCROLL)
        run {
            val title = TextView(this)
            fun show(v: Int) { title.text = "Typing delay: $v ms per report (raise if letters repeat or drop)" }
            val current = prefs.getInt(KEY_GAP2, HidController.DEFAULT_KEY_GAP_MS.toInt())
            show(current)
            val bar = SeekBar(this).apply {
                max = 30; progress = current
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        show(p); prefs.edit().putInt(KEY_GAP2, p).apply(); applySettings()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            box.addView(title); box.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        box.addView(TextView(this).apply { text = "Bluetooth link mode (restart app to apply)"; setPadding(0, pad, 0, 0) })
        val modes = listOf(HidController.LINK_BEST_EFFORT, HidController.LINK_GUARANTEED, HidController.LINK_NONE)
        box.addView(android.widget.RadioGroup(this).apply {
            val cur = prefs.getInt(KEY_LINK, HidController.LINK_BEST_EFFORT)
            modes.forEach { m ->
                addView(android.widget.RadioButton(context).apply {
                    id = 1000 + m; text = HidController.linkModeName(m); isChecked = m == cur
                })
            }
            setOnCheckedChangeListener { _, id -> prefs.edit().putInt(KEY_LINK, id - 1000).apply() }
        })
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(box)
            .setPositiveButton("Done", null)
            .setNeutralButton("Reset") { _, _ ->
                prefs.edit().remove(KEY_POINTER).remove(KEY_SCROLL).remove(KEY_GAP2).remove(KEY_LINK).apply(); applySettings()
            }
            .show()
    }

    companion object {
        private const val KEY_POINTER = "pointer_speed"
        private const val KEY_SCROLL = "scroll_speed"
        private const val KEY_STAGED = "staged_mode"
        private const val KEY_GAP2 = "key_gap_ms_v2" // new key so the old 4ms default resets to 0
        private const val KEY_LINK = "link_mode"
        private const val DEFAULT_POINTER = 1.6f
        private const val DEFAULT_SCROLL = 1.0f
        private const val BASE_SCROLL_DIVISOR = 24f
    }

    override fun onDestroy() {
        hid.stop()
        super.onDestroy()
    }
}
