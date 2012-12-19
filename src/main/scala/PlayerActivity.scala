package com.github.fxthomas.lunar

import scala.util.Random

import _root_.android.app.Activity
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

class PlayerActivity extends Activity
with TypedActivity
with PlayerService.PlayerListener
with WheelView.Listener {

  private var wheelShown = false

  private class ProgressUpdateHandler extends Handler {
    override def handleMessage (msg: Message) {
      if (playerService != null && playerService.isPlaying) {
        findView (TR.album_view).setProgress (playerService.currentProgress)
        sendMessageDelayed (Message.obtain, 500)
      }
    }
  }
  private val progressUpdateHandler = new ProgressUpdateHandler

  def onSliceClick (i: Int) = {
    if (playerService != null) playerService.setCurrentMood(i)
  }

  override def onStop (service: PlayerService) = {
    findView(TR.album_view).setProgress(0f)
  }

  override def onPause (service: PlayerService) = {
    findView (TR.album_view).setProgress(service.currentProgress)
    findView (TR.btn_pause).setImageResource (R.drawable.play)
  }

  override def onResume (service: PlayerService) = {
    findView (TR.btn_pause).setImageResource (R.drawable.pause)
    progressUpdateHandler.sendMessage (Message.obtain)
  }

  override def onStartPlaying (service: PlayerService, song: Song) = {
    findView (TR.btn_pause).setImageResource (R.drawable.pause)
    findView (TR.album_view).setProgress(0f)
    findView(TR.album_view).setImageDrawable(song.album.getCover(this)
      .map(b => new BitmapDrawable(b))
      .getOrElse(null))

    progressUpdateHandler.sendMessage (Message.obtain)

    findView (TR.song_title).setText (song.title)
    findView (TR.artist_name).setText (song.artist.name)
    findView (TR.album_title).setText (song.album.title)
  }

  private var playerService: PlayerService = null
  private val playerServiceConnection = new ServiceConnection() {
    def onServiceConnected(name: ComponentName, binder: IBinder) {
      playerService = binder.asInstanceOf[PlayerService.PlayerServiceBinder].getService
      playerService.setListener(PlayerActivity.this)
      playerService.currentSong.foreach (s => onStartPlaying (playerService, s))
    }

    def onServiceDisconnected(name: ComponentName) {
      playerService = null
      onStop(null)
    }
  }

  override def onDestroy = {
    disconnectService
    super.onDestroy
  }

  override def onCreate(bundle: Bundle) {
    Log.i ("PlayerActivity", "Creating activity...")
    super.onCreate(bundle)
    setContentView(R.layout.main)
    setVolumeControlStream(AudioManager.STREAM_MUSIC)

    findView(TR.btn_pause).setOnClickListener(new View.OnClickListener {
       def onClick(b: View) = togglePlaying
    })

    findView(TR.btn_next).setOnClickListener(new View.OnClickListener {
       def onClick(b: View) = next
    })

    findView(TR.album_view).setOnClickListener(new View.OnClickListener {
      def onClick(b: View) = showWheel
    })

    findView(TR.wheel_view).setVisibility(View.GONE)
    findView(TR.wheel_view).setListener(this)

    connectService
  }

  def showWheel = {
    if (!wheelShown) {
      findView(TR.wheel_view).animateOn
      wheelShown = true
    }
  }

  def hideWheel = {
    if (wheelShown) {
      findView(TR.wheel_view).animateOff
      wheelShown = false
    }
  }

  override def onBackPressed = {
    if (wheelShown) hideWheel
    else {
      if (playerService != null && !playerService.isPlaying)
        stopService (new Intent(getApplicationContext, classOf[PlayerService]))
      super.onBackPressed
    }
  }

  def connectService = {
    val intent = new Intent(getApplicationContext, classOf[PlayerService])
    startService(intent)
    bindService(intent, playerServiceConnection, Context.BIND_ABOVE_CLIENT)
  }

  def disconnectService = {
    unbindService (playerServiceConnection)
  }

  def play (uri: Uri) = {
    hideWheel
    if (playerService != null)
      playerService.loadUri (uri)
  }

  def pause = {
    hideWheel
    if (playerService != null)
      playerService.pause
  }

  def resume = {
    hideWheel
    if (playerService != null)
      playerService.resume
  }

  def next = {
    hideWheel
    if (playerService != null)
      playerService.next
  }

  def togglePlaying = {
    hideWheel
    if (playerService != null)
      playerService.togglePlaying
  }
}
