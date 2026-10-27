package com.mdev.mnotes.data

import com.mdev.mnotes.core.edit.Sticker
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** F19: magazyn naklejek na dysku. */
class StickerStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun saveListLoadRenameDelete() {
        val lib = tmp.newFolder("library")
        val nbAssets = tmp.newFolder("assets")
        File(nbAssets, "foto.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val store = StickerStore(lib)
        val els = listOf(
            Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, floatArrayOf(10f, 10f, 1f, 30f, 40f, 1f)),
            ImageEl(Ids.next(), "foto.jpg", 50f, 50f, 20f, 20f),
        )
        val item = store.save("  Benzen\n", Sticker.normalize(els)!!) { File(nbAssets, it) }
        assertEquals("Benzen", item.name)
        assertEquals(listOf(item.id), store.list().map { it.id })
        val back = store.load(item.id)
        assertNotNull(back)
        assertEquals(2, back!!.elements.size)
        assertTrue(store.assetFile(item.id, "foto.jpg").exists())
        store.rename(item.id, "Pierścień benzenowy")
        assertEquals("Pierścień benzenowy", store.list()[0].name)
        store.delete(item.id)
        assertTrue(store.list().isEmpty())
    }
}
