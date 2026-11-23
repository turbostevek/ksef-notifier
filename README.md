# ksef-importer

Lokalna aplikacja (Kotlin, Spring Boot, JVM 21) do importu faktur zakupowych z KSeF. Zakres i decyzje: [specyfikacja MVP](docs/spec/ksef-importer-mvp.md).

## Budowanie

```sh
./gradlew bootJar          # Windows: gradlew.bat bootJar
```

Wynik: `build/libs/ksef-importer-0.0.1-SNAPSHOT.jar` (fat JAR).

## Uruchomienie

```sh
java -jar build/libs/ksef-importer-0.0.1-SNAPSHOT.jar import --config=ksef-importer.yml
```

Parametr `--config` przyjmuje wyłącznie formę `--config=<plik>`; forma z spacją jest odrzucana (kod 2). Parametr jest opcjonalny; domyślnie używany jest plik `ksef-importer.yml` w bieżącym katalogu.

Polecenie `check-token` sprawdza, czy token KSeF jest zapisany w systemowym magazynie sekretów (wartości nie wypisuje):

```sh
java -jar build/libs/ksef-importer-0.0.1-SNAPSHOT.jar check-token
```

Polecenie `import` loguje się tokenem KSeF (z magazynu sekretów) na NIP z konfiguracji i pobiera faktury zakupowe (podmiot 2, nabywca). Pierwszy import startuje od `startDate` (od północy czasu polskiego, Europe/Warsaw), a kolejne od czasu ostatniego udanego importu. Faktury są dodawane do Historii w lokalnej bazie SQLite `invoice-history.db`, leżącej obok pliku konfiguracyjnego. Ponowny import nie tworzy duplikatów (klucz: numer KSeF). Pliki XML są przetwarzane w pamięci i nie są zapisywane na dysku.

Po imporcie wysyłany jest jeden zbiorczy e-mail na adres `recipientEmail` z fakturami zakupowymi do zapłaty, których nie ma jeszcze w powiadomieniach (faktury oznaczone jako opłacone przy wystawieniu są pomijane). Gdy nie ma takich faktur, e-mail nie jest wysyłany. Faktury trafiają do powiadomienia dopiero po udanej wysyłce. Błąd wysyłki (np. SMTP) jest tylko logowany i nie zmienia kodu wyjścia; kolejny przebieg ponawia wysyłkę zaległych faktur.

Kody wyjścia:

- `0` – konfiguracja poprawna, import zakończony (lub token zapisany, dla `check-token`)
- `1` – błąd konfiguracji (brak parametru, nieprawidłowa wartość, brak pliku) albo brak tokenu KSeF w magazynie sekretów; komunikat jest wypisywany na stderr
- `2` – brak polecenia `import` lub `check-token`
- `3` – błąd KSeF (token odrzucony lub wygasły, błąd eksportu, brak połączenia); komunikat jest logowany jako błąd i wypisywany na stderr
- `4` – nieoczekiwany błąd podczas importu (np. błąd wewnętrzny); komunikat jest logowany wraz z przyczyną i wypisywany na stderr

## Token KSeF

Token nie należy do pliku konfiguracyjnego. Jest przechowywany w systemowym magazynie sekretów pod nazwą `ksef-importer/ksef-token`.

Token testowy (środowisko `test`) generuje osobna aplikacja `ksef-token-generator-csharp` (C# / .NET 10, poza tym repozytorium, zob. [ADR 0003](docs/adr/0003-generator-tokenow-testowych-w-javie.md)). Uruchomienie: `dotnet run --no-build > token.out` (token na stdout, status na stderr). Sprawdzenie tokena: `dotnet run --no-build -- verify <nip> token.out`. Token zapisujesz w magazynie tak samo jak produkcyjny:

- Ubuntu (libsecret, pakiet `libsecret-tools`):
  ```sh
  secret-tool store --label='KSeF token' service ksef-importer account ksef-token
  ```
- Windows: Menedżer poświadczeń → Dane logowania systemu Windows → Dodaj poświadczenie ogólne, adres sieciowy `ksef-importer/ksef-token`, hasło = token.

Hasło SMTP (potrzebne, gdy w `smtp` jest `username`) zapisujesz tak samo, pod nazwą `ksef-importer/smtp-password`:

- Ubuntu: `secret-tool store --label='SMTP password' service ksef-importer account smtp-password`
- Windows: poświadczenie ogólne o adresie sieciowym `ksef-importer/smtp-password`.

Ręczne sprawdzenie przepływu uwierzytelniania na TEST (bez aplikacji): kolekcja Postman [docs/postman/ksef-test-auth.postman_collection.json](docs/postman/ksef-test-auth.postman_collection.json) i environment [docs/postman/ksef-test.postman_environment.json](docs/postman/ksef-test.postman_environment.json). W environment wklej token w pole `ksefToken` (nie commituj go), potem uruchom requesty 1 → 6 po kolei. Kolejność jest istotna: request 3 korzysta z certyfikatu z requestu 1 i challenge z requestu 2.

## Konfiguracja

Plik YAML bez sekretów. Token KSeF i hasło SMTP nie należą do tego pliku; klucze spoza poniższego schematu (np. `smtp.password`) powodują błąd.

Domyślne wartości są wbudowane w aplikację (plik [src/main/resources/ksef-importer-defaults.yml](src/main/resources/ksef-importer-defaults.yml)): `importTime: "07:30"`, `ksef.environment: test`, `smtp.port: 587`. Każdy klucz z pliku profilu (`--config`) nadpisuje wartość domyślną; sekcje `ksef` i `smtp` są scalane pole po polu. Dane podmiotu i SMTP (`nip`, `startDate`, `recipientEmail`, `smtp.host`, `smtp.from`) muszą być w profilu.

```yaml
nip: "1234563218"            # wymagane, NIP podmiotu: sprawdzany jest tylko format (10 cyfr), bez sumy kontrolnej, żeby wyłapać literówki
importTime: "07:30"          # wymagane, format HH:mm
startDate: 2026-01-01        # wymagane, data startowa pierwszego importu
recipientEmail: zona@example.com   # wymagane
ksef:
  environment: test          # opcjonalne: test (domyślnie) lub prod
smtp:
  host: smtp.example.com     # wymagane
  port: 587                  # opcjonalne, domyślnie 587
  username: ksef@example.com # opcjonalne
  from: ksef@example.com     # wymagane
```
