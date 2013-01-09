package com.github.fxthomas.lunar

import scala.collection.mutable.MutableList
import scala.util.Random
import scala.concurrent._
import ExecutionContext.Implicits.global

import _root_.android.app.{Service, Notification, PendingIntent}
import _root_.android.media.{MediaPlayer, AudioManager}
import _root_.android.media.audiofx.Visualizer
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
with MediaPlayer.OnCompletionListener
with Visualizer.OnDataCaptureListener {

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

  // Prepare visualizer
  private val visualizer = new Visualizer(0)

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

  // Visualizer output
  def onFftDataCapture(v: Visualizer, fft: Array[Byte], samplingRate: Int) = {}
  def onWaveFormDataCapture(v: Visualizer, waveform: Array[Byte], samplingRate: Int) = {
    Log.i ("Aubio", s"Sending data to native, sampling rate is $samplingRate")
    Song.computeBPM (waveform)
  }

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
    playlist ++= Random.shuffle(Song.cursorToStream(data).toList filter (_.is_music))
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
    visualizer.release
  }

  /*************************
   * MediaPlayer callbacks *
   *************************/

  def onPrepared (player: MediaPlayer) = {
    // Reset star power
    starPower = 1

    // Start playback
    player.start

    // Start visualizer
    visualizerStart

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
    visualizer.setEnabled(false)
    incrementBySongDuration
    next
  }

  def onError (player: MediaPlayer, what: Int, extra: Int) = {
    visualizer.setEnabled(false)
    Log.e ("PlayerService", "Error while playing")
    true
  }

  /****************************
   * Player interface methods *
   ****************************/

  /**
   * Start the BPM counter
   */
  private def visualizerStart = {
    if (!visualizer.getEnabled) {
      Log.i ("Aubio", "Enabling BPM counter")
      visualizer.setDataCaptureListener(this, Visualizer.getMaxCaptureRate/4, true, false)
      visualizer.setEnabled(true)
    }
  }

  /**
   * Stop the BPM counter
   */
  private def visualizerStop = {
    if (visualizer.getEnabled) {
      Log.i ("Aubio", "Disabling BPM counter")
      visualizer.setDataCaptureListener(null, 0, false, false)
      visualizer.setEnabled(false)
    }
  }

  /**
   * Play `song`
   */
  def play (song: Song) = {
    try {
      // Stop the BPM counter
      visualizerStop

      // Reset the media player
      mediaPlayer.reset

      // Set the media source
      mediaPlayer.setAudioStreamType (AudioManager.STREAM_MUSIC)
      mediaPlayer.setDataSource (getApplicationContext, song.uri)

      // Prepare the media player
      mediaPlayer.prepareAsync

    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  /**
   * Play song at `index` in the current playlist
   */
  def playAt (index: Int) = {
    if (index >= 0 && index < playlist.length)
      play (playlist(index))
  }

  /**
   * Pause the media player
   */
  def pause = {
    try {
      // Stop the BPM counter
      visualizerStop

      // Pause the media player
      mediaPlayer.pause

      // Notify the listener
      listener.foreach(_.onPausePlayer(this))

      // Disable notifications
      stopForeground(true)

    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  /**
   * Stop the media player
   */
  def stop = {
    try {
      // Update the play time
      incrementByPlayTime

      // Stop the BPM counter
      visualizerStop

      // Stop the media player
      mediaPlayer.stop

      // Notify the listener
      listener.foreach(_.onStopPlayer)

      // Disable notifications
      stopForeground(true)

    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  /**
   * Resume the media player
   */
  def resume = {
    try {
      // Start media player
      mediaPlayer.start

      // Start BPM counter
      visualizerStart

      // Notify the listener
      listener.foreach(_.onResumePlayer(this))

      // Show notification
      startForeground(1, runningNotification)

    } catch { case e: IllegalStateException =>
      Log.w("PlayerService", "Exception occured: " + e.getMessage)
    }
  }

  /**
   * Make the current song count twice more!
   */
  def star = starPower *= 2

  /**
   * Pause/Resume button
   */
  def togglePlaying = {
    if (isPlaying) pause
    else resume
  }

  /**
   * Play next song
   */
  def next = {
    // Increment playing time
    if (isPlaying) incrementByPlayTime

    // Reset media player
    visualizer.setEnabled(false)
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

  /**
   * Returns current progress as a float between 0 (beginning) and 1 (end)
   */
  def currentProgress = {
    if (mediaPlayer.getDuration > 0) {
      mediaPlayer.getCurrentPosition.asInstanceOf[Float]/mediaPlayer.getDuration.asInstanceOf[Float]
    } else 0f
  }

  /**
   * Returns the time left until the end of the song (in ms)
   */
  def timeLeft = {
    mediaPlayer.getDuration - mediaPlayer.getCurrentPosition
  }

  /**
   * `true` is the media player is playing, `false` otherwise
   */
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
