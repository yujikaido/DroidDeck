package com.droiddeck.launcher.input

import android.view.MotionEvent
import kotlin.math.hypot

class TwoFingerTap(private val slopPx: Float) {
    enum class Result { PASS, SWALLOW, TAP, CANCEL }

    var x = 0f
        private set
    var y = 0f
        private set

    private val starts = HashMap<Int, Pair<Float, Float>>()
    private var downAt = 0L
    private var twoAt = 0L
    private var candidate = false
    private var swallowed = false

    fun onTouch(event: MotionEvent): Result {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                starts.clear()
                candidate = false
                swallowed = false
                downAt = event.eventTime
                x = event.x
                y = event.y
                note(event, event.actionIndex)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                note(event, event.actionIndex)
                candidate = event.pointerCount == 2 && !swallowed && event.eventTime - downAt < LAND_MS
                if (candidate) twoAt = event.eventTime
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val start = starts[event.getPointerId(i)] ?: continue
                if (hypot(event.getX(i) - start.first, event.getY(i) - start.second) > slopPx) candidate = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (candidate && event.eventTime - twoAt < TAP_MS) {
                    swallowed = true
                    return Result.SWALLOW
                }
                candidate = false
                if (swallowed) return Result.SWALLOW
            }
            MotionEvent.ACTION_UP -> {
                val tapped = candidate && swallowed && event.eventTime - twoAt < TAP_MS
                val wasSwallowed = swallowed
                candidate = false
                swallowed = false
                return when {
                    tapped -> Result.TAP
                    wasSwallowed -> Result.CANCEL
                    else -> Result.PASS
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                candidate = false
                swallowed = false
            }
        }
        return Result.PASS
    }

    private fun note(event: MotionEvent, index: Int) {
        starts[event.getPointerId(index)] = event.getX(index) to event.getY(index)
    }

    companion object {
        const val LAND_MS = 250L
        const val TAP_MS = 350L
    }
}
