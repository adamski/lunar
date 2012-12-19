package com.github.fxthomas.lunar

import _root_.android.view.{View, MotionEvent}
import _root_.android.content.Context
import _root_.android.content.res.Resources
import _root_.android.graphics.{Paint, BitmapFactory, Path, Rect, RectF, Canvas, Color}
import _root_.android.graphics.drawable.{Drawable, ColorDrawable}
import _root_.android.util.{Log, AttributeSet}
import _root_.android.animation.{Animator, AnimatorSet, ObjectAnimator}

import scala.math.{sin, cos, atan2}

class WheelView(context: Context, attributes: AttributeSet)
extends View(context, attributes) {
  import WheelView._

  private val default_colors = List(
    R.color.wheel_blue,
    R.color.light_gray,
    R.color.wheel_purple,
    R.color.wheel_yellow,
    R.color.wheel_green,
    R.color.wheel_red
  )

  private var _listener: Option[Listener] = None
  def setListener (l: Listener) = _listener = Option(l)

  private val colors: List[Int] = (0 to default_colors.length-1).toList.map(
    s => attributes.getAttributeResourceValue(
      "fx", "color_$s", context.getResources.getColor(default_colors(s))))
  private var selectionAngle: Option[Float] = None

  // View styles
  private val imageScale = 0.8f
  private val wheelScale = 0.82f
  private val innerScale = 0.083f
  private var clipSweep = 0f

  def setClipSweep (s: Float) = {
    clipSweep = s
    clip.rewind
    clip.addArc(new RectF(
      centerX - imageSize/2, centerY - imageSize/2,
      centerX + imageSize/2, centerY + imageSize/2
    ), 0, s)
    clip.lineTo(centerX, centerY)
    invalidate
  }

  // Paths
  private val clip = new Path

  // Current sizes
  private var width = 0
  private var height = 0
  private var size = 0

  // Useful sizes
  private def imageSize = size * imageScale
  private def innerSize = size * innerScale
  private def wheelSize = size * wheelScale
  private def centerX = width/2
  private def centerY = height/2

  // Path for a part of the wheel
  private def wheelSlice (startAngle: Float, sweepAngle: Float) = {
    val ac = 3.14159265f * (startAngle + sweepAngle*.5f) / 180f
    val xo = (.5f * innerSize * cos(ac)).asInstanceOf[Float]
    val yo = (.5f * innerSize * sin(ac)).asInstanceOf[Float]
    val ws = selectionAngle match {
      case None => wheelSize
      case Some(a) =>
        if (a > startAngle && a < startAngle+sweepAngle) wheelSize*1.2f
        else wheelSize
    }

    val path = new Path
    path.addArc(new RectF(
      centerX - ws/2 + xo, centerY - ws/2 + yo,
      centerX + ws/2 + xo, centerY + ws/2 + yo
    ), startAngle, sweepAngle)
    path.lineTo(centerX + xo, centerY + yo)
    path
  }

  // Paint object with which we'll draw the progress bar
  private var color_fill = new Paint
  color_fill.setStyle (Paint.Style.FILL)
  color_fill.setARGB (255, 51, 181, 229) // Light blue
  color_fill.setAntiAlias (true)

  // Black filled paint
  private var darkgray_fill = new Paint
  darkgray_fill.setStyle (Paint.Style.FILL)
  darkgray_fill.setARGB (255, 50, 50, 50) // Dark Gray
  darkgray_fill.setAntiAlias (true)

  override def onTouchEvent(m: MotionEvent): Boolean = {
    val x = m.getX-centerX
    val y = m.getY-centerY
    val s = Some(180f * ((2f + atan2(y, x).asInstanceOf[Float] / 3.14159265f) % 2f))

    m.getAction match {
      case MotionEvent.ACTION_DOWN =>
        selectionAngle = s
      case MotionEvent.ACTION_MOVE =>
        selectionAngle = s
      case MotionEvent.ACTION_UP => {
        selectionAngle.foreach (a => _listener.foreach(
          l => l.onSliceClick((a * colors.length / 360f).asInstanceOf[Int])))
        selectionAngle = None
      }
      case _ => {}
    }

    invalidate

    return true
  }

  override def onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = {
    // Call super
    super.onSizeChanged(w, h, oldw, oldh)

    // Update sizes
    width = w
    height = h
    size = if (w < h) w else h

    // Clipping circle
    setClipSweep(clipSweep)
  }

  def animateOff = {
    val clipAnimate = ObjectAnimator.ofFloat(this, "clipSweep", 360, 0)
    clipAnimate.setDuration (500)
    clipAnimate.addListener (new Animator.AnimatorListener {
      def onAnimationEnd(a: Animator) = setVisibility(View.GONE)
      def onAnimationCancel(a: Animator) = {}
      def onAnimationRepeat(a: Animator) = {}
      def onAnimationStart(a: Animator) = {}
    })
    clipAnimate.start
  }

  def animateOn = {
    setVisibility(View.VISIBLE)
    val clipAnimate = ObjectAnimator.ofFloat(this, "clipSweep", 0, 360)
    clipAnimate.setDuration (500)
    clipAnimate.start
  }

  override def onDraw(canvas: Canvas) = {
    // Set the clip for the drawable
    canvas.clipPath (clip)

    // Fill the clip
    canvas.drawPath (clip, darkgray_fill)

    // Scale and draw the base view
    canvas.scale (imageScale, imageScale, centerX, centerY)
    super.onDraw (canvas)

    // Draw arcs
    val sweepAngle = 360f/colors.length
    for (i <- 0 to colors.length-1) {
      color_fill.setColor(colors(i))
      canvas.drawPath (wheelSlice(i*sweepAngle, sweepAngle), color_fill)
    }
  }
}

object WheelView {
  abstract trait Listener {
    def onSliceClick (sliceNumber: Int)
  }
}
