package com.example.nativemap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/** A lightweight map-style surface with a single, continuously animated driver marker. */
class DriverMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stopPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F7568.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val parkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCEBE0.toInt() }
    private val buildingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3E8E5.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF66756D.toInt()
        textSize = dp(11f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x260D2723 }
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F7568.toInt() }
    private val carGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8F5F2.toInt() }
    private val routePath = Path()
    private val pathMeasure = PathMeasure()
    private val markerPosition = FloatArray(2)
    private val markerTangent = FloatArray(2)
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val mapBounds = RectF()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Map showing your driver moving from pickup to destination"
    }

    fun startRide() {
        animator?.cancel()
        progress = 0f
        invalidate()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9_000L
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopRide() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        val corner = dp(24f)
        mapBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val clip = Path().apply { addRoundRect(mapBounds, corner, corner, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawColor(0xFFE9F0EC.toInt())

        drawMapDetails(canvas)
        buildRoute()
        // A pale casing under the colored route keeps it legible over every map detail.
        roadPaint.strokeWidth = dp(11f)
        canvas.drawPath(routePath, roadPaint)
        canvas.drawPath(routePath, routePaint)
        drawStops(canvas)
        drawDriver(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawMapDetails(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val unit = min(w, h)
        canvas.drawRoundRect(w * .61f, h * .05f, w * .94f, h * .26f, dp(20f), dp(20f), parkPaint)
        canvas.drawRoundRect(w * .04f, h * .10f, w * .28f, h * .32f, dp(12f), dp(12f), buildingPaint)
        canvas.drawRoundRect(w * .69f, h * .40f, w * .96f, h * .58f, dp(12f), dp(12f), buildingPaint)
        canvas.drawRoundRect(w * .08f, h * .50f, w * .34f, h * .69f, dp(12f), dp(12f), parkPaint)
        canvas.drawRoundRect(w * .54f, h * .75f, w * .89f, h * .95f, dp(12f), dp(12f), buildingPaint)

        // Quiet neighborhood streets, kept below the route for visual hierarchy.
        roadPaint.color = 0xFFF9FBF9.toInt()
        roadPaint.strokeWidth = unit * .075f
        val streets = Path().apply {
            moveTo(-w * .1f, h * .42f); cubicTo(w * .22f, h * .32f, w * .38f, h * .42f, w * 1.1f, h * .31f)
            moveTo(-w * .08f, h * .79f); cubicTo(w * .25f, h * .68f, w * .61f, h * .68f, w * 1.08f, h * .77f)
            moveTo(w * .43f, -h * .08f); cubicTo(w * .35f, h * .23f, w * .51f, h * .58f, w * .42f, h * 1.08f)
            moveTo(w * .91f, -h * .08f); cubicTo(w * .80f, h * .24f, w * .91f, h * .61f, w * 1.02f, h * 1.08f)
        }
        canvas.drawPath(streets, roadPaint)
        roadPaint.color = 0xFFFFFFFF.toInt()

        canvas.drawText("RIVERSIDE PARK", w * .64f, h * .15f, labelPaint)
        canvas.drawText("OAK STREET", w * .08f, h * .44f, labelPaint)
        canvas.drawText("CENTRAL AVE", w * .48f, h * .61f, labelPaint)
    }

    private fun buildRoute() {
        val w = width.toFloat()
        val h = height.toFloat()
        routePath.reset()
        routePath.moveTo(w * .16f, h * .79f)
        routePath.cubicTo(w * .28f, h * .78f, w * .24f, h * .60f, w * .43f, h * .57f)
        routePath.cubicTo(w * .60f, h * .54f, w * .47f, h * .37f, w * .65f, h * .37f)
        routePath.cubicTo(w * .77f, h * .37f, w * .71f, h * .24f, w * .84f, h * .19f)
        pathMeasure.setPath(routePath, false)
    }

    private fun drawStops(canvas: Canvas) {
        val startX = width * .16f
        val startY = height * .79f
        val endX = width * .84f
        val endY = height * .19f
        drawStop(canvas, startX, startY, "A", 0xFF0F7568.toInt())
        drawStop(canvas, endX, endY, "B", 0xFFE26B4D.toInt())
    }

    private fun drawStop(canvas: Canvas, x: Float, y: Float, label: String, color: Int) {
        stopPaint.color = 0x240D2723
        canvas.drawCircle(x, y + dp(3f), dp(17f), stopPaint)
        stopPaint.color = color
        canvas.drawCircle(x, y, dp(15f), stopPaint)
        stopPaint.color = 0xFFFFFFFF.toInt()
        stopPaint.textSize = dp(13f)
        stopPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        stopPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, x, y + dp(4.5f), stopPaint)
        stopPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawDriver(canvas: Canvas) {
        val distance = pathMeasure.length * progress.coerceIn(0f, 1f)
        pathMeasure.getPosTan(distance, markerPosition, markerTangent)
        val x = markerPosition[0]
        val y = markerPosition[1]
        val angle = Math.toDegrees(kotlin.math.atan2(markerTangent[1].toDouble(), markerTangent[0].toDouble())).toFloat()
        canvas.save()
        canvas.rotate(angle, x, y)
        canvas.drawRoundRect(x - dp(17f), y - dp(11f) + dp(3f), x + dp(17f), y + dp(11f) + dp(3f), dp(8f), dp(8f), shadowPaint)
        canvas.drawRoundRect(x - dp(17f), y - dp(11f), x + dp(17f), y + dp(11f), dp(8f), dp(8f), carPaint)
        canvas.drawRoundRect(x - dp(7f), y - dp(7f), x + dp(6f), y + dp(7f), dp(4f), dp(4f), carGlassPaint)
        canvas.drawRoundRect(x + dp(9f), y - dp(6f), x + dp(13f), y - dp(2f), dp(2f), dp(2f), carGlassPaint)
        canvas.drawRoundRect(x + dp(9f), y + dp(2f), x + dp(13f), y + dp(6f), dp(2f), dp(2f), carGlassPaint)
        canvas.restore()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
