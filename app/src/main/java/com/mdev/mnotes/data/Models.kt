package com.mdev.mnotes.data

import com.mdev.mnotes.core.text.NameText
import com.mdev.mnotes.core.model.PageSpec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

data class PageMeta(val id: String, val spec: PageSpec)

data class Bookmark(val pageId: String, val title: String)

/** Wzór wzoru okładki (rysowany w UI); kody są częścią formatu. */
object CoverStyle {
    const val PLAIN = 0
    const val STRIPES = 1
    const val DOTS = 2
    const val WAVES = 3
    const val GRID = 4
    const val LINEN = 5
    const val ARCS = 6
    const val COUNT = 7
}

data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val color: Int = 0xFF87CF3E.toInt(),
    val trashedAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("c", color).put("t", trashedAt)
        .also { if (parentId != null) it.put("p", parentId) }

    companion object {
        fun fromJson(o: JSONObject) = Folder(o.getString("id"), NameText.oneLine(o.optString("n", "?")), o.optString("p", "").ifEmpty { null }, o.optInt("c", 0xFF87CF3E.toInt()), o.optLong("t", 0L))
    }
}

data class NotebookMeta(
    val id: String,
    val title: String,
    val folderId: String? = null,
    val created: Long,
    val modified: Long,
    val opened: Long = modified,
    val cover: Int = 0xFF4C9A6A.toInt(),
    val coverStyle: Int = CoverStyle.PLAIN,
    val favorite: Boolean = false,
    val trashedAt: Long = 0L,
    val tags: List<String> = emptyList(),
    val locked: Boolean = false,
    val lastPage: Int = 0,
    /** 0 = pionowo, 1 = poziomo. */
    val layout: Int = 0,
    val pages: List<PageMeta> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
) {
    val isTrashed: Boolean get() = trashedAt > 0L

    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1).put("id", id).put("title", title).put("cr", created).put("mod", modified).put("op", opened)
        o.put("cov", cover).put("cs", coverStyle).put("fav", favorite).put("tr", trashedAt).put("lock", locked)
        o.put("last", lastPage).put("lay", layout)
        if (folderId != null) o.put("f", folderId)
        o.put("tags", JSONArray(tags))
        val pg = JSONArray()
        for (p in pages) pg.put(JSONObject().put("id", p.id).put("s", JSONObject(p.spec.toJson())))
        o.put("pages", pg)
        val bm = JSONArray()
        for (b in bookmarks) bm.put(JSONObject().put("p", b.pageId).put("t", b.title))
        o.put("bm", bm)
        return o.toString()
    }

    companion object {
        fun fromJson(json: String): NotebookMeta {
            val o = JSONObject(json)
            val pages = ArrayList<PageMeta>()
            o.optJSONArray("pages")?.let { a ->
                for (i in 0 until a.length()) {
                    val p = a.getJSONObject(i)
                    pages.add(PageMeta(p.getString("id"), PageSpec.fromJson(p.getJSONObject("s").toString())))
                }
            }
            val tags = ArrayList<String>()
            o.optJSONArray("tags")?.let { a -> for (i in 0 until a.length()) tags.add(a.getString(i)) }
            val bms = ArrayList<Bookmark>()
            o.optJSONArray("bm")?.let { a ->
                for (i in 0 until a.length()) { val b = a.getJSONObject(i); bms.add(Bookmark(b.getString("p"), b.optString("t", ""))) }
            }
            val created = o.optLong("cr", 0L)
            val modified = o.optLong("mod", created)
            return NotebookMeta(
                id = o.getString("id"), title = NameText.oneLine(o.optString("title", "")), folderId = o.optString("f", "").ifEmpty { null },
                created = created, modified = modified, opened = o.optLong("op", modified),
                cover = o.optInt("cov", 0xFF4C9A6A.toInt()), coverStyle = o.optInt("cs", 0),
                favorite = o.optBoolean("fav"), trashedAt = o.optLong("tr"), tags = tags, locked = o.optBoolean("lock"),
                lastPage = o.optInt("last"), layout = o.optInt("lay"), pages = pages, bookmarks = bms,
            )
        }
    }
}
