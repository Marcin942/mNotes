package com.mdev.mnotes.core.pages

import com.mdev.mnotes.core.geom.Bounds
import java.util.Calendar

/**
 * Tryb wykładu (pomysł Fable nr 1): każde zdjęcie z aparatu trafia na nową stronę za bieżącą, z nagłówkiem
 * „data, godzina · zdjęcie n” i miejscem na notatki pod spodem — po wykładzie zeszyt jest gotowy do adnotacji.
 */
object LecturePages {
    const val MARGIN = 28f
    const val HEADER = 26f
    /** Część strony pod zdjęcie; reszta zostaje na notatki. */
    const val PHOTO_SHARE = 0.62f

    /** Nagłówek strony, np. „27.10.2026, 10:15 · zdjęcie 3”. */
    fun header(millis: Long, n: Int, pl: Boolean): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val d = "%02d.%02d.%04d, %02d:%02d".format(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return d + if (pl) " · zdjęcie $n" else " · photo $n"
    }

    /**
     * Prostokąt zdjęcia [iw]×[ih] px na stronie [pw]×[ph] pt: pod nagłówkiem, wpisane w szerokość strony z marginesami
     * i w [PHOTO_SHARE] wysokości (proporcje zachowane, wyśrodkowane w poziomie).
     */
    fun photoRect(iw: Int, ih: Int, pw: Float, ph: Float): Bounds {
        val maxW = pw - 2 * MARGIN
        val maxH = ph * PHOTO_SHARE - HEADER
        val k = minOf(maxW / iw.coerceAtLeast(1), maxH / ih.coerceAtLeast(1))
        val w = iw * k; val h = ih * k
        val l = (pw - w) / 2f; val t = MARGIN + HEADER
        return Bounds(l, t, l + w, t + h)
    }

    /** Numer następnego zdjęcia wykładu: po nagłówkach już obecnych w zeszycie (tego samego dnia). */
    fun nextNumber(headers: List<String>, millis: Long): Int {
        val day = header(millis, 0, true).substringBefore(',')
        return headers.count { it.startsWith(day) && (it.contains("zdjęcie") || it.contains("photo")) } + 1
    }
}
