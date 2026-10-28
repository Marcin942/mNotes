# Jak pomóc przy mNotes

> Szkic (3a-2). Ostateczną wersję zatwierdza autor projektu przed publikacją.

mNotes to zeszyt do pisania rysikiem na Androidzie, działający w pełni offline. Zmiany są mile widziane, pod warunkiem,
że aplikacja zostaje taka, jaka jest: lokalna, bez kont i bez telemetrii.

## Budowanie

- JDK 21 i Android SDK z platformą 36.1 (`compileSdk = release(36) { minorApiLevel = 1 }`). Gradle przychodzi
  z wrapperem (`./gradlew`), więc nie trzeba go instalować osobno.
- Utwórz `local.properties` z wpisem `sdk.dir=/ścieżka/do/Android/Sdk`. Tego pliku nie commituj.
- Warianty:
  - `foss`: bez żadnej niewolnej biblioteki.
  - `gms`: dokłada łącze z telefonem przez Nearby z Usług Google Play.
- Budowanie: `./gradlew :app:assembleFossDebug` albo `./gradlew :app:assembleGmsDebug`.

## Testy

```sh
./gradlew :app:testFossDebugUnitTest :app:testGmsDebugUnitTest
```

Testy działają na JVM, bez urządzenia. Logikę (geometria, kodeki, wyszukiwanie, łącze) trzymamy w
`app/src/main/java/com/mdev/mnotes/core/` i każda zmiana tam ma mieć test. Na gałęzi `kreda` część testów czyta
prawdziwe zdjęcia tablic z katalogu podanego w `KREDA_DIR`. Bez tej zmiennej te testy się pomijają.

## Zasady

- **Offline.** Bez uprawnienia `INTERNET`, bez analityki i bez raportów o awariach wysyłanych w sieć.
- **Zależności.** Tylko wolne licencje (Apache-2.0, MIT, BSD). Biblioteki Google wyłącznie w wariancie `gms`
  (`src/gms/`), za przełącznikiem w ustawieniach.
- **Zgodność plików.** Stare zeszyty muszą się dalej otwierać. Pilnuje tego `FormatCompatTest` na plikach z
  `app/src/test/resources/format/`. Nowe pola zapisuj tak, żeby starsza wersja mogła je pominąć.
- **Pliki przyjmujemy szeroko** (`*/*`), a rodzaj rozpoznajemy po zawartości, nie po rozszerzeniu.
- **Kod.** Kod piszemy po angielsku, komentarze po polsku i tylko o tym, „dlaczego”. Teksty widoczne w aplikacji
  są po polsku i po angielsku.
- **Bez zmian identyfikatora.** Nie zmieniaj `applicationId`, podpisu ani `versionCode`/`versionName` bez zgody
  autora.
- **Commity.** Małe commity, każdy z jedną linią o tym, dlaczego powstał. Podpisuj je przez `git commit -s` (DCO:
  potwierdzasz, że masz prawo oddać tę zmianę na licencji projektu).

## Zgłaszanie błędów

Zakładaj zgłoszenie (Issue) i podaj w nim: urządzenie, wersję Androida, wariant (`foss`/`gms`), kroki do powtórzenia
i co miało się stać. **Błędy bezpieczeństwa**, np. w szyfrowaniu łącza z telefonem, zgłaszaj prywatnie
(„Report a vulnerability” na GitHubie), nie w publicznym zgłoszeniu.
