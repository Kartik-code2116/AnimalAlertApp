package com.example.animalalert.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.example.animalalert.R
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object MapPinHelper {

    enum class PinStyle {
        ACTIVE,
        OFFLINE,
        PRIMARY
    }

    fun pinStyleFor(status: String?, isPrimary: Boolean): PinStyle = when {
        isPrimary -> PinStyle.PRIMARY
        status == "offline" -> PinStyle.OFFLINE
        else -> PinStyle.ACTIVE
    }

    fun colorFor(style: PinStyle): Int = when (style) {
        PinStyle.ACTIVE -> 0xFF00C853.toInt()
        PinStyle.OFFLINE -> 0xFF64748B.toInt()
        PinStyle.PRIMARY -> 0xFFF59E0B.toInt()
    }

    fun createNumberedPin(context: Context, number: Int, pinStyle: PinStyle): BitmapDescriptor {
        val sizePx = (44 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fillColor = colorFor(pinStyle)
        val strokeColor = ContextCompat.getColor(context, R.color.white)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            this.style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            this.style = Paint.Style.STROKE
            strokeWidth = 3f * context.resources.displayMetrics.density
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            textAlign = Paint.Align.CENTER
            textSize = 16f * context.resources.displayMetrics.density
            isFakeBoldText = true
        }

        val pad = 4f * context.resources.displayMetrics.density
        val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)
        canvas.drawOval(rect, circlePaint)
        canvas.drawOval(rect, strokePaint)

        val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(number.toString(), sizePx / 2f, textY, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
