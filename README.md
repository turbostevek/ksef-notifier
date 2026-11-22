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

Polecenie `import` loguje się tokenem KSeF (z magazynu sekretów) na NIP z konfiguracji, pobiera faktury zakupowe (podmiot 2, nabywca) od `startDate` do chwili obecnej i zlicza je. Pliki faktur są przetwarzane w pamięci i nie są zapisywane na dysku. Zapis do Historii i powiadomienia są poza zakresem tego polecenia.

Kody wyjścia:

- `0` – konfiguracja poprawna, import zakończony (lub token zapisany, dla `check-token`)
- `1` – błąd konfiguracji (brak parametru, nieprawidłowa wartość, brak pliku) albo brak tokenu KSeF w magazynie sekretów; komunikat jest wypisywany na stderr
- `2` – brak polecenia `import` lub `check-token`
- `3` – błąd KSeF (token odrzucony lub wygasły, błąd eksportu, brak połączenia); komunikat jest logowany jako błąd i wypisywany na stderr

## Token KSeF

Token nie należy do pliku konfiguracyjnego. Jest przechowywany w systemowym magazynie sekretów pod nazwą `ksef-importer/ksef-token`.

Token testowy (środowisko `test`) generuje osobna aplikacja `ksef-token-generator-csharp` (C# / .NET 10, poza tym repozytorium, zob. [ADR 0003](docs/adr/0003-generator-tokenow-testowych-w-javie.md)). Uruchomienie: `dotnet run --no-build > token.out` (token na stdout, status na stderr). Sprawdzenie tokena: `dotnet run --no-build -- verify <nip> token.out`. Token zapisujesz w magazynie tak samo jak produkcyjny:

- Ubuntu (libsecret, pakiet `libsecret-tools`):
  ```sh
  secret-tool store --label='KSeF token' service ksef-importer account ksef-token
  ```
- Windows: Menedżer poświadczeń → Dane logowania systemu Windows → Dodaj poświadczenie ogólne, adres sieciowy `ksef-importer/ksef-token`, hasło = token.

Ręczne sprawdzenie przepływu uwierzytelniania na TEST (bez aplikacji): kolekcja Postman [docs/postman/ksef-test-auth.postman_collection.json](docs/postman/ksef-test-auth.postman_collection.json) i environment [docs/postman/ksef-test.postman_environment.json](docs/postman/ksef-test.postman_environment.json). W environment wklej token w pole `ksefToken` (nie commituj go), potem uruchom requesty 1 → 6 po kolei. Kolejność jest istotna: request 3 korzysta z certyfikatu z requestu 1 i challenge z requestu 2.

## Konfiguracja

Plik YAML bez sekretów. Token KSeF i hasło SMTP nie należą do tego pliku; klucze spoza poniższego schematu (np. `smtp.password`) powodują błąd.

```yaml
nip: "1234563218"            # wymagane, 10 cyfr z poprawną sumą kontrolną, NIP podmiotu
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
