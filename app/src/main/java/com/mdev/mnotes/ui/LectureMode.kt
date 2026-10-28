package com.mdev.mnotes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mdev.mnotes.core.layout.BottomStack
import com.mdev.mnotes.editor.Editor
import com.mdev.mnotes.editor.T
import java.io.File

/**
 * Tryb wykładu (pomysł Fable nr 1): kapsuła u dołu płótna — „Zdjęcie na nową stronę” otwiera aparat systemowy,
 * zdjęcie trafia na nową stronę za bieżącą (z nagłówkiem daty i numeru), „✕” kończy tryb. Bez uprawnienia CAMERA:
 * zdjęcie robi aplikacja aparatu do pliku podanego przez FileProvider.
 */
@Composable
internal fun BoxScope.LectureControls(ed: Editor) {
    val ctx = LocalContext.current
    // 2c-A15: ścieżka w rememberSaveable — gdy system zamknie aktywność przy aparacie, zdjęcie po powrocie nie ginie.
    val pending = rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending.value?.let(::File)
        pending.value = null
        if (ok && f != null) ed.lectureShot(f) else f?.delete()
    }
    fun shoot() {
        val f = ed.lectureFile()
        pending.value = f.path
        // A2-8: bez aparatu tryb nie ma sensu — kończymy go, zamiast zostawiać kapsułę, która nic nie zrobi.
        runCatching { camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)) }
            .onFailure { pending.value = null; f.delete(); ed.lectureMode = false; ed.say(T.t("Brak aplikacji aparatu", "No camera app")) }
    }
    // 2b-A14: ten sam aparat dla trybu wykładu i przycisku „Zdjęcie tablicy” w nagłówku (bez kapsuły trybu).
    LaunchedEffect(ed.lectureShootTick) { if (ed.lectureShootTick > ed.lectureShootDone) { ed.lectureShootDone = ed.lectureShootTick; shoot() } }
    if (!ed.lectureMode) return
    FloatingCard(Modifier.align(Alignment.BottomCenter).padding(bottom = BottomStack.CAPSULE_BOTTOM.dp).panelEnter()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(T.t("Tryb wykładu", "Lecture mode"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
            // Chip akcji, nie przełącznik: nie wygląda na „wybrany” (A2-8).
            Chip(T.t("Zdjęcie na nową stronę", "Photo on a new page"), false, { shoot() }, icon = Icons.Rounded.PhotoCamera)
            IconBtn(Icons.Rounded.Close, T.t("Zakończ tryb wykładu", "End lecture mode"), { ed.lectureMode = false }, size = 32.dp, iconSize = 18.dp)
        }
    }
}
