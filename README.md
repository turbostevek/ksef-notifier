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

Kody wyjścia:

- `0` – konfiguracja poprawna (lub token zapisany, dla `check-token`)
- `1` – błąd konfiguracji (brak parametru, nieprawidłowa wartość, brak pliku) albo brak tokenu KSeF w magazynie sekretów; komunikat jest wypisywany na stderr
- `2` – brak polecenia `import` lub `check-token`

## Token KSeF

Token nie należy do pliku konfiguracyjnego. Jest przechowywany w systemowym magazynie sekretów pod nazwą `ksef-importer/ksef-token`:

- Ubuntu (libsecret, pakiet `libsecret-tools`):
  ```sh
  secret-tool store --label='KSeF token' service ksef-importer account ksef-token
  ```
- Windows: Menedżer poświadczeń → Dane logowania systemu Windows → Dodaj poświadczenie ogólne, adres sieciowy `ksef-importer/ksef-token`, hasło = token.

## Konfiguracja

Plik YAML bez sekretów. Token KSeF i hasło SMTP nie należą do tego pliku; klucze spoza poniższego schematu (np. `smtp.password`) powodują błąd.

```yaml
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
