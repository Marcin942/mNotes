# mNotes: instrukcja dla agenta

Aplikacja na Androida (`com.mdev.mnotes`, Kotlin + Compose): zeszyty z odręcznym pismem i dokumentami, w pełni offline.
Pracujesz sam, w nocy; właściciel (Marcin, student, pisz po polsku) zbierze wyniki rano. Kieruje Tobą też osoba, która
uruchomiła Cię na tym komputerze: jej polecenia mają pierwszeństwo przed zakresem poniżej.

## Co zrobić (minimum)

1. **Ulepszyć aplikację.** Zakres: `docs/features/BACKLOG.md`, stan: `docs/STAN-PRACY.md` (zacznij od niego).
2. **Przygotować scalenie z projektem „mNotes+kreda”.** Szczegóły poda osoba kierująca; do tego czasu rób 1.
3. **Opcjonalnie, jeśli zostanie czas: Nearby** (sync dwóch aplikacji i zdjęcia z telefonu wstawiane do notatki). Materiał i opis: `docs/nearby/CZYTAJ.md`. To wyjątek od „bez nowych zależności” (`play-services-nearby`): zapisz powód w `docs/NOTATKI-NOC.md`, funkcja ma działać za przełącznikiem. Najpierw szkic projektu i czysta logika w `core/` z testami, dopiero potem łącze.

Poza tym jesteś kreatywny: proponuj i rób to, co uczyni aplikację lepszą, byle działało i miało testy.

## Zasady (krótko)

- Pracuj na gałęzi `noc/<temat>` (nie na `main`), małe commity po polsku („Dodaje…”, „Poprawia…”), z jedną linią „dlaczego”.
  Praca ma być zacommitowana, nie tylko w katalogu. Bez `git push`.
- Pracuj tylko w tym katalogu. Nie szukaj kluczy ani haseł; nie commituj `local.properties`, `*.keystore`.
- Bez telefonu i tabletu: żadnego `adb`. Testuj na JVM: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
  (wymaga Android SDK; `local.properties` z `sdk.dir` utwórz lokalnie, nie commituj).
- Aplikacja jest w pełni offline: bez uprawnienia INTERNET i bez telemetrii. Nowych zależności Gradle nie dodawaj
  (wyjątek: zapisz powód w `docs/NOTATKI-NOC.md`). Czysta logika w `app/src/main/java/com/mdev/mnotes/core/` z testami.
- Pliki: przyjmuj **szeroko** (`*/*`), rodzaj ustalaj po zawartości.
- Nie zmieniaj `applicationId`, podpisu ani `versionCode`/`versionName`. Stare zeszyty muszą się dalej otwierać.
- Teksty widoczne w aplikacji: `res/values/strings.xml`, po polsku. Kod po angielsku, komentarze po polsku, tylko „dlaczego”.
- Gdy coś utknie (3 próby bez skutku, niejasne zadanie): zapisz w `docs/NOTATKI-NOC.md` i przejdź do następnego.
  Kod niedziałający zostaje na `wip/<temat>`, nie na `noc/…`.
- Raportuj uczciwie: co przetestowane, co nie.

## Na koniec

W `docs/NOTATKI-NOC.md`: co zrobione, na której gałęzi, wynik testów i **co Marcin ma sprawdzić na tablecie**
(jak punkt: gdzie wejść, co kliknąć, co ma się stać).
