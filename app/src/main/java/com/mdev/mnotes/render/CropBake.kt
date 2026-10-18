package com.mdev.mnotes.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.mdev.mnotes.core.edit.CropGeom
import com.mdev.mnotes.core.model.ImageCrop
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * „Wypalanie” przycięcia: kopia samej widocznej części obrazu, wyprostowana do prostokąta wycinka.
 * Eksport do PDF i SVG rysuje taką kopię zamiast całego pliku, bo format wektorowy zachowałby cały obraz
 * (przycięcie byłoby tylko maską), a przycięty fragment ma zniknąć z pliku bezpowrotnie.
 */
object CropBake {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * Nowa bitmapa z prostokątem [crop] obrazu [src] (zawsze nowa, nigdy [src], więc można ją zwolnić).
     * Rozmiar nie przekracza [maxW] × [maxH] pikseli i nie jest większy niż oryginał.
     */
    fun bake(src: Bitmap, crop: ImageCrop, maxW: Int, maxH: Int): Bitmap {
        val f = CropGeom.frameOf(crop, src.width.toFloat(), src.height.toFloat())
        val natW = (2f * f.hw).coerceAtLeast(1f); val natH = (2f * f.hh).coerceAtLeast(1f)
        val q = min(1f, min(maxW / natW, maxH / natH)).coerceAtLeast(1e-3f)
        val ow = (natW * q).roundToInt().coerceAtLeast(1)
        val oh = (natH * q).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        m.postTranslate(-f.cx, -f.cy)
        m.postRotate(Math.toDegrees(-f.a.toDouble()).toFloat())
        m.postScale(ow / natW, oh / natH)
        m.postTranslate(ow * 0.5f, oh * 0.5f)
        Canvas(out).drawBitmap(src, m, paint)
        return out
    }
}
