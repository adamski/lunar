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
import _root_.java.util.concurrent.{TimeUnit, ScheduledExecutorService}

class PlayerActivity extends Activity
with TypedActivity
with PlayerService.PlayerListener {

  private class ProgressUpdateHandler extends Handler {
    override def handleMessage (msg: Message) {
      findView (TR.album_view).setProgress (playerService.currentProgress)
      if (playerService != null && playerService.isPlaying) sendMessage (Message.obtain)
    }
  }
  private val progressUpdateHandler = new ProgressUpdateHandler

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
    }

    def onServiceDisconnected(name: ComponentName) {
      playerService = null
    }
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

    connectService
  }

  def connectService = {
    val intent = new Intent(getApplicationContext, classOf[PlayerService])
    bindService(intent, playerServiceConnection, Context.BIND_AUTO_CREATE)
  }

  def play (uri: Uri) = {
    if (playerService != null)
      playerService.loadUri (uri)
  }

  def pause = {
    if (playerService != null)
      playerService.pause
  }

  def resume = {
    if (playerService != null)
      playerService.resume
  }

  def next = {
    if (playerService != null)
      playerService.next
  }

  def togglePlaying = {
    if (playerService != null)
      playerService.togglePlaying
  }
}
