# Rozwiązywanie problemów

| Objaw | Przyczyna i działanie |
|---|---|
| `ModuleNotFoundError: No module named 'PIL'` | `pip install pillow`, potem to samo polecenie. |
| „Nie mogę wyrenderować strony … (brak pdftoppm…)” | `pip install pymupdf` albo `apt-get install -y poppler-utils`, potem to samo polecenie. |
| „Nie umiem odczytać tego PDF-a” | Plik uszkodzony albo zaszyfrowany. Poproś o inny plik. Dla PDF z hasłem: `qpdf --decrypt` (jeśli jest) albo poproś o wersję bez hasła. |
| Zadań 0, UWAGA „skan” | PDF to obrazy. Plan ręczny: `plan-reczny.md`. |
| Zadań 0, ale PDF ma tekst | Nietypowa numeracja. Podaj `--task-regex`, np. `"^\s*Z\d+\."` (regex Pythona dopasowany od początku linii). |
| Za dużo zadań (np. wzory wzięte za numery) | Dodaj `--task-regex` z precyzyjnym wzorcem albo usuń fałszywe wpisy z `plan.json`. |
| Podpunkty nie wykryte | Skrypt rozpoznaje podpunkty `a)`/`(a)`, `i)`/`(i)`, `1)`/`(1)`, gdy są wcięte względem zadania i jest ich co najmniej 2. Inny zapis: `--sub-regex`. |
| Pierwszy wiersz zadania ucięty | Zwiększ `y0` przez edycję `plan.json` albo zmniejsz poprzedni `y1` (zob. `plan-reczny.md`). |
| Obrazki wystają poza stronę A5 | Ostrzeżenie z `build.py`: wycinek jest wyższy niż strona. Powiedz użytkownikowi, w mNotes można zmniejszyć obrazek palcem. |
| Brak wyniku `.pdf` | Brak backendu skalującego. `pip install pypdf`, a gdy się nie da, daj użytkownikowi `.mnotes` i powiedz o tym wprost. |
| `BrokenPipeError` | Wynik był przycięty `head`. Uruchom polecenie bez `| head`. |
| Pliku nie ma w `/mnt/user-data/outputs/` | Sprawdź wiersz „OK .mnotes: ścieżka” z `build.py` i wskaż użytkownikowi tę ścieżkę. |

Skrypty są deterministyczne: to samo wejście daje ten sam plan. Po każdej zmianie planu uruchom `build.py` od nowa (nadpisuje poprzedni wynik).
