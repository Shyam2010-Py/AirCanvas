package com.aircanvas.app.drawing

import android.graphics.PointF

class CoordinateMapper {
    private val OLED_WIDTH = 128f
    private val OLED_HEIGHT = 64f

    fun mapToOled(screenX: Float, screenY: Float, canvasWidth: Float, canvasHeight: Float): PointF {
        val normX = (screenX / canvasWidth).coerceIn(0f, 1f)
        val normY = (screenY / canvasHeight).coerceIn(0f, 1f)
        
        return PointF(
            (normX * 127f).coerceIn(0f, 127f),
            (normY * 63f).coerceIn(0f, 63f)
        )
    }
}

enum class Tool {
    PEN, ERASER, LINE, RECTANGLE, CIRCLE
}
