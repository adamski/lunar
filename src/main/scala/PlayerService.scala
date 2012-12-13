package com.github.fxthomas.lunar

import scala.collection.mutable.MutableList
import scala.util.Random
import scala.concurrent._
import ExecutionContext.Implicits.global

import _root_.android.app.{Service, Notification, PendingIntent}
import _root_.android.media.{MediaPlayer, AudioManager}
import _root_.android.net.Uri
import _root_.android.os.{Binder, IBinder, Bundle}
import _root_.android.util.Log
import _root_.android.content.Intent
import _root_.android.provider.MediaStore
import _root_.android.database.Cursor

class PlayerService
extends Service
with MediaPlayer.OnPreparedListener
with MediaPlayer.OnErrorListener
with MediaPlayer.OnCompletionListener {

  import PlayerService._

  /*********************
   * Private variables *
   *********************/

  // Declare variables
  private val binder = new PlayerServiceBinder(this)
  private val mediaPlayer = new MediaPlayer
  private var playlist = MutableList[Song]()
  private var uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  private var currentSongIndex = 0
  
  // Accessible variables
  private var listener: Option[PlayerService.PlayerListener] = None
  def setListener(l: PlayerService.PlayerListener) = {
    listener = Option(l)
  }

  // Initialize mediaPlayer
  mediaPlayer.setOnPreparedListener (this)
  mediaPlayer.setOnErrorListener (this)
  mediaPlayer.setOnCompletionListener (this)

  /*************************
   * LoaderManager methods *
   *************************/

  def cursorFromUri (uri: Uri): Future[Cursor] =
    future(getApplicationContext.getContentResolver.query(uri, null, null,
    null, null))

  def loadUri (uri: Uri) = cursorFromUri(uri) onSuccess { case data => {
    currentSongIndex = 0
    playlist.clear
    playlist ++= Random.shuffle(Song.cursorToStream(data).toList)
    playAt(currentSongIndex)
  }}

  /*******************
   * Service methods *
   *******************/

  override def onBind (intent: Intent): IBinder = binder

  override def onCreate = {
    loadUri(uri)
  }

  override def onStartCommand (intent: Intent, flags: Int, startId: Int): Int = {
    intent.getAction match {
      case ACTION_PLAY =>
        uri = Uri.parse (intent.getStringExtra (EXTRA_URI))
        loadUri (uri)
      case ACTION_PAUSE =>
        pause
      case ACTION_STOP =>
        stop
      case ACTION_RESUME =>
        resume
      case ACTION_NEXT =>
        next
    }

    return 0
  }

  override def onDestroy = {
    mediaPlayer.release
    listener.foreach(_.onStop(this))
  }

  /*************************
   * MediaPlayer callbacks *
   *************************/

  def onPrepared (player: MediaPlayer) = {
    // Start playback
    player.start

    // Notify listener
    listener.foreach (_.onStartPlaying(this, playlist(currentSongIndex)))

    // Show notification
    startForeground (1, runningNotification)
  }

  private def runningNotification = {
    val pendingIntent = PendingIntent.getActivity(
      getApplicationContext, 0,
      new Intent(getApplicationContext, classOf[PlayerActivity]),
      PendingIntent.FLAG_UPDATE_CURRENT)
    val notif = new Notification
    notif.tickerText = playlist(currentSongIndex).title
    notif.icon = R.drawable.ic_status
    notif.flags |= Notification.FLAG_ONGOING_EVENT
    notif.setLatestEventInfo (
      getApplicationContext,
      "Lunar", "Playing " + playlist(currentSongIndex).title,
      pendingIntent)
    notif
  }

  def onCompletion (player: MediaPlayer) = {
    next
  }

  def onError (player: MediaPlayer, what: Int, extra: Int) = {
    Log.e ("PlayerService", "Error while playing")
    true
  }

  /****************************
   * Player interface methods *
   ****************************/

  def play (song: Song) = {
    mediaPlayer.reset
    mediaPlayer.setAudioStreamType (AudioManager.STREAM_MUSIC)
    mediaPlayer.setDataSource (getApplicationContext, song.uri)
    mediaPlayer.prepareAsync
  }

  def playAt (index: Int) = {
    if (index >= 0 && index < playlist.length)
      play (playlist(index))
  }

  def pause = {
    mediaPlayer.pause
    listener.foreach(_.onPause(this))
    stopForeground(true)
  }

  def stop = {
    mediaPlayer.stop
    listener.foreach(_.onStop(this))
    stopForeground(true)
  }

  def resume = {
    mediaPlayer.start
    listener.foreach(_.onResume(this))
    startForeground(1, runningNotification)
  }

  def togglePlaying = {
    if (mediaPlayer.isPlaying) pause
    else resume
  }

  def next = {
    if (playlist.length == 0) {
      loadUri (MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    if (playlist.length > 0) {
      currentSongIndex = (currentSongIndex + 1) % playlist.length
      playAt (currentSongIndex)
    }
  }

  def currentProgress = {
    if (mediaPlayer.getDuration > 0) {
      mediaPlayer.getCurrentPosition.asInstanceOf[Float]/mediaPlayer.getDuration.asInstanceOf[Float]
    } else 0f
  }

  def timeLeft = {
    mediaPlayer.getDuration - mediaPlayer.getCurrentPosition
  }

  def isPlaying = mediaPlayer.isPlaying
}

object PlayerService {
  // Actions and intent extras
  val ACTION_PLAY = "com.github.fxthomas.lunar.PLAY"
  val ACTION_PAUSE = "com.github.fxthomas.lunar.PAUSE"
  val ACTION_STOP = "com.github.fxthomas.lunar.STOP"
  val ACTION_RESUME = "com.github.fxthomas.lunar.RESUME"
  val ACTION_NEXT = "com.github.fxthomas.lunar.NEXT"
  val EXTRA_URI = "com.github.fxthomas.lunar.EXTRA_URI"

  // Service binder
  class PlayerServiceBinder(service: PlayerService) extends Binder {
    def getService = service
  }

  trait PlayerListener {
    def onStartPlaying(service: PlayerService, song: Song) = {}
    def onPause(service: PlayerService) = {}
    def onResume(service: PlayerService) = {}
    def onError(service: PlayerService) = {}
    def onStop(service: PlayerService) = {}
  }
}
