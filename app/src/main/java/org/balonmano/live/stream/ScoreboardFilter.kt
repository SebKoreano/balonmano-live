package org.balonmano.live.stream

import android.graphics.Bitmap
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender

/** Owns each submitted bitmap; uploads and recycles exclusively under one monitor. */
class ScoreboardFilter : ImageObjectFilterRender() {
    private var current: Bitmap? = null
    private var released = false
    @Synchronized fun submit(bitmap: Bitmap) {
        if (released) { bitmap.recycle(); return }
        current?.takeUnless { it.isRecycled }?.recycle()
        current = bitmap
        super.setImage(bitmap)
    }
    @Synchronized override fun drawFilter() { super.drawFilter() }
    @Synchronized override fun release() {
        released = true
        super.release()
        current?.takeUnless { it.isRecycled }?.recycle()
        current = null
    }
}
