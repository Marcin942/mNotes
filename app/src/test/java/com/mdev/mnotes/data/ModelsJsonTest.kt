package com.mdev.mnotes.data

import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsJsonTest {
    @Test fun notebookRoundTrip() {
        val spec = PageSpec.of(PageFormat.A4, true, Template.GRID)
        val pages = listOf(PageMeta("p1", spec), PageMeta("p2", spec))
        val n = NotebookMeta(
            "n1", "Zeszyt \"ąę\"", "f1", 10L, 20L, 30L, 0xFF112233.toInt(), CoverStyle.STRIPES,
            favorite = true, trashedAt = 5L, tags = listOf("a", "b"), lastPage = 1, layout = 1,
            pages = pages, bookmarks = listOf(Bookmark("p2", "Strona 2")),
        )
        val back = NotebookMeta.fromJson(n.toJson())
        assertEquals(n, back)
    }

    @Test fun folderRoundTrip() {
        val f = Folder("f1", "Szkoła", "root", 0xFF336699.toInt(), 7L)
        assertEquals(f, Folder.fromJson(JSONObject(f.toJson().toString())))
        val top = Folder("f2", "X")
        assertNull(Folder.fromJson(JSONObject(top.toJson().toString())).parentId)
    }
}
