package com.mdev.mnotes.data

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PdfRef
import com.mdev.mnotes.core.samsung.ConvertedNote
import com.mdev.mnotes.core.samsung.SdBackground
import com.mdev.mnotes.render.SamsungBackground
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Zapisuje notatkę z Samsung Notes (po konwersji i zatwierdzeniu w oknie importu) jako zeszyt mNotes. Tła, które nie
 * są zwykłym wzorem mNotes, trafiają do jednego PDF-a w zasobach zeszytu (po jednej stronie na różne tło), a strony
 * odwołują się do niego jak do tła zaimportowanego PDF-a. Zwrócone meta trzeba zarejestrować przez `repo.addNotebook`
 * na wątku UI; samo zapisywanie robić poza nim.
 */
class SamsungImporter(private val repo: Repository) {
    private val cover = 0xFF3E7CB1.toInt()

    fun save(note: ConvertedNote, title: String, folderId: String?): NotebookMeta {
        val id = newId()
        try {
            val backgrounds = note.pages.mapNotNull { it.background }.distinct()
            val refs = HashMap<SdBackground, PdfRef>()
            if (backgrounds.isNotEmpty()) {
                val pdf = ByteArrayOutputStream().also { SamsungBackground.writePdf(backgrounds, it) }.toByteArray()
                val asset = repo.importAsset(id, ByteArrayInputStream(pdf), "pdf")
                backgrounds.forEachIndexed { i, bg -> refs[bg] = PdfRef(asset, i) }
            }
            val now = System.currentTimeMillis()
            val pages = note.pages.map { cp ->
                val pageId = newId()
                val spec = cp.background?.let { cp.spec.copy(pdf = refs.getValue(it)) } ?: cp.spec
                if (cp.strokes.isNotEmpty()) repo.writePage(id, pageId, PageCodec.encode(PageContent.of(cp.strokes)))
                PageMeta(pageId, spec)
            }
            return NotebookMeta(id, title, folderId, now, now, now, cover, CoverStyle.PLAIN, pages = pages)
        } catch (e: Throwable) {
            repo.dir(id).deleteRecursively()
            throw e
        }
    }
}
