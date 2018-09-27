package org.simple.clinic.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.support.v7.widget.AppCompatButton
import android.util.AttributeSet
import org.simple.clinic.R

/**
 * Accepting better names.
 *
 * TODO: Draw bottom border.
 * TODO: Draw touch feedback.
 */
class PrimarySolidButton(context: Context, attrs: AttributeSet?) : AppCompatButton(context, attrs) {

  /**
   * These insets are applied as extra padding around the button shape. They are set
   * in such a way that if we ever go back to platform buttons that have elevation,
   * the spacings in usages will not change.
   */
  private val insets = RectF(dpToPx(4), dpToPx(5), dpToPx(4), dpToPx(3))

  /** Nullable because it gets called by super.constructor. */
  private var backgroundColorPaint: Paint? = null

  private val shapeBounds = RectF()
  private val cornerRadiusPx = resources.getDimensionPixelSize(R.dimen.corner_radius_button).toFloat()

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    shapeBounds.set(
        insets.left,
        insets.top,
        w.toFloat() - insets.right,
        h.toFloat() - insets.bottom)
  }

  override fun setBackground(background: Drawable?) {
    super.setBackground(null)

    background?.apply {
      if (this is ColorDrawable) {
        requireBackgroundColorPaint().color = color
        invalidate()
      } else {
        throw AssertionError("Non-solid backgrounds aren't supported")
      }
    }
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawRoundRect(shapeBounds, cornerRadiusPx, cornerRadiusPx, requireBackgroundColorPaint())
    super.onDraw(canvas)
  }

  private fun requireBackgroundColorPaint(): Paint {
    if (backgroundColorPaint == null) {
      backgroundColorPaint = Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    }
    return backgroundColorPaint!!
  }
}
