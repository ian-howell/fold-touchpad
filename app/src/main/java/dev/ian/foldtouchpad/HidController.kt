package dev.ian.foldtouchpad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
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

    /**
     * All reports go through one background thread. sendReport() is a blocking
     * binder call; doing it on the UI thread was the main source of keyboard lag.
     */
    private class Report(val id: Int, val data: ByteArray)
    private val queue = LinkedBlockingQueue<Report>()
    private val sender = Thread({
        while (true) {
            val r = queue.take()
            val h = hid; val d = host
            if (h != null && d != null) h.sendReport(d, r.id, r.data)
        }
    }, "hid-sender").apply { isDaemon = true; start() }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            onStatus(if (registered) "Registered – pair from your PC or pick a host" else "Not registered")
            if (registered) device?.let { hid?.connect(it) }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> { host = device; onStatus("Connected: ${device.name}") }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == device) host = null
                    onStatus("Disconnected")
                }
                BluetoothProfile.STATE_CONNECTING -> onStatus("Connecting to ${device.name}…")
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
                proxy.registerApp(sdp, null, null, executor, callback)
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null; host = null; onStatus("HID service lost")
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

    /**
     * Types a string as fast as HID allows. Consecutive different keys are sent as
     * direct press->press transitions (no release report in between); a release is
     * only inserted when the same key repeats or the modifier changes. That roughly
     * halves the report count versus press/release per character.
     */
    fun typeText(text: CharSequence, extraModifiers: Int = 0) {
        var lastMod = 0; var lastUsage = 0
        for (c in text) {
            val (mod0, usage) = KeyMap.forChar(c) ?: continue
            val mod = mod0 or extraModifiers
            if (usage == lastUsage || mod != lastMod) {
                if (lastUsage != 0) sendKeys(0, 0)
            }
            sendKeys(mod, usage)
            lastMod = mod; lastUsage = usage
        }
        if (lastUsage != 0) sendKeys(0, 0)
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
    }
}
