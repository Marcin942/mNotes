package com.mdev.mnotes.core.goodnotes

import com.mdev.mnotes.core.goodnotes.PdfSerializer.ascii
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Składa nowy PDF z wybranych stron innego PDF-a (tło stron, których nie umiemy w całości zamienić na obiekty mNotes).
 * Treść strony podaje wołający (z wygaszonymi elementami, które stały się obiektami mNotes), a zasoby są kopiowane
 * razem z tym, do czego się odwołują; strumienie przepisujemy bez rozpakowania. Adnotacje, zakładki i opisy struktury
 * odpadają, bo odwołują się do drzewa stron oryginału.
 */
object PdfWriter {
    /** Strona do przepisania. [content] null = zostaw oryginalne strumienie /Contents. */
    class Source(val page: PdfPage, val content: ByteArray?)

    private val KEEP = listOf("Group")

    fun write(doc: PdfDocument, sources: List<Source>): ByteArray {
        val bodies = ArrayList<ByteArray?>()
        val mapped = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()

        fun alloc(): Int { bodies.add(null); return bodies.size }
        val catalog = alloc()
        val pagesNode = alloc()

        fun isTree(o: Any?): Boolean {
            val d = (o as? PdfDict) ?: (o as? PdfStream)?.dict ?: return false
            val t = d.name("Type")
            return t == "Page" || t == "Pages" || t == "Catalog"
        }

        // numery ujemne to obiekty nowego pliku (drzewo stron, treść): przechodzą bez zmian, dodatnie to odwołania do oryginału
        fun mapRef(r: PdfObjRef): PdfObjRef? {
            if (r.num < 0) return PdfObjRef(-r.num, 0)
            val target = doc.obj(r.num) ?: return null
            if (isTree(target)) return null
            val n = mapped.getOrPut(r.num) { alloc().also { queue.add(r.num) } }
            return PdfObjRef(n, 0)
        }

        fun serialize(v: Any?): ByteArray {
            val out = ByteArrayOutputStream()
            if (v is PdfStream) {
                val d = PdfDict(LinkedHashMap(v.dict.map))
                d["Length"] = v.raw.size.toDouble()
                PdfSerializer.write(d, out, ::mapRef)
                out.ascii("\nstream\n")
                out.write(v.raw)
                out.ascii("\nendstream")
            } else {
                PdfSerializer.write(v, out, ::mapRef)
            }
            return out.toByteArray()
        }

        val pageRefs = ArrayList<PdfObjRef>()
        for (src in sources) {
            val pg = src.page
            val pageNum = alloc()
            pageRefs.add(PdfObjRef(-pageNum, 0))
            val d = PdfDict()
            d["Type"] = PdfName("Page")
            d["Parent"] = PdfObjRef(-pagesNode, 0)
            d["MediaBox"] = pg.mediaBox.map { it }
            if (!pg.cropBox.contentEquals(pg.mediaBox)) d["CropBox"] = pg.cropBox.map { it }
            if (pg.rotate != 0) d["Rotate"] = pg.rotate.toDouble()
            d["Resources"] = pg.resources
            for (k in KEEP) pg.dict[k]?.let { d[k] = it }
            if (src.content == null) {
                pg.dict["Contents"]?.let { d["Contents"] = it }
            } else {
                val cn = alloc()
                bodies[cn - 1] = streamObject(src.content)
                d["Contents"] = PdfObjRef(-cn, 0)
            }
            bodies[pageNum - 1] = serialize(d)
        }
        while (queue.isNotEmpty()) {
            val old = queue.removeFirst()
            val n = mapped.getValue(old)
            bodies[n - 1] = serialize(doc.obj(old))
        }

        val cat = PdfDict()
        cat["Type"] = PdfName("Catalog")
        cat["Pages"] = PdfObjRef(-pagesNode, 0)
        bodies[catalog - 1] = serialize(cat)
        val pages = PdfDict()
        pages["Type"] = PdfName("Pages")
        pages["Kids"] = pageRefs
        pages["Count"] = pageRefs.size.toDouble()
        bodies[pagesNode - 1] = serialize(pages)

        val out = ByteArrayOutputStream()
        out.ascii("%PDF-1.7\n%âãÏÓ\n")
        val offsets = IntArray(bodies.size)
        bodies.forEachIndexed { i, b ->
            offsets[i] = out.size()
            out.ascii("${i + 1} 0 obj\n")
            out.write(b ?: "null".toByteArray())
            out.ascii("\nendobj\n")
        }
        val xref = out.size()
        out.ascii("xref\n0 ${bodies.size + 1}\n")
        out.ascii("0000000000 65535 f \n")
        for (o in offsets) out.ascii("%010d 00000 n \n".format(o))
        out.ascii("trailer\n<</Size ${bodies.size + 1} /Root $catalog 0 R>>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    private fun streamObject(content: ByteArray): ByteArray {
        val def = Deflater(6)
        def.setInput(content)
        def.finish()
        val z = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (!def.finished()) { val c = def.deflate(buf); z.write(buf, 0, c) }
        def.end()
        val out = ByteArrayOutputStream()
        out.ascii("<</Filter /FlateDecode /Length ${z.size()}>>\nstream\n")
        out.write(z.toByteArray())
        out.ascii("\nendstream")
        return out.toByteArray()
    }
}
