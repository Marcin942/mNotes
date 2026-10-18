# mNotes: instrukcja dla agentów

Aplikacja na Androida (`com.mdev.mnotes`) zarządzana tak samo jak mChochlik (`~/AndroidStudioProjects/mChochlik`),
a instalowana i wydawana przez mStore. Wdrożenie projektu: `~/AndroidStudioProjects/mChochlik/docs/PORZADEK-PROJEKTOW.md`.

## Wspólna kultura pracy

Zasady pracy, commitów, punktów `Sprawdź:`, `land`, wersji i snapshotów, bezpiecznika pamięci Gradle'a, sekretów
i stylu kodu są wspólne i leżą w mChochliku (jedno źródło, zmiana działa od razu we wszystkich aplikacjach):

@~/AndroidStudioProjects/mChochlik/docs/KULTURA-PRACY.md

## Ta aplikacja

- `id` w rejestrze demona: `mnotes`; katalog: `~/AndroidStudioProjects/mNotes`.
- Scalanie: `PYTHONPATH=~/AndroidStudioProjects/mChochlik/server ~/AndroidStudioProjects/mChochlik/server/.venv/bin/python -m chochlik land --app mnotes <gałąź>`
  (z katalogu repozytorium, dowolny worktree).
- Testy: `~/AndroidStudioProjects/mChochlik/bin/gradle :app:assembleDebug :app:testDebugUnitTest` (kolejka Gradle'a;
  gołe `./gradlew` zatrzymuje hook). Gotową gałąź kończysz własnym `land` (polecenie wyżej), bez czekania na prośbę.
- Snapshot na życzenie: `~/AndroidStudioProjects/mChochlik/bin/snapshot --app mnotes`.
- **Wersje jeszcze nie z gita.** `versionCode`/`versionName` w `app/build.gradle.kts` są na sztywno (1 i `0.1.0`),
  więc mStore nie odróżnia snapshotów, a „Wydanie” nie zadziała. Do czasu wdrożenia formuły z
  `PORZADEK-PROJEKTOW.md` §3 (osobna gałąź, potem `version_prop` w rejestrze) nie obiecuj właścicielowi działających
  snapshotów i nie wpisuj tych wersji ręcznie jako obejścia.
- **Zakres i stan prac:** `docs/features/BACKLOG.md` (jedyne źródło prawdy o zakresie) i `docs/STAN-PRACY.md`
  (zacznij tutaj po wznowieniu sesji; aktualizuj oba na końcu partii zmian).
- **Formaty przyjmowane przez mNotes („Udostępnij”, „Otwórz w aplikacji”, importy).** Decyzja właściciela (2026-10-18):
  do mNotes mogą trafić różne zdjęcia, dokumenty, `.sdocx` (Samsung Notes) i podobne pliki, **we wszystkich używanych
  formatach**. Wybierając, jakie typy plików aplikacja przyjmuje (filtry intencji w `AndroidManifest.xml`, rozpoznanie
  w `FileKinds`/`ImportProbe`, selektory plików), nie zawężaj ich do kilku typów „na oko”: przy SEND Android dopasowuje
  tylko typ MIME, a aplikacje źródłowe podają własne albo ogólne typy. Domyślnie przyjmuj szeroko (`*/*`), a rodzaj pliku
  ustalaj po zawartości; zawężaj tylko po zmierzeniu, jaki typ naprawdę przychodzi (BACKLOG LIB-017).
- **Ograniczenia aplikacji:** w pełni offline (brak INTERNET, brak telemetrii), brak nowych zależności Gradle bez
  zgody właściciela. Czysta logika leży w `app/src/main/java/com/mdev/mnotes/core/` i ma testy jednostkowe.
- **Tablet właściciela (adb `R5GL12C1HMT`) jest realnie używany**, nie jest sprzętem testowym. Zasady (sprawdzenie
  pierwszego planu i zmian zeszytów przed `install`/`am start`/`input`, tylko zeszyt „Test wyszukiwania”) są w
  `docs/STAN-PRACY.md`; przeczytaj je przed jakimkolwiek poleceniem `adb`.
- Zmiany niezacommitowane w głównym drzewie (`~/AndroidStudioProjects/mNotes`) to praca właściciela w toku: nie ruszaj.
