package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** 3c-A11: czcionki dołączone do aplikacji — zapis numeru rodziny, zgodność ze starymi polami, eksport bez czcionki. */
class FontFamilyTest {

    private fun box(family: Int) = TextBox(Ids.next(), 100f, 100f, 200f, 30f, 0f, "Zażółć αβγ x²", 14f, 0xFF000000.toInt(), family)

    @Test
    fun everyFamilySurvivesSaveAndLoad() {
        val all = (TextBox.FAMILY_SANS..TextBox.FAMILY_CAVEAT).map(::box)
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(all)))
        assertEquals(all.map { it.family }, back.elements.map { (it as TextBox).family })
    }

    /** Pole bez jawnej rodziny (stare zeszyty, import) jest dalej bezszeryfowe systemowe, nie nowa domyślna Literata. */
    @Test
    fun oldFieldsKeepTheSystemLook() {
        assertEquals(TextBox.FAMILY_SANS, box(TextBox.FAMILY_SANS).family)
        assertEquals(TextBox.FAMILY_SANS, TextBox(1, 0f, 0f, 10f, 10f, 0f, "a", 12f, 0).family)
        for (f in TextBox.FAMILY_SANS..TextBox.FAMILY_HAND) assertNull("rodzina $f jest systemowa", TextBox.fontName(f))
        assertEquals(TextBox.FAMILY_LITERATA, TextBox.FAMILY_DEFAULT)
    }

    @Test
    fun bundledFamiliesMapToTheirKindForExport() {
        assertEquals(TextBox.FAMILY_SERIF, TextBox.generic(TextBox.FAMILY_LITERATA))
        assertEquals(TextBox.FAMILY_SANS, TextBox.generic(TextBox.FAMILY_INTER))
        assertEquals(TextBox.FAMILY_SANS, TextBox.generic(TextBox.FAMILY_ATKINSON))
        assertEquals(TextBox.FAMILY_MONO, TextBox.generic(TextBox.FAMILY_JETBRAINS))
        assertEquals(TextBox.FAMILY_HAND, TextBox.generic(TextBox.FAMILY_CAVEAT))
        assertEquals("nieznany numer z nowszej wersji", TextBox.FAMILY_SANS, TextBox.generic(42))
        for (f in TextBox.FAMILY_LITERATA..TextBox.FAMILY_CAVEAT) assertNotNull(TextBox.fontName(f))
    }

    /** Każda dołączona rodzina ma plik w assets i licencję OFL obok (bez pliku krój po cichu byłby systemowy). */
    @Test
    fun fontFilesAndLicencesAreBundled() {
        val dir = listOf(File("src/main/assets/fonts"), File("app/src/main/assets/fonts")).first { it.isDirectory }
        for (name in listOf("literata", "inter", "atkinson", "atkinson-bold", "jetbrainsmono", "caveat")) {
            val f = File(dir, "$name.ttf")
            assert(f.isFile && f.length() > 10_000) { "brak $f" }
        }
        val licences = File(dir, "licencje").listFiles().orEmpty().filter { it.name.startsWith("OFL-") }
        assertEquals(5, licences.size)
        assert(licences.all { it.readText().contains("SIL OPEN FONT LICENSE") }) { "licencja OFL" }
        assert(dir.walkTopDown().filter { it.extension == "ttf" }.sumOf { it.length() } < 4_000_000) { "budżet 4 MB" }
    }
}
