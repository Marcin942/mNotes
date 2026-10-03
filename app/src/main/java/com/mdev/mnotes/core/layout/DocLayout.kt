package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.PageSpec
import kotlin.math.max

enum class LayoutMode { VERTICAL, HORIZONTAL }

/**
 * Ułożenie stron w jednym wspólnym układzie dokumentu (pt). Strony leżą wzdłuż osi głównej
 * (pionowej lub poziomej) i są wyśrodkowane na osi poprzecznej.
 */
class DocLayout(
    private val widths: FloatArray,
    private val heights: FloatArray,
    val mode: LayoutMode,
    val gap: Float = DEFAULT_GAP,
    val pad: Float = DEFAULT_PAD,
) {
    val count: Int = widths.size

    private val starts = FloatArray(count)
    private val lefts = FloatArray(count)
    private val tops = FloatArray(count)

    val docWidth: Float
    val docHeight: Float

    init {
        require(widths.size == heights.size)
        var maxW = 0f
        var maxH = 0f
        for (i in 0 until count) {
            maxW = max(maxW, widths[i])
            maxH = max(maxH, heights[i])
        }
        var cursor = pad
        for (i in 0 until count) {
            starts[i] = cursor
            if (mode == LayoutMode.VERTICAL) {
                lefts[i] = pad + (maxW - widths[i]) / 2f
                tops[i] = cursor
                cursor += heights[i] + gap
            } else {
                lefts[i] = cursor
                tops[i] = pad + (maxH - heights[i]) / 2f
                cursor += widths[i] + gap
            }
        }
        val mainEnd = if (count == 0) 2f * pad else cursor - gap + pad
        if (mode == LayoutMode.VERTICAL) {
            docWidth = maxW + 2f * pad
            docHeight = mainEnd
        } else {
            docWidth = mainEnd
            docHeight = maxH + 2f * pad
        }
    }

    fun left(i: Int): Float = lefts[i]
    fun top(i: Int): Float = tops[i]
    fun width(i: Int): Float = widths[i]
    fun height(i: Int): Float = heights[i]
    fun rect(i: Int): Bounds = Bounds(lefts[i], tops[i], lefts[i] + widths[i], tops[i] + heights[i])

    /** Strona zawierająca punkt dokumentu albo -1, gdy punkt leży między stronami. */
    fun pageAt(x: Float, y: Float): Int {
        val i = nearest(x, y)
        if (i < 0) return -1
        return if (rect(i).contains(x, y)) i else -1
    }

    /** Strona najbliższa punktowi wzdłuż osi głównej — używana do „bieżącej strony”. */
    fun nearest(x: Float, y: Float): Int {
        if (count == 0) return -1
        val p = if (mode == LayoutMode.VERTICAL) y else x
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= p) lo = mid else hi = mid - 1
        }
        // Punkt w przerwie po stronie [lo] może być bliżej następnej strony.
        if (lo + 1 < count) {
            val end = starts[lo] + mainSize(lo)
            val nextStart = starts[lo + 1]
            if (p > end && (p - end) > (nextStart - p)) return lo + 1
        }
        return lo
    }

    /** Zakres stron, których prostokąt przecina [view]; pusty, gdy żadna. */
    fun visibleRange(view: Bounds): IntRange {
        if (count == 0) return IntRange.EMPTY
        val a = if (mode == LayoutMode.VERTICAL) view.t else view.l
        val b = if (mode == LayoutMode.VERTICAL) view.b else view.r
        var first = lowerBound(a)
        var last = lowerBound(b)
        while (first < count && starts[first] + mainSize(first) < a) first++
        while (last >= 0 && starts[last] > b) last--
        first = first.coerceAtLeast(0)
        last = last.coerceAtMost(count - 1)
        return if (first > last) IntRange.EMPTY else first..last
    }

    /** Ostatnia strona, której początek jest ≤ [p]; -1 gdy brak. Przy [p] przed pierwszą zwraca 0. */
    private fun lowerBound(p: Float): Int {
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= p) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun mainSize(i: Int): Float = if (mode == LayoutMode.VERTICAL) heights[i] else widths[i]

    companion object {
        const val DEFAULT_GAP = 14f
        const val DEFAULT_PAD = 20f

        fun of(specs: List<PageSpec>, mode: LayoutMode, gap: Float = DEFAULT_GAP, pad: Float = DEFAULT_PAD): DocLayout =
            DocLayout(
                FloatArray(specs.size) { specs[it].w },
                FloatArray(specs.size) { specs[it].h },
                mode, gap, pad,
            )
    }
}
