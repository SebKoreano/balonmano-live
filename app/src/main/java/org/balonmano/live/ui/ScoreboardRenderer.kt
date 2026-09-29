package org.balonmano.live.ui

import android.graphics.*
import org.balonmano.live.domain.MatchState
import org.balonmano.live.domain.clockText

/** The only inputs allowed into the video overlay are public match fields. */
class ScoreboardRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun render(s: MatchState): Bitmap {
        val image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        paint.color = Color.rgb(16, 24, 23)
        canvas.drawRoundRect(0f, 0f, 560f, 142f, 16f, 16f, paint)
        text(canvas, s.periodLabel, 18f, 29f, 18f, 0xFF83F3C4.toInt())
        text(canvas, when { s.timeout != null -> "TIEMPO MUERTO"; s.periodEnded -> "FIN DEL PERÍODO"; s.running -> "EN JUEGO"; else -> "PAUSA" }, 80f, 29f, 14f, Color.LTGRAY)
        text(canvas, clockText(s.totalElapsedMs), 538f, 34f, 29f, Color.WHITE, Paint.Align.RIGHT)
        s.teams.forEachIndexed { i, team ->
            val y = 73f + i * 49
            paint.color = team.color
            canvas.drawRoundRect(14f, y - 22, 21f, y + 8, 3f, 3f, paint)
            text(canvas, fit(team.name.uppercase(), 317f, 23f), 32f, y, 23f, Color.WHITE)
            text(canvas, "TM ${team.timeouts}", 430f, y, 15f, Color.LTGRAY, Paint.Align.RIGHT)
            text(canvas, team.goals.toString(), 538f, y + 2, 34f, Color.WHITE, Paint.Align.RIGHT)
        }
        var bottom = 150f
        s.teams.forEachIndexed { i, team ->
            val list = s.exclusions.filter { it.team == i }
            if (list.isNotEmpty()) {
                paint.color = 0xED101817.toInt(); canvas.drawRoundRect(0f, bottom, 560f, bottom + 34, 7f, 7f, paint)
                val visible = list.take(3).joinToString("   ") { "${if (it.shirt.isBlank()) "−2′" else "#${it.shirt}"} ${clockText(it.remainingMs, true)}" }
                val extra = if (list.size > 3) "   +${list.size - 3}" else ""
                paint.color = team.color; canvas.drawRect(0f, bottom + 5, 5f, bottom + 29, paint)
                text(canvas, visible + extra, 16f, bottom + 24, 18f, Color.WHITE)
                bottom += 40
            }
        }
        s.timeout?.let {
            paint.color = 0xFFFFB86B.toInt(); canvas.drawRoundRect(0f, bottom, 560f, bottom + 36, 7f, 7f, paint)
            text(canvas, "TM ${fit(s.teams[it.team].name, 340f, 18f)}", 15f, bottom + 25, 18f, Color.BLACK)
            text(canvas, clockText(it.remainingMs, true), 540f, bottom + 25, 22f, Color.BLACK, Paint.Align.RIGHT)
        }
        return image
    }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        paint.color = color; paint.textSize = size; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD); paint.textAlign = align
        c.drawText(value, x, y, paint)
    }
    private fun fit(value: String, maxWidth: Float, size: Float): String {
        paint.textSize = size; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        if (paint.measureText(value) <= maxWidth) return value
        var text = value
        while (text.isNotEmpty() && paint.measureText("$text…") > maxWidth) text = text.dropLast(1)
        return "$text…"
    }
    companion object { const val WIDTH = 560; const val HEIGHT = 272 }
}
