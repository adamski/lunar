package com.github.fxthomas.lunar

import _root_.android.database.Cursor
import _root_.android.database.sqlite.{SQLiteOpenHelper, SQLiteDatabase}
import _root_.android.net.Uri
import _root_.android.content.{Context, ContentUris, ContentValues}
import _root_.android.graphics.{Bitmap, BitmapFactory}
import _root_.android.provider.{MediaStore, BaseColumns}
import _root_.android.provider.MediaStore.Audio.AudioColumns
import _root_.android.provider.MediaStore.MediaColumns
import _root_.android.util.Log

import scala.reflect

// Creates a database called "playerdata" at version 1
class PlayerDataOpenHelper(context: Context)
extends SQLiteOpenHelper(context, "playerdata", null, 1) {

  // SQL query for creating the "songs" table
  private val QUERY_CREATE_TABLE_SONGS =
    "CREATE TABLE songs (uri TEXT, mood INTEGER, playtime REAL, PRIMARY KEY (uri, mood));"

  private val QUERY_INCREMENT_PLAYTIME =
    "UPDATE songs SET playtime=(playtime + ?) WHERE uri = ? AND mood = ?;"

  override def onCreate (db: SQLiteDatabase) = {
    db.execSQL (QUERY_CREATE_TABLE_SONGS)
  }

  override def onUpgrade (db: SQLiteDatabase, oldVer: Int, newVer: Int) = {}

  def incrementPlaytime (s: Song, mood: Int, incr: Float) = {
    // Prepare values
    val cv = new ContentValues
    cv.put ("uri", s.uriString)
    cv.put ("mood", Int.box(mood))
    cv.put ("playtime", Float.box(0f))

    // Start transaction
    val db = getWritableDatabase
    db.beginTransaction

    try {
      db.insertWithOnConflict ("songs", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
      db.rawQuery(QUERY_INCREMENT_PLAYTIME,
        Array(s.uriString, mood.toString, incr.toString))
      db.setTransactionSuccessful
    } finally db.endTransaction
  }
}

case class Album(title: String, id: Long, key: String) {
  def getCover (context: Context): Option[Bitmap] = {
    try {
      // Parse cover URI
      val uri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"), id)

      // Open an input stream with the URI
      val in = context.getContentResolver.openInputStream(uri)

      // And decode the input stream
      Option(BitmapFactory.decodeStream(in))

    } catch {

      // If the cover is not found, then return None
      case e: java.io.FileNotFoundException => {
        Log.w ("Music", s"Exception when reading cover image: ${e.getMessage}")
        None
      }

      // If we encountered a security exception
      case e: java.lang.SecurityException => {
        Log.w ("Music", s"Exception when reading cover image: ${e.getMessage}")
        None
      }
    }
  } 
}

case class Artist(name: String, id: Long, key: String)

case class Song(
  title: String,
  is_music: Boolean,
  album: Album,
  artist: Artist,
  composer: String,
  duration: Long,
  uriString: String
) {
  lazy val uri = {
    val b = new Uri.Builder
    b.appendPath(uriString)
    b.build
  }
}

object Song {
  // Load audioproc library
  // System.loadLibrary("audioproc")

  // Native methods
  // @native def computeBPM (data: Array[Byte])
  def computeBPM(data: Array[Byte]) = {}

  def cursorToStream (cursor: Cursor) = {
    // Convert the list of columns into a dictionary (name => index)
    val c = List(
      MediaColumns.TITLE,
      MediaColumns.DATA,
      AudioColumns.IS_MUSIC,
      AudioColumns.ALBUM,
      AudioColumns.ALBUM_ID,
      AudioColumns.ALBUM_KEY,
      AudioColumns.ARTIST,
      AudioColumns.ARTIST_ID,
      AudioColumns.ARTIST_KEY,
      AudioColumns.COMPOSER,
      AudioColumns.DURATION)
    .map(s => (s, cursor.getColumnIndexOrThrow(s)))
    .toMap

    // Move the cursor to the first element
    cursor.moveToFirst

    // Create a Stream with the cursor's data
    Stream.continually((

      // 1. Create a Song object
      Song(
        cursor.getString(c(MediaColumns.TITLE)),
        cursor.getInt(c(AudioColumns.IS_MUSIC)) == 1,
        Album(
          cursor.getString(c(AudioColumns.ALBUM)),
          cursor.getLong(c(AudioColumns.ALBUM_ID)),
          cursor.getString(c(AudioColumns.ALBUM_KEY))
        ),
        Artist(
          cursor.getString(c(AudioColumns.ARTIST)),
          cursor.getLong(c(AudioColumns.ARTIST_ID)),
          cursor.getString(c(AudioColumns.ARTIST_KEY))
        ),
        cursor.getString(c(AudioColumns.COMPOSER)),
        cursor.getLong(c(AudioColumns.DURATION)),
        cursor.getString(c(MediaColumns.DATA))
      ),

      // 2. Include a sentinel value to tell the Stream it's okay to go on
      cursor.moveToNext))

    .takeWhile(_._2)  // We continue while moveToNext is not false
    .map(_._1)        // And we discard the sentinel
  }
}
