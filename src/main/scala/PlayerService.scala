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

  // Music database (lazy because it needs to be loaded after onCreate)
  private lazy val musicDatabase = new
    PlayerDataOpenHelper(getApplicationContext)

  // Declare variables
  private val binder = new PlayerServiceBinder(this)
  private val mediaPlayer = new MediaPlayer
  private var playlist = MutableList[Song]()
  private var uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  private var currentSongIndex = -1
  private var starPower = 1

  // Public variables
  var currentMood = 0
  var isLoading = false

  // Increment by song duration in database (in seconds)
  private def incrementBySongDuration = {
    for (s <- currentSong)
      musicDatabase.incrementPlaytime (s, currentMood, starPower * s.duration / 1000f)
  }

  // Increment playtime in database (in seconds)
  private def incrementByPlayTime = {
    for (s <- currentSong)
      musicDatabase.incrementPlaytime (
        s, currentMood, starPower * mediaPlayer.getCurrentPosition.asInstanceOf[Float] / 1000f)
  }

  def setCurrentMood(m: Int) = {
    Log.i ("Lunar", s"Setting current mood to $m")
    currentMood = m
  }

  def currentSong = {
    if (currentSongIndex >= 0 && currentSongIndex < playlist.length)
      Some(playlist(currentSongIndex))
    else None
  }
  
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
    future {
      isLoading = true
      getApplicationContext.getContentResolver.query(uri, null, null, null, null)
    }

  def loadUri (uri: Uri): Unit =
    cursorFromUri(uri) onSuccess {
      case d => onPlaylistLoaded(d)}

  /*******************
   * Service methods *
   *******************/

  def onPlaylistLoaded (data: Cursor) = {
    isLoading = false
    currentSongIndex = -1
    playlist.clear
    playlist ++= Random.shuffle(Song.cursorToStream(data).toList)
    next
  }

  override def onBind (intent: Intent): IBinder = binder

  override def onStartCommand (intent: Intent, flags: Int, startId: Int): Int = {
    intent.getAction match {
      case ACTION_PLAY =>
        if (intent.hasExtra(EXTRA_URI)) {
          uri = Uri.parse (intent.getStringExtra (EXTRA_URI))
          loadUri (uri)
        } else if (!isPlaying) {
          if (currentSongIndex < 0) next
          else resume
        }

      case ACTION_PAUSE =>
        pause
      case ACTION_STOP =>
        stop
      case ACTION_RESUME =>
        resume
      case ACTION_NEXT =>
        next
      case _ => {}
    }

    return 0
  }

  override def onDestroy = {
    incrementByPlayTime
    listener.foreach(_.onStopPlayer)
    mediaPlayer.release
  }

  /*************************
   * MediaPlayer callbacks *
   *************************/

  def onPrepared (player: MediaPlayer) = {
    // Reset star power
    starPower = 1

    // Start playback
    player.start

    // Notify listener
    for (s <- currentSong;
         l <- listener)
       l.onStartPlayer(this, s)

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
      "Lunar", s"${playlist(currentSongIndex).artist.name} — ${playlist(currentSongIndex).title}",
      pendingIntent)
    notif
  }

  def onCompletion (player: MediaPlayer) = {
    incrementBySongDuration
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
    try {
      mediaPlayer.reset
      mediaPlayer.setAudioStreamType (AudioManager.STREAM_MUSIC)
      mediaPlayer.setDataSource (getApplicationContext, song.uri)
      mediaPlayer.prepareAsync
    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  def playAt (index: Int) = {
    if (index >= 0 && index < playlist.length)
      play (playlist(index))
  }

  def pause = {
    try {
      mediaPlayer.pause
      listener.foreach(_.onPausePlayer(this))
      stopForeground(true)
    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  def stop = {
    try {
      incrementByPlayTime
      mediaPlayer.stop
      listener.foreach(_.onStopPlayer)
      stopForeground(true)
    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  def resume = {
    try {
      mediaPlayer.start
      listener.foreach(_.onResumePlayer(this))
      startForeground(1, runningNotification)
    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  def star = starPower *= 2

  def togglePlaying = {
    if (isPlaying) pause
    else resume
  }

  def next = {
    // Increment playing time
    if (isPlaying) incrementByPlayTime

    // Reset media player
    mediaPlayer.reset

    // Load URI if nothing is in the playlist
    if (playlist.length == 0)
      loadUri (MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

    // If there's something in the playlist, we can play it immediately
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

  def isPlaying = try mediaPlayer.isPlaying catch { case _: Throwable => false }
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
    def onStartPlayer(service: PlayerService, song: Song) = {}
    def onPausePlayer(service: PlayerService) = {}
    def onResumePlayer(service: PlayerService) = {}
    def onErrorPlayer(service: PlayerService) = {}
    def onStopPlayer = {}
  }
}
