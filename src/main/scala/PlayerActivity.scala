package com.github.fxthomas.lunar

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.animation.{ObjectAnimator, AnimatorSet}

class PlayerActivity extends Activity with TypedActivity {
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    val anim = ObjectAnimator.ofFloat (findView(TR.album_view), "progress", 0f, 1f)
    anim.setDuration (4000)

    val s = new AnimatorSet
    s.play (anim)
    s.start
  }
}
