package com.github.fxthomas.lunar

import _root_.android.widget.ImageView
import _root_.android.content.Context
import _root_.android.content.res.Resources
import _root_.android.graphics.{Paint, BitmapFactory, Path, Rect, RectF, Canvas, Color}
import _root_.android.graphics.drawable.{Drawable, ColorDrawable}
import _root_.android.util.AttributeSet

class AlbumView(context: Context, attributes: AttributeSet)
extends ImageView(context, attributes) {
    // View styles
    private val progressbarScale = 0.875f
    private val imageScale = 0.8f
    private val innerScale = 0.083f

    // Number between 0 and 1 indicating progress
    private var _progress: Float = 0

    // Paths
    private val progressbar = new Path
    private val clip = new Path
    private val inner = new Path
    private val inner_small = new Path

    // Current sizes
    private var width = 0
    private var height = 0
    private var size = 0

    // Useful sizes
    private def imageSize = size * imageScale
    private def progressbarSize = size * progressbarScale
    private def innerSize = size * innerScale
    private def innerSmallSize = size * innerScale / 3
    private def centerX = width/2
    private def centerY = height/2
    private def progressCenterX = width/2
    private def progressCenterY = (height + imageSize - progressbarSize)/2

    // Paint object with which we'll draw the progress bar
    private var color_fill = new Paint
    color_fill.setStyle (Paint.Style.FILL)
    color_fill.setARGB (255, 51, 181, 229) // Light blue
    color_fill.setAntiAlias (true)

    // Black filled paint
    private var black_fill = new Paint
    black_fill.setStyle (Paint.Style.FILL)
    black_fill.setARGB (255, 0, 0, 0) // Black
    black_fill.setAntiAlias (true)

    // Black filled paint
    private var darkgray_fill = new Paint
    darkgray_fill.setStyle (Paint.Style.FILL)
    darkgray_fill.setARGB (255, 50, 50, 50) // Dark Gray
    darkgray_fill.setAntiAlias (true)

    // Setter/Getter for the background color
    def getColor = color_fill.getColor
    def setColor (c: Int) = {
      color_fill.setColor(c)
      invalidate
    }

    // Setter/Getter for the progress value
    def getProgress = _progress
    def setProgress (p: Float): Unit = {
      _progress = p

      progressbar.rewind
      progressbar.addArc(new RectF(
          progressCenterX - progressbarSize/2, progressCenterY - progressbarSize/2,
          progressCenterX + progressbarSize/2, progressCenterY + progressbarSize/2
      ), 90, 360*p)
      progressbar.lineTo (progressCenterX, progressCenterY)
      invalidate
    }

    override def onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = {
      // Call super
      super.onSizeChanged(w, h, oldw, oldh)

      // Update sizes
      width = w
      height = h
      size = if (w < h) w else h

      // Progress circle
      setProgress(_progress)

      // Inner circle
      inner.rewind; inner_small.rewind
      inner.addCircle (centerX, centerY, innerSize/2, Path.Direction.CW)
      inner_small.addCircle (centerX, centerY, innerSmallSize/2, Path.Direction.CW)

      // Clipping circle
      clip.rewind
      clip.addCircle(centerX, centerY, imageSize/2, Path.Direction.CW)
    }

    override def onDraw(canvas: Canvas) = {
      // Draw progressbar
      canvas.drawPath (progressbar, color_fill)

      // Set the clip for the drawable
      canvas.clipPath (clip)

      // Fill the clip
      canvas.drawPath (clip, darkgray_fill)

      // Scale and draw the drawable
      canvas.scale (imageScale, imageScale, centerX, centerY)
      super.onDraw (canvas)

      // Draw the small inner dot
      canvas.drawPath (inner, color_fill)
      canvas.drawPath (inner_small, black_fill)
    }
}
