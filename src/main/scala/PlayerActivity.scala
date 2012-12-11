package com.github.fxthomas.lunar

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.util.Log
import _root_.android.provider.MediaStore
import _root_.android.database.Cursor
import _root_.android.animation.{ObjectAnimator, AnimatorSet}

class ScalaCursor(cursor: Cursor, columns: Array[String]) {
  // Convert the list of columns into a list of tuples (name, index)
  val indexedQueries = List.fromArray(columns).zipWithIndex

  def toStream = {
    // Move the cursor to the first element
    cursor.moveToFirst

    // Create a Stream with the cursor's data
    Stream.continually((

      // 1. Make a map with the requested columns
      indexedQueries.map(
        q => (q._1, cursor.getString(q._2))).toMap,

      // 2. Include a sentinel value to tell the Stream it's okay to go on
      cursor.moveToNext))

    .takeWhile(_._2)  // We continue while moveToNext is not false
    .map(_._1)        // And we discard the sentinel
  }
}

class PlayerActivity extends Activity with TypedActivity {
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    val columns = Array(
      MediaStore.MediaColumns.TITLE,
      MediaStore.Audio.AudioColumns.ALBUM,
      MediaStore.Audio.AudioColumns.ARTIST,
      MediaStore.Audio.AudioColumns.DURATION)
    val audioCursor = managedQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      columns, null, null, null)
    val list = new ScalaCursor(audioCursor, columns).toStream.toList
  }
}
