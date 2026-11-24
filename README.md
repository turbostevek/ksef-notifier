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

Po imporcie wysyłany jest jeden zbiorczy e-mail HTML na adres `recipientEmail` z tabelą faktur zakupowych do zapłaty, których nie ma jeszcze w powiadomieniach (faktury oznaczone jako opłacone przy wystawieniu są pomijane). Kolumny w stałej kolejności: data wystawienia, numer KSeF, sprzedawca, adres, forma płatności, rachunek, kwota, termin płatności. W tabeli e-mail podaje nazwę i adres wystawcy, formę płatności (z kodu FA(3): 1 gotówka, 2 karta, 3 bon, 4 czek, 5 kredyt, 6 przelew, 7 mobilna) oraz, jeśli jest na fakturze, numer rachunku i nazwę banku. NIP wystawcy nie trafia do e-maila (pozostaje w bazie). Gdy nie ma takich faktur, e-mail nie jest wysyłany. Faktury trafiają do powiadomienia dopiero po udanej wysyłce. Błąd wysyłki (np. SMTP) nie cofa zapisu faktur, ale nie przesuwa daty ostatniego sukcesu: przebieg kończy się kodem `5`, a kolejny przebieg ponownie pobiera ten sam zakres i ponawia wysyłkę zaległych faktur.

Jeśli wiadomość została wysłana, ale aplikacja zakończyła się przed zapisaniem znacznika powiadomienia, następny przebieg może wysłać ją ponownie.

Kody wyjścia:

- `0` – konfiguracja poprawna, import zakończony (lub token zapisany, dla `check-token`)
- `1` – błąd konfiguracji (brak parametru, nieprawidłowa wartość, brak pliku) albo brak tokenu KSeF w magazynie sekretów; komunikat jest wypisywany na stderr
- `2` – brak polecenia `import` lub `check-token`
- `3` – błąd KSeF (token odrzucony lub wygasły, błąd eksportu, brak połączenia); komunikat jest logowany jako błąd i wypisywany na stderr
- `4` – nieoczekiwany błąd podczas importu (np. błąd wewnętrzny); komunikat jest logowany wraz z przyczyną i wypisywany na stderr
- `5` – import zakończony, ale nie udało się wysłać powiadomienia (np. błąd SMTP); faktury są zapisane, ale nie są oznaczane jako powiadomione, data ostatniego sukcesu nie przesuwa się, a błąd jest logowany i wypisywany na stderr

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

`startDate` określa początek pierwszego importu; późniejsze importy zaczynają się od ostatniego sukcesu. `recipientEmail` to adres odbiorcy powiadomień. `smtp.host` i `smtp.port` wskazują serwer SMTP, `smtp.from` to adres nadawcy, a `smtp.username` włącza uwierzytelnianie hasłem z magazynu sekretów. Serwer SMTP musi obsługiwać STARTTLS.

`importTime` jest wczytywany i sprawdzany, ale **nie uruchamia importu**. Godzinę i częstotliwość ustawia się w harmonogramie systemowym; zmiana `importTime` nie zmienia harmonogramu.

## Codzienny import z harmonogramu

Zbuduj JAR, przygotuj plik konfiguracyjny i zapisz sekrety zgodnie z sekcjami powyżej. Uruchom import ręcznie tym samym kontem, które będzie wykonywać zadanie. Konto musi mieć dostęp do JAR-a, konfiguracji, magazynu sekretów oraz możliwość zapisu bazy obok konfiguracji. Przykładowe ścieżki poniżej zastąp własnymi.

### Windows 11: Harmonogram zadań

1. Otwórz Harmonogram zadań i wybierz **Utwórz zadanie**. W zakładce **Ogólne** wybierz konto, na którym zapisano sekrety, oraz **Uruchom tylko wtedy, gdy użytkownik jest zalogowany**.
2. W zakładce **Wyzwalacze** dodaj wyzwalacz **Codziennie**, co 1 dzień, np. o 07:30.
3. W zakładce **Akcje** dodaj **Uruchom program**:
   - **Program/skrypt:** pełna ścieżka do `java.exe` z Javy 21, np. `C:\Program Files\Java\jdk-21\bin\java.exe`.
   - **Dodaj argumenty:** `-jar "C:\ksef-importer\ksef-importer-0.0.1-SNAPSHOT.jar" import --config="C:\ksef-importer\ksef-importer.yml"`.
   - **Rozpocznij w:** `C:\ksef-importer` (bez cudzysłowów).
4. W zakładce **Ustawienia** włącz uruchomienie zadania możliwie szybko po pominiętym terminie i ustaw zasadę dla działającego już zadania na **Nie uruchamiaj nowego wystąpienia**. Zapisz zadanie.
5. Uruchom zadanie ręcznie z Harmonogramu i sprawdź **Wynik ostatniego uruchomienia** oraz historię zadania. Niezerowy wynik oznacza błąd opisany w sekcji kodów wyjścia.

Zadanie wymaga zalogowania wybranego użytkownika. Po wyłączeniu komputera funkcja uruchomienia po pominiętym terminie pozwala nadrobić import; sprawdź jej działanie przy wybranych ustawieniach konta.

### Ubuntu: cron

Użyj crontaba tego samego użytkownika, który zapisał sekrety. W tym przykładzie `/home/user` to katalog tego użytkownika, a `1000` to jego UID (`id -u`). Dostosuj ścieżki, UID i godzinę:

```cron
PATH=/usr/bin:/bin
30 7 * * * DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus /usr/bin/java -jar /home/user/ksef-importer/ksef-importer-0.0.1-SNAPSHOT.jar import --config=/home/user/ksef-importer/ksef-importer.yml >> /home/user/ksef-importer/import.log 2>&1
```

Wpis dodaj przez `crontab -e`. Ustawienie uruchamia import codziennie o 07:30 według czasu systemowego; sprawdź `/usr/bin/java -version` (wymagana Java 21) i dostępność `secret-tool` w podanym `PATH`. Ścieżki w przykładzie nie zawierają spacji.

Cron nie dziedziczy pełnego środowiska sesji graficznej. Powyższy adres jest przykładem dla dostępnej sesyjnej magistrali D-Bus użytkownika; magazyn libsecret musi być dostępny i odblokowany podczas zadania. Wstępnie sprawdź dostęp do tokenu z tym samym `PATH` i adresem D-Bus, podstawiając UID i ścieżkę do JAR-a:

```sh
env PATH=/usr/bin:/bin DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus /usr/bin/java -jar /home/user/ksef-importer/ksef-importer-0.0.1-SNAPSHOT.jar check-token
```

Następnie sprawdź uruchomienie importu i `import.log`; ogranicz dostęp do logu, ponieważ może zawierać informacje o błędach. Jeśli magazyn nie jest dostępny z crona, skonfiguruj dostęp do sesji i odblokowanie magazynu dla tego konta przed użyciem harmonogramu. `check-token` nie sprawdza hasła SMTP ani wysyłki. Testowy import faktycznie pobiera faktury i może wysłać powiadomienie.

Cron nie uruchamia pominiętego zadania po włączeniu komputera. Po pominiętym terminie uruchom import ręcznie albo poczekaj na następny termin, gdy komputer będzie włączony i magazyn dostępny; import obejmie zaległy zakres.
