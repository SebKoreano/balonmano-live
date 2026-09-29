package org.balonmano.live

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceView

/** Test-only foreground surface; absent from the signed release APK. */
class CaptureTestActivity : Activity() {
    lateinit var surface: SurfaceView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = SurfaceView(this)
        setContentView(surface)
    }
}
