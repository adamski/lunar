package com.github.fxthomas.lunar

import _root_.android.database.Cursor
import _root_.android.net.Uri
import _root_.android.content.{Context, ContentUris}
import _root_.android.graphics.{Bitmap, BitmapFactory}
import _root_.android.provider.{MediaStore, BaseColumns}
import _root_.android.provider.MediaStore.Audio.AudioColumns
import _root_.android.provider.MediaStore.MediaColumns

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
      case e: java.io.FileNotFoundException => None
    }
  }
}

case class Artist(name: String, id: Long, key: String)

case class Song(
  title: String,
  album: Album,
  artist: Artist,
  composer: String,
  duration: Long,
  uriString: String
) {
  lazy val uri = Uri.parse(uriString)
}

object Song {
  def cursorToStream (cursor: Cursor) = {
    // Convert the list of columns into a dictionary (name => index)
    val c = List(
      MediaColumns.TITLE,
      MediaColumns.DATA,
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
