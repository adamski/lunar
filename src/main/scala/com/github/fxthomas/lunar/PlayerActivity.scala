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

  def findFragmentById[T](id: Int) =
    getFragmentManager.findFragmentById(id).asInstanceOf[T]
  
  private lazy val songInfo =
    findFragmentById[SongInfoFragment](R.id.fragment_songinfo)

  private class ProgressUpdateHandler extends Handler {
    override def handleMessage (msg: Message) {
      for (p <- playerService) if (p.isPlaying) {
        songInfo.setProgress (p.currentProgress)
        sendMessageDelayed (Message.obtain, 500)
      }
    }
  }
  private val progressUpdateHandler = new ProgressUpdateHandler

  def onSliceClick (i: Int) = {
    for (p <- playerService) {
      p.setCurrentMood(i)
      songInfo.setSelectedSlice(i)
    }
  }

  override def onStopPlayer = {
    songInfo.setProgress(0f)
  }

  override def onPausePlayer (service: PlayerService) = {
    songInfo.setProgress(service.currentProgress)
    findView (TR.btn_pause).setImageResource (R.drawable.play)
  }

  override def onResumePlayer (service: PlayerService) = {
    findView (TR.btn_pause).setImageResource (R.drawable.pause)
    progressUpdateHandler.sendMessage (Message.obtain)
  }

  override def onStartPlayer (service: PlayerService, song: Song) = {
    findView (TR.btn_pause).setImageResource (R.drawable.pause)
    songInfo.setProgress(0f)
    songInfo.setSong(song)
    progressUpdateHandler.sendMessage (Message.obtain)
  }

  private var playerService: Option[PlayerService] = None
  private val playerServiceConnection = new ServiceConnection() {
    def onServiceConnected(name: ComponentName, binder: IBinder) {
      // Set the PlayerService object
      playerService = Option(binder.asInstanceOf[PlayerService.PlayerServiceBinder].getService)

      // And configure it
      for (p <- playerService) {
        songInfo.setSelectedSlice(p.currentMood)
        p.setListener(PlayerActivity.this)
        p.currentSong.foreach (s => onStartPlayer (p, s))
      }
    }

    def onServiceDisconnected(name: ComponentName) {
      playerService = None
      onStop
    }
  }

  override def onDestroy = {
    disconnectService
    super.onDestroy
  }

  override def onCreate(bundle: Bundle) {
    Log.i ("PlayerActivity", "Creating activity...")
    super.onCreate(bundle)
    setContentView(R.layout.activity_player)
    setVolumeControlStream(AudioManager.STREAM_MUSIC)

    findView(TR.btn_pause).setOnClickListener(new View.OnClickListener {
       def onClick(b: View) = togglePlaying
    })

    findView(TR.btn_next).setOnClickListener(new View.OnClickListener {
       def onClick(b: View) = next
    })

    findView(TR.btn_star).setOnClickListener(new View.OnClickListener {
       def onClick(b: View) = star
    })

    connectService
  }

  def showWheel = songInfo.showWheel
  def hideWheel = songInfo.hideWheel

  override def onBackPressed = {
    for (p <- playerService) if (p.isPlaying)
      stopService (new Intent(
        getApplicationContext, classOf[PlayerService]))
    super.onBackPressed
  }

  def connectService = {
    val intent = new Intent(getApplicationContext, classOf[PlayerService])
    intent.setAction (PlayerService.ACTION_PLAY)
    startService(intent)
    bindService(intent, playerServiceConnection, Context.BIND_ABOVE_CLIENT)
  }

  def disconnectService = {
    unbindService (playerServiceConnection)
  }

  def star = {
    hideWheel
    for (p <- playerService) p.star
  }

  def play (uri: Uri) = {
    hideWheel
    for (p <- playerService) p.loadUri (uri)
  }

  def pause = {
    hideWheel
    for (p <- playerService) p.pause
  }

  def resume = {
    hideWheel
    for (p <- playerService) p.resume
  }

  def next = {
    hideWheel
    for (p <- playerService) p.next
  }

  def togglePlaying = {
    hideWheel
    for (p <- playerService)
      p.togglePlaying
  }
}
