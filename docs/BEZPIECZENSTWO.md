# Bezpieczeństwo mNotes — audyt

## Model zagrożeń

mNotes działa w pełni offline (bez uprawnienia INTERNET), więc główne drogi ataku to: **inna aplikacja na tym samym
urządzeniu** (intencje do wyeksportowanej aktywności, podsunięte adresy `content://`/`file://`, pliki w folderach
wspólnych), **wrogi plik** otwarty lub udostępniony przez użytkownika (zeszyt `.mnotes`, Samsung Notes `.sdocx`, PDF,
obraz), **obce urządzenie w pobliżu** przy łączu telefon–tablet (Nearby, Bluetooth) oraz **osoba z fizycznym dostępem**
do odblokowanego lub podłączonego urządzenia (zablokowane zeszyty, podgląd w ostatnich aplikacjach, debugowanie USB).
Cele atakującego: odczyt notatek, podrzucenie treści, awaria aplikacji albo utrata zeszytu, wyczerpanie pamięci.

## Znaleziska

| ID | Waga | Gdzie | Scenariusz | Stan |
|----|------|-------|------------|------|
| S-01 | średnie | paczka instalacyjna (wersja debug) | Instalowany APK jest w wersji debug (`debuggable=true`): z włączonym debugowaniem USB `run-as` daje pełny odczyt danych aplikacji, także zeszytów zablokowanych biometrią. | do decyzji (wymaga klucza wydania, D13/D14) |
| S-02 | niskie | manifest wersji debug (`PreviewActivity` z narzędzi Compose) | Wyeksportowana aktywność podglądu Compose w wersji debug: inna aplikacja może ją uruchomić z nazwą funkcji `@Composable`. | w analizie |

## Sprawdzone bez problemów

- `allowBackup="false"` w manifeście głównym; INTERNET usuwany także z bibliotek Usług Google (`tools:node="remove"`).
- `FileProvider` (`${applicationId}.files`): `exported=false`, zakres tylko `cache/share/`.
- `NoteWindowActivity`: `exported=false`.
- Komponenty bibliotek w scalonym manifeście: `WakeUpService` (Usługi Google) chroniony uprawnieniem podpisu Usług
  Google, `ProfileInstallReceiver` chroniony `android.permission.DUMP`, `InitializationProvider` niewyeksportowany.
- Kreda: dekodowanie zdjęć najpierw czyta wymiary, potem próbkuje (`DecodePlan`), więc dłuższy bok ograniczony niezależnie
  od wymiarów z nagłówka; wyjątki i brak pamięci łapane (`runCatching`). „Wyślij do mLabs” udostępnia tylko PNG strony
  i JSON (tytuł, numer strony, data, tekst wpisany ręcznie) przez systemowy wybór aplikacji — bez wskazanego pakietu;
  nazwa pliku z tytułu zeszytu przechodzi przez `fileStem` (tylko `a-z0-9-`), więc tytuł nie wyjdzie poza katalog.

## Stan

- 29.10: szkielet raportu; manifesty obu wariantów i kreda przejrzane. W toku: pliki z zewnątrz, łącze, dane w spoczynku.
