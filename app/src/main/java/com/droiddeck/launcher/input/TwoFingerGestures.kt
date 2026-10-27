package com.droiddeck.launcher.input

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot

class TwoFingerGestures(private val slopPx: Float, private val listener: Listener) {
    interface Listener {
        fun cancelTouch()
        fun rightClick(x: Float, y: Float)
        fun scroll(dx: Float, dy: Float)
    }

    private enum class State { IDLE, CANDIDATE, TAPPED, HELD, SCROLLING, PASSING }

    private val handler = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var x = 0f
    private var y = 0f
    private var downAt = 0L
    private var twoAt = 0L
    private var startSpan = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val starts = HashMap<Int, Pair<Float, Float>>()
    private val hold = Runnable {
        if (state != State.CANDIDATE) return@Runnable
        state = State.HELD
        listener.cancelTouch()
        listener.rightClick(x, y)
    }

    fun cancel() {
        handler.removeCallbacks(hold)
        state = State.IDLE
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                starts.clear()
                downAt = event.eventTime
                x = event.x
                y = event.y
                note(event, event.actionIndex)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                note(event, event.actionIndex)
                if (state == State.IDLE && event.pointerCount == 2 && event.eventTime - downAt < LAND_MS) {
                    state = State.CANDIDATE
                    twoAt = event.eventTime
                    startSpan = span(event)
                    lastX = midX(event)
                    lastY = midY(event)
                    handler.postDelayed(hold, HOLD_MS)
                } else if (state == State.CANDIDATE) {
                    handler.removeCallbacks(hold)
                    state = State.PASSING
                } else if (state == State.SCROLLING || state == State.HELD || state == State.TAPPED) {
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> when (state) {
                State.CANDIDATE -> if (event.pointerCount == 2 && moved(event)) {
                    handler.removeCallbacks(hold)
                    if (abs(span(event) - startSpan) > slopPx * 2) {
                        state = State.PASSING
                    } else {
                        state = State.SCROLLING
                        listener.cancelTouch()
                        lastX = midX(event)
                        lastY = midY(event)
                        return true
                    }
                }
                State.SCROLLING -> {
                    if (event.pointerCount >= 2) {
                        val mx = midX(event)
                        val my = midY(event)
                        listener.scroll(lastX - mx, lastY - my)
                        lastX = mx
                        lastY = my
                    }
                    return true
                }
                State.TAPPED, State.HELD -> return true
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> when (state) {
                State.CANDIDATE -> {
                    handler.removeCallbacks(hold)
                    if (event.eventTime - twoAt < TAP_MS) {
                        state = State.TAPPED
                        return true
                    }
                    state = State.PASSING
                }
                State.SCROLLING, State.HELD, State.TAPPED -> return true
                else -> Unit
            }
            MotionEvent.ACTION_UP -> {
                val was = state
                cancel()
                when (was) {
                    State.TAPPED -> {
                        listener.cancelTouch()
                        listener.rightClick(x, y)
                        return true
                    }
                    State.SCROLLING, State.HELD -> return true
                    else -> Unit
                }
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return false
    }

    private fun note(event: MotionEvent, index: Int) {
        starts[event.getPointerId(index)] = event.getX(index) to event.getY(index)
    }

    private fun moved(event: MotionEvent): Boolean = (0 until event.pointerCount).any { i ->
        val start = starts[event.getPointerId(i)] ?: return@any false
        hypot(event.getX(i) - start.first, event.getY(i) - start.second) > slopPx
    }

    private fun span(event: MotionEvent) = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
    private fun midX(event: MotionEvent) = (event.getX(0) + event.getX(1)) / 2f
    private fun midY(event: MotionEvent) = (event.getY(0) + event.getY(1)) / 2f

    companion object {
        const val LAND_MS = 250L
        const val TAP_MS = 350L
        const val HOLD_MS = 500L
    }
}
