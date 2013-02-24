package com.github.fxthomas.lunar

import scala.util.Random
import _root_.android.app.{Activity, Fragment}
import _root_.android.os.{IBinder, Bundle, Handler, Message}
import _root_.android.util.Log
import _root_.android.provider.MediaStore
import _root_.android.database.Cursor
import _root_.android.animation.{ObjectAnimator, AnimatorSet}
import _root_.android.graphics.drawable.BitmapDrawable
import _root_.android.content.{Context, ServiceConnection, Intent, ComponentName}
import _root_.android.media.AudioManager
import _root_.android.widget.Button
import _root_.android.view.View
import _root_.android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup

object Try {
  def apply[T](result: => T): Option[T] = try {
    Some(result)
  } catch {
    case e: Exception => None
  }
}

class SongInfoFragment extends Fragment
with WheelView.Listener {

  private var wheelShown = false
  private var rootView: View = null
  
  def findViewById(id: Int) = Try(rootView.findViewById(id))
  def findView[T](tr: TypedResource[T]) =
    findViewById(tr.id) map (_.asInstanceOf[T])
  
  def wheelView = findView(TR.wheel_view)
  def albumView = findView(TR.album_view)
  def albumTitleView = findView(TR.album_title)
  def songTitleView = findView(TR.song_title)
  def artistNameView = findView(TR.artist_name)
  
  def onSliceClick(sliceNumber: Int) = {}
  def setProgress(p: Float) = albumView foreach (_.setProgress(p))
  def setSelectedSlice(m: Int) = wheelView foreach (_.setSelectedSlice(m))
  
  def setSong (s: Song) = {
    // Set song cover
    albumView foreach (_.setImageDrawable(
      s.album.getCover(getActivity)
      .map(b => new BitmapDrawable(b))
      .getOrElse(null)
    ))
    
    // Set song info
    songTitleView foreach(_.setText(s.title))
    artistNameView foreach (_.setText(s.artist.name))
    albumTitleView foreach(_.setText(s.album.title))
  }

  override def onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup,
    bundle: Bundle): View = {

    // Inflate view from XML
    Log.i("SongInfoFragment", "Creating root view")
    rootView = inflater.inflate(R.layout.fragment_songinfo, container, false)

    // Set some options
    Log.i("SongInfoFragment", "Creating wheelview")
    wheelView foreach (_.setVisibility(View.GONE))
    wheelView foreach (_.setListener(this))

    // Return view
    return rootView
  }

  def showWheel = {
    if (!wheelShown) {
      wheelView foreach (_.animateOn)
      wheelShown = true
    }
  }

  def hideWheel = {
    if (wheelShown) {
      wheelView foreach (_.animateOff)
      wheelShown = false
    }
  }
}
