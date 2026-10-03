# mNotes

Szybka, w pełni offline aplikacja do notatek odręcznych na Androida (rysik, tablet) — zeszyty, foldery,
import i adnotacja PDF-ów (slajdy z zajęć), szablony stron, wyszukiwanie. Bez internetu, bez kont.

- `app/src/main/java/com/mdev/mnotes/core/` — czysta logika (geometria, model strony, kodek, układ, cofanie), testowana jednostkowo.
- `docs/features/` — specyfikacja funkcji i `BACKLOG.md` (źródło prawdy o postępie).
- `docs/STAN-PRACY.md` — **zacznij tutaj po wznowieniu sesji**: co zrobione, co dalej, jak testować.

Rozpoznawanie wzorów (wzór → LaTeX) jest świadomie poza zakresem; może wrócić w odległej przyszłości.

Budowanie: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
