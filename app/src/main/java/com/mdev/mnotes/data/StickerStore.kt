package com.mdev.mnotes.data

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.edit.Sticker
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.text.NameText
import org.json.JSONObject
import java.io.File

/**
 * Naklejki (F19) w `files/library/stickers/<id>/`: `s.mnp` (elementy w formacie strony), `meta.json` (nazwa, rozmiar,
 * data) i `assets/` z kopiami obrazów. Wspólne dla wszystkich zeszytów. Wołać poza wątkiem UI.
 */
class StickerStore(libraryRoot: File) {
    data class Item(val id: String, val name: String, val w: Float, val h: Float, val created: Long)

    private val dir = File(libraryRoot, "stickers")

    fun list(): List<Item> = (dir.listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { d ->
        runCatching {
            val o = JSONObject(File(d, "meta.json").readText())
            Item(d.name, NameText.oneLine(o.optString("n", "")), o.optDouble("w", 0.0).toFloat(), o.optDouble("h", 0.0).toFloat(), o.optLong("c", 0L))
        }.getOrNull()
    }.sortedByDescending { it.created }

    /** Zapisuje naklejkę; [asset] daje plik obrazu o danej nazwie z zeszytu źródłowego. */
    fun save(name: String, n: Sticker.Normalized, asset: (String) -> File): Item {
        val id = newId()
        val d = File(dir, id).apply { mkdirs() }
        for (a in Sticker.assets(n.elements)) {
            val src = asset(a)
            if (src.exists()) src.copyTo(File(d, "assets/$a").apply { parentFile?.mkdirs() }, overwrite = true)
        }
        Repository.writeAtomic(File(d, "s.mnp"), PageCodec.encode(PageContent.of(n.elements)), false)
        val item = Item(id, NameText.oneLine(name).ifBlank { "Naklejka" }, n.w, n.h, System.currentTimeMillis())
        writeMeta(d, item)
        return item
    }

    fun load(id: String): Sticker.Normalized? = runCatching {
        val d = File(dir, id)
        val o = JSONObject(File(d, "meta.json").readText())
        Sticker.Normalized(PageCodec.decode(File(d, "s.mnp").readBytes()).elements, o.optDouble("w", 0.0).toFloat(), o.optDouble("h", 0.0).toFloat())
    }.getOrNull()

    fun assetFile(id: String, name: String) = File(dir, "$id/assets/$name")

    fun rename(id: String, name: String) {
        val it = list().firstOrNull { s -> s.id == id } ?: return
        writeMeta(File(dir, id), it.copy(name = NameText.oneLine(name).ifBlank { it.name }))
    }

    fun delete(id: String) { File(dir, id).deleteRecursively() }

    private fun writeMeta(d: File, i: Item) {
        val o = JSONObject().put("n", i.name).put("w", i.w.toDouble()).put("h", i.h.toDouble()).put("c", i.created)
        Repository.writeAtomic(File(d, "meta.json"), o.toString().toByteArray(), false)
    }
}
