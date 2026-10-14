# Ręczna poprawka planu (`plan.json`)

Używaj, gdy podglądy z `analyze.py` pokazują błąd albo gdy PDF to skan / ma dwie kolumny. Plan to zwykły JSON, edytuj go narzędziem do edycji plików (nie przepisuj całości z pamięci).

## Format

```json
{
 "version": 1,
 "title": "Analiza 3, lista 16",
 "pdf": "/mnt/user-data/uploads/an3.pdf",
 "page_sizes": [[595.28, 841.89]],
 "x_range": [51.1, 514.0],
 "tasks": [
  {
   "label": "Zadanie 16.1",
   "intro": [{"page": 1, "y0": 140.6, "y1": 156.4}],
   "subs": [
    {"label": "(i)",  "segs": [{"page": 1, "y0": 158.3, "y1": 188.0}]},
    {"label": "(ii)", "segs": [{"page": 1, "y0": 190.0, "y1": 220.1}]}
   ]
  },
  {
   "label": "Zadanie 16.2.",
   "intro": [{"page": 1, "y0": 268.4, "y1": 334.2}],
   "subs": []
  }
 ]
}
```

- `page`: numer strony PDF **od 1**. `y0`, `y1`: pozycje **w punktach od góry strony** (1 pt = 1/72 cala; A4 ma 595×842 pt). `y0 < y1`.
- `x_range`: lewa i prawa granica wycinków w pt, wspólna dla wszystkich (obejmij cały tekst ze wzorami; dla A4 zwykle ok. 50–545).
- `intro`: treść zadania **przed podpunktami** (albo cała treść, gdy podpunktów brak). Lista wycinków, więc zadanie dzielące się na dwie strony PDF ma dwa wpisy (kolejno), a skrypt skleja je w jeden obrazek.
- `subs`: podpunkty w kolejności. Każdy ma `label` i `segs` (też lista). Zadanie bez podpunktów ma `"subs": []`.
- Pole `hint` jest opcjonalne i ignorowane przy budowaniu (możesz je usunąć).
- Tekst po ostatnim podpunkcie (np. zakończenie polecenia) włącz do `segs` ostatniego podpunktu.

## Jak ustalić współrzędne

1. Obejrzyj `podglad-strN.png` z `analyze.py`: czerwona linijka z lewej ma podziałkę co 50 pt. Wartości odczytuj z niej (w obrazie 1 pt = 1,6 piksela).
2. Granicę między dwoma wycinkami ustaw w środku białej przerwy między ich treścią (nie w tekście, nie przez wzór).
3. Dla skanu bez tekstu: ułóż `tasks` całkowicie ręcznie (po jednym wpisie na zadanie), `label` dowolny („Zadanie 1”).
4. Sprawdź plan: `python3 "$SK/preview.py" plan.json --out /tmp/zm_podglad` i obejrzyj obrazy. Dopiero potem `build.py`.

## Typowe poprawki

- **Za mało zadań** (skrypt nie widzi numeru): dodaj wpis zadania, skopiuj `y1` poprzedniego zadania jako `y0` nowego i skoryguj `y1` poprzedniego.
- **Fałszywe zadanie** (np. nagłówek wzięty za zadanie): usuń wpis i wydłuż `y1` poprzedniego, jeśli treść do niego należy.
- **Ucięty wzór**: zwiększ `y1` (zwykle o 2–6 pt) i odpowiednio `y0` następnego elementu.
- **Dwie kolumny**: dla każdego zadania w lewej kolumnie podaj wycinek z `x_range` lewej połowy. Ponieważ `x_range` jest wspólny, zrób wtedy osobne PDF-y: wytnij kolumny narzędziem (np. `gs`/`pdftocairo -x -y -W -H`) albo poproś użytkownika o inny plik. Nie ukrywaj tego ograniczenia: powiedz je w odpowiedzi.
