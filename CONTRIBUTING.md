# Jak pomóc przy mNotes

mNotes to zeszyt do pisania rysikiem na Androidzie, działający w pełni offline. Zmiany są mile widziane, jeśli
aplikacja zostaje lokalna, bez kont i bez telemetrii.

## Budowanie i testy

Potrzebujesz JDK 21 i Android SDK z platformą 36.1. Utwórz `local.properties` z `sdk.dir=…` (tego pliku nie commituj).

```sh
./gradlew :app:testFossDebugUnitTest :app:testGmsDebugUnitTest   # testy na JVM, bez urządzenia
./gradlew :app:assembleFossDebug                                  # albo :app:assembleGmsDebug
```

Wariant `foss` ma tylko wolne biblioteki, a `gms` dokłada łącze z telefonem (Nearby z Usług Google Play albo
Bluetooth). Na gałęzi `kreda` część testów czyta zdjęcia tablic z katalogu `KREDA_DIR`. Bez tej zmiennej te testy
się pomijają.

## Zasady

- **Offline.** Bez uprawnienia `INTERNET`, bez analityki i bez wysyłania raportów o awariach.
- **Zależności.** Tylko wolne licencje zgodne z MPL-2.0 (np. Apache-2.0, MIT, BSD). Biblioteki Google wolno dodać
  wyłącznie w `src/gms/`.
- **Stare zeszyty muszą się otwierać.** Pilnuje tego `FormatCompatTest`. Nowe pola zapisuj tak, żeby starsza wersja
  mogła je pominąć.
- **Logika w `core/` z testem.** Kod piszemy po angielsku, a komentarze po polsku, tylko o tym, „dlaczego”. Teksty
  w aplikacji trzymamy w `strings.xml`.
- **Małe commity**, każdy z jednym zdaniem o tym, dlaczego powstał.

Wysyłając zmianę, zgadzasz się, że trafi do projektu na licencji MPL-2.0, tak jak reszta kodu.

## Zgłaszanie błędów

W zgłoszeniu (Issue) podaj urządzenie, wersję Androida, wariant (`foss`/`gms`), kroki do powtórzenia i to, co miało
się stać. **Błędy bezpieczeństwa**, np. w szyfrowaniu łącza z telefonem, zgłaszaj prywatnie („Report a
vulnerability” na GitHubie).
