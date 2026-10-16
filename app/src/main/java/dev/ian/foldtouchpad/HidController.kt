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

    /** Link QoS requested at registration (takes effect on next app start). */
    var linkMode = LINK_BEST_EFFORT

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
            // Flow control: when the stack is busy it rejects the report. Wait briefly
            // and resend the *same* report so key releases are never lost, but give up
            // after REJECT_TIMEOUT_MS so a dead link can't stall typing forever.
            val t0 = SystemClock.elapsedRealtime()
            var ok = false
            while (true) {
                val h = hid; val d = host
                if (h == null || d == null) break
                ok = h.sendReport(d, r.id, r.data)
                event("send id=${r.id} ${r.data.joinToString(" ") { "%02x".format(it) }} -> $ok")
                if (ok) break
                rejected[r.id]++
                if (SystemClock.elapsedRealtime() - t0 > REJECT_TIMEOUT_MS) break
                Thread.sleep(2)
            }
            recordSend(r.id, ok, SystemClock.elapsedRealtime() - t0)
            // Keyboard reports are paced so the host sees every press and release.
            if (r.id == HidDescriptor.ID_KEYBOARD && keyGapMs > 0) Thread.sleep(keyGapMs)
        }
    }, "hid-sender").apply { isDaemon = true; start() }

    // Diagnostics: per report type, how many sendReport() calls the stack
    // accepted vs rejected, and how long they blocked. Shown in the status line.
    private val accepted = IntArray(3)
    private val rejected = IntArray(3)
    private var statMaxMs = 0L
    private var statLastShown = 0L
    private var baseStatus = ""

    private var burstStart = 0L
    private var burstReports = 0

    private fun recordSend(id: Int, ok: Boolean, ms: Long) {
        if (ok) accepted[id]++
        if (ms > statMaxMs) statMaxMs = ms
        val now = SystemClock.elapsedRealtime()
        if (burstReports == 0) burstStart = now - ms
        burstReports++
        if (queue.isEmpty() || now - statLastShown > 1000) {
            statLastShown = now
            val k = HidDescriptor.ID_KEYBOARD
            val secs = (now - burstStart).coerceAtLeast(1) / 1000.0
            onStatus("$baseStatus · ${linkModeName(linkMode)} · keys ok ${accepted[k]} " +
                "busy-retries ${rejected[k]} · max ${statMaxMs}ms · " +
                "%.0f reports/s".format(burstReports / secs))
            accepted.fill(0); rejected.fill(0); statMaxMs = 0; burstReports = 0
        }
    }

    /** Ring buffer of timestamped Bluetooth events, viewable by tapping the status line. */
    private val log = ArrayDeque<String>()
    private val logStart = SystemClock.elapsedRealtime()
    fun event(msg: String) = synchronized(log) {
        val t = SystemClock.elapsedRealtime() - logStart
        log.addLast("%6d.%03d  %s".format(t / 1000, t % 1000, msg))
        while (log.size > 300) log.removeFirst()
    }
    fun eventLog(): String = synchronized(log) { log.reversed().joinToString("\n") }

    private fun status(msg: String) { baseStatus = msg; onStatus(msg) }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            event("app registered=$registered")
            status(if (registered) "Registered – pair from your PC or pick a host" else "Not registered")
            if (registered) device?.let { hid?.connect(it) }
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            event("PC SET_PROTOCOL $protocol")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            event("PC interrupt data id=$reportId ${data.joinToString(" ") { "%02x".format(it) }}")
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) { event("PC virtual cable unplug") }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            event("connection state $state")
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
            event("PC GET_REPORT type=$type id=$id size=$bufferSize")
            val size = if (id.toInt() == HidDescriptor.ID_KEYBOARD) 8 else 5
            hid?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            event("PC SET_REPORT type=$type id=$id ${data.joinToString(" ") { "%02x".format(it) }}")
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
                // Same values as Google's WearMouse. "Guaranteed" seemed to make the
                // stack buffer reports instead of signalling busy, causing a backlog.
                val qos = when (linkMode) {
                    LINK_NONE -> null
                    else -> BluetoothHidDeviceAppQosSettings(
                        if (linkMode == LINK_GUARANTEED) BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED
                        else BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX,
                    )
                }
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
        /** Busy-retry flow control already paces reports; no extra gap by default. */
        const val DEFAULT_KEY_GAP_MS = 0L
        const val REJECT_TIMEOUT_MS = 500L

        const val LINK_BEST_EFFORT = 0
        const val LINK_GUARANTEED = 1
        const val LINK_NONE = 2
        fun linkModeName(m: Int) = when (m) {
            LINK_GUARANTEED -> "QoS guaranteed"; LINK_NONE -> "no QoS"; else -> "QoS best-effort"
        }
    }
}
