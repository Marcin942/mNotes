package com.mdev.mnotes.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Stan, który musi przeżyć zamknięcie aktywności przez system (aparat na pierwszym planie), czytany ze źródeł. */
class SavedStateTest {
    /** 2c-A15: plik zdjęcia z trybu wykładu w rememberSaveable — inaczej wynik aparatu trafia w pusty stan i ginie. */
    @Test fun lecturePhotoPathIsSaveable() {
        val src = File("src/main/java/com/mdev/mnotes/ui/LectureMode.kt").readText()
        assertTrue(Regex("""val pending = rememberSaveable \{""").containsMatchIn(src))
    }
}
