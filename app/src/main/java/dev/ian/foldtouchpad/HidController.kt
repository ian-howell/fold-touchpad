package dev.ian.foldtouchpad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.os.SystemClock
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/** Registers the phone as a Bluetooth HID keyboard+mouse and sends reports. */
@SuppressLint("MissingPermission")
class HidController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
) {
    private val adapter: BluetoothAdapter =
        context.getSystemService(BluetoothManager::class.java).adapter
    private val executor = Executors.newSingleThreadExecutor()
    private var hid: BluetoothHidDevice? = null
    var host: BluetoothDevice? = null
        private set
    private var buttons = 0

    /** Delay after each keyboard report (ms); set from Settings. */
    @Volatile var keyGapMs = DEFAULT_KEY_GAP_MS

    /**
     * All reports go through one background thread. sendReport() is a blocking
     * binder call; doing it on the UI thread was the main source of keyboard lag.
     */
    private class Report(val id: Int, val data: ByteArray)
    private val queue = LinkedBlockingQueue<Report>()
    private val sender = Thread({
        while (true) {
            val r = queue.take()
            // Send once. sendReport()'s return value is unreliable on some stacks
            // (false even on success), so retrying on it stalled typing badly.
            val h = hid; val d = host
            if (h != null && d != null) {
                val t0 = SystemClock.elapsedRealtime()
                h.sendReport(d, r.id, r.data)
                recordSendTime(SystemClock.elapsedRealtime() - t0)
            }
            // Keyboard reports are paced so the host sees every press and release.
            if (r.id == HidDescriptor.ID_KEYBOARD && keyGapMs > 0) Thread.sleep(keyGapMs)
        }
    }, "hid-sender").apply { isDaemon = true; start() }

    // Diagnostics: how long sendReport() blocks. Shown in the status line.
    private var statCount = 0
    private var statTotalMs = 0L
    private var statMaxMs = 0L
    private var statLastShown = 0L
    private var baseStatus = ""

    private fun recordSendTime(ms: Long) {
        statCount++; statTotalMs += ms; if (ms > statMaxMs) statMaxMs = ms
        val now = SystemClock.elapsedRealtime()
        if (queue.isEmpty() || now - statLastShown > 1000) {
            statLastShown = now
            onStatus("$baseStatus · send avg ${statTotalMs / statCount}ms, max ${statMaxMs}ms (${statCount})")
            statCount = 0; statTotalMs = 0; statMaxMs = 0
        }
    }

    private fun status(msg: String) { baseStatus = msg; onStatus(msg) }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            status(if (registered) "Registered – pair from your PC or pick a host" else "Not registered")
            if (registered) device?.let { hid?.connect(it) }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> { host = device; status("Connected: ${device.name}") }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == device) host = null
                    status("Disconnected")
                }
                BluetoothProfile.STATE_CONNECTING -> status("Connecting to ${device.name}…")
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = if (id.toInt() == HidDescriptor.ID_KEYBOARD) 8 else 5
            hid?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    fun start() {
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hid = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "Fold Touchpad", "Touchpad and keyboard", "dev.ian",
                    BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.DESCRIPTOR,
                )
                // Ask for a low-latency link. Without QoS the stack may let the link
                // idle into sniff mode, delaying each report by hundreds of ms
                // (the PC then auto-repeats the held key).
                val qos = BluetoothHidDeviceAppQosSettings(
                    BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
                    800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX,
                )
                proxy.registerApp(sdp, null, qos, executor, callback)
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null; host = null; status("HID service lost")
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        hid?.let { h ->
            host?.let { h.disconnect(it) }
            h.unregisterApp()
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        }
        hid = null; host = null
    }

    fun bondedDevices(): List<BluetoothDevice> = adapter.bondedDevices?.toList().orEmpty()

    fun connect(device: BluetoothDevice) {
        hid?.connect(device)
    }

    val isConnected get() = host != null

    // ---- Mouse ----
    fun move(dx: Int, dy: Int, wheel: Int = 0, hWheel: Int = 0) {
        var rx = dx; var ry = dy; var rw = wheel; var rh = hWheel
        do {
            val x = rx.coerceIn(-127, 127); val y = ry.coerceIn(-127, 127)
            val w = rw.coerceIn(-127, 127); val h = rh.coerceIn(-127, 127)
            sendMouse(buttons, x, y, w, h)
            rx -= x; ry -= y; rw -= w; rh -= h
        } while (rx != 0 || ry != 0 || rw != 0 || rh != 0)
    }

    fun setButton(mask: Int, down: Boolean) {
        buttons = if (down) buttons or mask else buttons and mask.inv()
        sendMouse(buttons, 0, 0, 0, 0)
    }

    fun click(mask: Int) { setButton(mask, true); setButton(mask, false) }

    private fun sendMouse(b: Int, x: Int, y: Int, w: Int, h: Int) {
        if (host == null) return
        queue.put(Report(HidDescriptor.ID_MOUSE,
            byteArrayOf(b.toByte(), x.toByte(), y.toByte(), w.toByte(), h.toByte())))
    }

    // ---- Keyboard ----
    fun tapKey(modifiers: Int, usage: Int) {
        sendKeys(modifiers, usage)
        // Release twice: a lost release is what makes the PC auto-repeat a key,
        // and a duplicate "all keys up" report is harmless.
        sendKeys(0, 0)
        sendKeys(0, 0)
    }

    /** Types a string: one press + release per character, queued in a single batch. */
    fun typeText(text: CharSequence, extraModifiers: Int = 0) {
        for (c in text) {
            val (mod, usage) = KeyMap.forChar(c) ?: continue
            tapKey(mod or extraModifiers, usage)
        }
    }

    private fun sendKeys(modifiers: Int, usage: Int) {
        if (host == null) return
        queue.put(Report(HidDescriptor.ID_KEYBOARD,
            byteArrayOf(modifiers.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0)))
    }

    companion object {
        const val BTN_LEFT = 1
        const val BTN_RIGHT = 2
        const val BTN_MIDDLE = 4
        /** 3 reports/char at 4ms ≈ 80 chars/s. */
        const val DEFAULT_KEY_GAP_MS = 4L
    }
}
