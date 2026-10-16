package dev.ian.foldtouchpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Gestures:
 *  - 1 finger drag: move pointer        - 1 finger tap: left click
 *  - 2 finger tap: right click          - 3 finger tap: middle click
 *  - 2 finger drag: scroll (vert+horiz) - double-tap-and-hold then drag: left-drag
 */
class TouchpadView(context: Context, private val hid: HidController) : View(context) {
    var sensitivity = 1.6f
    var scrollDivisor = 24f

    private val tapSlop = 12 * resources.displayMetrics.density
    private val tapTimeout = 220L

    private var downTime = 0L
    private var maxPointers = 0
    private var moved = false
    private var lastX = 0f; private var lastY = 0f
    private var startX = 0f; private var startY = 0f
    private var accX = 0f; private var accY = 0f
    private var scrollAccX = 0f; private var scrollAccY = 0f
    private var lastTapUp = 0L
    private var dragging = false

    private val paint = Paint().apply { color = Color.argb(60, 255, 255, 255); textSize = 14 * resources.displayMetrics.scaledDensity }

    init { setBackgroundColor(Color.rgb(24, 24, 28)) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val msg = if (hid.isConnected) "Touchpad" else "Not connected"
        canvas.drawText(msg, 24f, height - 24f, paint)
    }

    private fun centroid(e: MotionEvent): Pair<Float, Float> {
        var x = 0f; var y = 0f
        for (i in 0 until e.pointerCount) { x += e.getX(i); y += e.getY(i) }
        return x / e.pointerCount to y / e.pointerCount
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = e.eventTime; maxPointers = 1; moved = false
                lastX = e.x; lastY = e.y; startX = e.x; startY = e.y
                accX = 0f; accY = 0f; scrollAccX = 0f; scrollAccY = 0f
                if (e.eventTime - lastTapUp < tapTimeout) {
                    dragging = true; hid.setButton(HidController.BTN_LEFT, true)
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                // Re-anchor on centroid of remaining pointers to avoid jumps.
                val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
                var x = 0f; var y = 0f; var n = 0
                for (i in 0 until e.pointerCount) if (i != skip) { x += e.getX(i); y += e.getY(i); n++ }
                if (n > 0) { lastX = x / n; lastY = y / n }
            }
            MotionEvent.ACTION_MOVE -> {
                val (cx, cy) = centroid(e)
                val dx = cx - lastX; val dy = cy - lastY
                lastX = cx; lastY = cy
                if (!moved && hypot(cx - startX, cy - startY) > tapSlop) moved = true
                if (!moved) return true
                if (e.pointerCount >= 2) {
                    scrollAccY += dy / scrollDivisor; scrollAccX += dx / scrollDivisor
                    val wy = scrollAccY.toInt(); val wx = scrollAccX.toInt()
                    if (wy != 0 || wx != 0) {
                        scrollAccY -= wy; scrollAccX -= wx
                        // Natural scrolling: content follows fingers.
                        hid.move(0, 0, wy, -wx)
                    }
                } else {
                    accX += dx * accel(dx, dy); accY += dy * accel(dx, dy)
                    val ix = accX.toInt(); val iy = accY.toInt()
                    if (ix != 0 || iy != 0) { accX -= ix; accY -= iy; hid.move(ix, iy) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val quick = e.eventTime - downTime < tapTimeout * 1.5
                if (dragging) {
                    dragging = false; hid.setButton(HidController.BTN_LEFT, false)
                    lastTapUp = 0L
                } else if (!moved && quick && e.actionMasked == MotionEvent.ACTION_UP) {
                    when (maxPointers) {
                        1 -> { hid.click(HidController.BTN_LEFT); lastTapUp = SystemClock.uptimeMillis() }
                        2 -> hid.click(HidController.BTN_RIGHT)
                        else -> hid.click(HidController.BTN_MIDDLE)
                    }
                }
            }
        }
        return true
    }

    private fun accel(dx: Float, dy: Float): Float {
        val speed = abs(dx) + abs(dy)
        return sensitivity * (1f + (speed / 40f).coerceAtMost(1.5f))
    }
}
