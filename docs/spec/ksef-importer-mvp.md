# Specyfikacja: Ksef Importer (MVP)

Status: gotowe do implementacji. Źródła ustaleń: [GLOSSARY.md](/home/stevek/Projects/java/ksef-importer/GLOSSARY.md), [ADR 0001](/home/stevek/Projects/java/ksef-importer/docs/adr/0001-brak-sledzenia-platnosci.md), [ADR 0002](/home/stevek/Projects/java/ksef-importer/docs/adr/0002-kotlin-spring-boot.md).

## Problem Statement

Niektóre firmy przestały wysyłać maile o fakturach do zapłaty i publikują je tylko w KSeF. Podmiot (firma użytkownika) nie zauważa nowych faktur do zapłaty na czas, bo musiałby codziennie ręcznie sprawdzać KSeF. Potrzebne jest automatyczne, codzienne sprawdzenie KSeF i powiadomienie o nowych fakturach do zapłaty.

## Solution

Lokalna aplikacja uruchamiana raz dziennie przez harmonogram systemowy (Harmonogram zadań Windows lub cron na Ubuntu). Pobiera z KSeF nowe faktury zakupowe podmiotu, zapisuje ich podstawowe dane w lokalnej Historii i wysyła jeden zbiorczy email o nowych fakturach do zapłaty. Faktury oznaczone na fakturze jako już opłacone trafiają do Historii, ale nie są zgłaszane w powiadomieniu. Pliki faktur nie są zapisywane. Aplikacja nie zarządza statusem płatności. Godzina importu, data startowa i adres odbiorcy są konfigurowalne w pliku konfiguracyjnym.

## User Stories

1. Jako użytkownik, chcę uruchamiać import raz dziennie przez harmonogram systemowy, żeby nie musiałem pamiętać o sprawdzaniu KSeF.
2. Jako użytkownik, chcę otrzymać email, gdy pojawi się nowa faktura do zapłaty, żeby nie przegapić terminu.
3. Jako użytkownik, chcę, żeby powiadomienie dotyczyło tylko nowo zaimportowanych faktur, żeby nie dostawać duplikatów.
4. Jako użytkownik, chcę, żeby każda faktura do zapłaty wywoływała powiadomienie co najwyżej raz, żeby nie dostawać go ponownie przy kolejnych importach.
5. Jako użytkownik, chcę, żeby faktura oznaczona na fakturze jako już opłacona trafiała do Historii bez powiadomienia, żeby zakupy z przedpłatą nie generowały fałszywych alarmów.
6. Jako użytkownik, chcę, żeby faktura bez oznaczenia zapłaty była traktowana jako do zapłaty, żeby nie przegapić faktury, gdy oznaczenie jest brakujące.
7. Jako użytkownik, chcę, żeby aplikacja nie wysyłała powiadomienia o fakturze, którą już wcześniej zaimportowała, nawet jeśli później została opłacona, żeby zachowany był prosty model bez śledzenia płatności.
8. Jako użytkownik, chcę widzieć w Historii wszystkie zaimportowane faktury zakupowe, żeby mieć ich zapis.
9. Jako użytkownik, chcę, żeby w Historii był numer KSeF, NIP sprzedawcy, data wystawienia, termin płatności, kwota, waluta, oznaczenie zapłaty i informacja o wysłanym powiadomieniu, żeby wiedzieć, o czym jest faktura.
10. Jako użytkownik, chcę, żeby w Historii brakujący termin płatności był oznaczony jako „brak terminu”, a w powiadomieniu data wystawienia była podana jako odniesienie, żeby faktura nie zginęła.
11. Jako użytkownik, chcę, żeby pliki faktur nie były zapisywane lokalnie, żeby nie przechowywać danych, których nie potrzebuję.
12. Jako użytkownik, chcę, żeby import pobierał tylko faktury od ostatniego udanego importu, żeby nie pobierać tego samego wielokrotnie.
13. Jako użytkownik, chcę, żeby przy pierwszym uruchomieniu import obejmował okres od daty z konfiguracji, żeby objąć starsze faktury, które jeszcze nie zostały zaimportowane.
14. Jako użytkownik, chcę, żeby data startowa importu była konfigurowalna, żeby móc dostosować ją do potrzeb.
15. Jako użytkownik, chcę dostać jeden zbiorczy email z nowymi fakturami do zapłaty, żeby nie dostawać wielu wiadomości naraz.
16. Jako użytkownik, chcę, żeby email nie był wysyłany, gdy nie ma nowych faktur do zapłaty, żeby nie zaśmiecać skrzynki.
17. Jako użytkownik, chcę, żeby zbiorczy email po przerwie obejmował wszystkie nowe faktury do zapłaty od ostatniego udanego importu, żeby nie zgubić żadnej.
18. Jako użytkownik, chcę, żeby godzina importu była konfigurowalna w harmonogramie systemowym, żeby dopasować ją do pory, kiedy komputer jest włączony.
19. Jako użytkownik, chcę, żeby adres odbiorcy powiadomień był konfigurowalny, żeby mogła go zmienić żona lub inna osoba.
20. Jako użytkownik, chcę, żeby jeśli komputer był wyłączony w dniu importu, aplikacja nadrobiła import przy najbliższym uruchomieniu, żeby nie zgubić faktur.
21. Jako użytkownik, chcę, żeby po kilku dniach bez uruchomienia wysłano jedno zbiorcze powiadomienie o wszystkich nowych fakturach od ostatniego sukcesu, żeby dostać pełny obraz.
22. Jako użytkownik, chcę, żeby token KSeF był przechowywany w systemowym magazynie sekretów, żeby nie leżał jawnie w pliku ani w repozytorium.
23. Jako użytkownik, chcę, żeby hasło SMTP było przechowywane w tym samym magazynie sekretów co token, żeby nie było jawne w konfiguracji.
24. Jako użytkownik, chcę, żeby aplikacja działała na Windows 11 i Ubuntu bez zmian w kodzie, żeby używać jej na obu systemach.
25. Jako użytkownik, chcę móc przełączyć środowisko KSeF między testowym a produkcyjnym w konfiguracji, żeby nie pobierać danych z produkcji podczas testów.
26. Jako użytkownik, chcę, żeby domyślnie aplikacja łączyła się ze środowiskiem testowym, żeby przypadkowo nie pracować na produkcji.
27. Jako użytkownik, chcę, żeby błąd połączenia z KSeF lub SMTP był zapisany w logu i nie kończył się cichą porażką, żeby wiedzieć, co się stało.
28. Jako użytkownik, chcę, żeby nieudany import nie aktualizował daty ostatniego sukcesu, żeby następne uruchomienie ponowiło pobieranie od właściwego miejsca.
29. Jako użytkownik, chcę, żeby ponowny import tych samych faktur nie tworzył duplikatów w Historii ani nie wysyłał ponownie powiadomień.
30. Jako użytkownik, chcę, żeby aplikacja uruchamiała się z linii poleceń bez interfejsu graficznego, żeby działała w harmonogramie.
31. Jako przyszły użytkownik, chcę, żeby logika importu i powiadomień nie zależała od sposobu uruchamiania, żeby w przyszłości dodać UI bez przepisywania rdzenia.

## Implementation Decisions

Stack: Kotlin, Spring Boot, JVM 21, budowanie przez Gradle lub Maven (do wyboru przy starcie projektu). Sposób uruchomienia: fat JAR wywoływany z harmonogramu systemowego; bez wbudowanego schedulera. Interfejs użytkownika poza zakresem MVP.

Moduły:
- **Konfiguracja**: wczytuje plik z NIP-em podmiotu (kontekst uwierzytelnienia tokenem), godziną importu, datą startową, adresem odbiorcy, środowiskiem KSeF (domyślnie test), ustawieniami SMTP. Odwołuje się do sekretów po nazwie, nie zawiera ich wartości.
  - Format pliku: YAML.
  - Lokalizacja: katalog użytkownika (Windows `%APPDATA%\ksef-importer`, Linux `~/.config/ksef-importer`). Ścieżkę można nadpisać argumentem CLI.
  - Kolejność nadpisywania: argument CLI > zmienna środowiskowa > plik. Plik jest źródłem wartości domyślnych.
  - Brak pliku: czytelny błąd z podaną ścieżką i instrukcją, bez tworzenia pliku.
- **Magazyn sekretów**: abstrakcja nad systemowym magazynem (Windows Credential Manager, libsecret na Ubuntu). Udostępnia token KSeF i hasło SMTP.
- **Klient KSeF**: integracja z KSeF API v2, logowanie tokenem, pobieranie paczek faktur za zakres dat. Przy zmianie środowiska zmienia się tylko adres bazowy.
- **Parser FA(3)**: z XML wyciąga NIP sprzedawcy, nazwę i adres sprzedawcy, NIP nabywcy, datę wystawienia, termin płatności (opcjonalny), kwotę brutto, walutę oraz z bloku `Platnosc` (opcjonalne): oznaczenie zapłaty, formę płatności, numer rachunku i nazwę banku. Numer KSeF nie występuje w XML (schemat FA(3) go nie zawiera); pochodzi z nazwy pliku w paczce (`{ksefNumber}.xml`), zgodnej z `ksefNumber` w `_metadata.json`. Nie zapisuje pliku XML.
- **Historia**: lokalna baza SQLite `invoice-history.db` w katalogu pliku konfiguracyjnego. Tabela `invoice_history` (klucz: `ksef_number`) zawiera kolumny: `ksef_number`, `seller_nip`, `buyer_nip`, `issue_date`, `due_date`, `gross_amount`, `currency`, `paid_at_issue` (znacznik „opłacona przy wystawieniu”), `notified` (znacznik „powiadomiono”), `imported_at`, `seller_name`, `seller_address` (adres w jednej linii, części połączone „, ”, znaki nowej linii usunięte), `payment_form` (kod FA(3) `FormaPlatnosci`, np. `6`; etykieta „przelew” pojawia się tylko w e-mailu, nieznany kod trafia do e-maila jako „nieznana (kod X)”), `bank_account` i `bank_name`. Brak terminu płatności zapisywany jest jako NULL i pokazywany jako „brak terminu”. Zmiana modelu: `payment_form` przechowywał wcześniej etykietę, a teraz przechowuje kod; wiersze ze starego schematu w bazie testowej zostały wyczyszczone. Tabela `import_state` przechowuje `last_imported_to`, czyli datę ostatniego udanego importu.
- **Powiadomienia**: wysyłka zbiorczego maila przez SMTP. Mail zawiera nowe faktury do zapłaty z danego przebiegu. Nie jest wysyłany, gdy takich faktur nie ma.
- **Serwis importu** (główny moduł): dla danego zakresu pobiera paczki z klienta KSeF, parsuje faktury, zapisuje nowe do Historii (pomijając istniejące po numerze KSeF), wysyła jedno zbiorcze powiadomienie o nowych fakturach do zapłaty, a następnie oznacza je jako powiadomione i aktualizuje datę ostatniego sukcesu dopiero po pełnym przebiegu.
- **Punkt wejścia CLI**: jedno polecenie uruchamiające import; kod zwraca niezerowy status przy błędzie. Kody wyjścia: `0` import zakończony, `1` błąd konfiguracji lub brak tokenu, `2` błędne użycie, `3` błąd KSeF, `4` nieoczekiwany błąd, `5` błąd wysyłki powiadomienia (import zapisany). Po udanym imporcie logowana jest liczba pobranych faktur zakupowych i liczba dodanych do Historii (pliki XML nie są zapisywane na dysku). Ta sama lista jest w [README](../../README.md).

Zasady:
- Aplikacja odczytuje oznaczenie zapłaty z faktury w chwili importu i nie śledzi późniejszych zmian (ADR 0001).
- Faktura zakupowa to faktura, w której nabywcą jest podmiot.
- Brak bloku płatności lub oznaczenia zapłaty oznacza fakturę do zapłaty.
- Faktura jest zgłaszana w powiadomieniu co najwyżej raz. Znacznik „powiadomiono” ustawiany po udanej wysyłce.
- Nieudany przebieg nie przesuwa daty ostatniego sukcesu.
- Zakres pierwszego importu bierze datę startową z konfiguracji; kolejne przebiegi biorą datę ostatniego sukcesu.
- Oznaczenie zapłaty w FA(3): element `Fa/Platnosc/Zaplacono` o wartości `1` (typ `TWybor1`), opcjonalnie z `Fa/Platnosc/DataZaplaty`. Źródło: XSD FA(3) (zob. Further Notes).
- Termin płatności pochodzi z XML: `Fa/Platnosc/TerminPlatnosci/Termin` (opcjonalny, może wystąpić do 100 razy). Metadane faktury z API (`InvoiceMetadata`) nie zawierają terminu ani oznaczenia zapłaty.

Seam testowy: jeden, na poziomie serwisu importu. Zależności zewnętrzne (klient KSeF, wysyłka mail, zegar) są wstrzyknięte jako interfejsy. Historia korzysta z prawdziwej, tymczasowej bazy SQLite. Nie ma istniejącego kodu, więc nie ma wcześniejszych wzorców testowych w repo; projekt powinien ustalić standard przy starcie.

## Testing Decisions

Dobry test sprawdza zachowanie widoczne z zewnątrz: co trafiło do Historii, co wysłano jako powiadomienie, jaka jest data ostatniego sukcesu. Nie sprawdza kolejności wywołań wewnętrznych ani szczegółów implementacji.

Testowane moduły:
- **Serwis importu** (główny, testy przez seam): z fałszywym klientem KSeF, fałszywą wysyłką i prawdziwą bazą SQLite w pamięci.
  - pierwszy import od daty startowej, kolejny przyrostowo
  - faktura zakupowa z oznaczeniem zapłaty trafia do Historii bez powiadomienia
  - faktura bez oznaczenia zapłaty trafia do Historii z powiadomieniem
  - ponowny import tych samych faktur nie duplikuje wpisów ani powiadomień
  - nowe faktury do zapłaty są wysyłane w jednym mailu zbiorczym
  - brak nowych faktur do zapłaty nie wysyła maila
  - błąd w trakcie importu nie przesuwa daty ostatniego sukcesu
  - błąd wysyłki nie oznacza faktur jako „powiadomiono”, więc następny przebieg ponowi wysyłkę
  - nadrobienie po kilku dniach wysyła jeden mail obejmujący wszystkie nowe faktury od ostatniego sukcesu
- **Parser FA(3)**: na fixture'ach XML (zanonimizowane faktury): faktura z blokiem płatności zapłaconej, bez bloku, z terminem i bez terminu, z walutą innej niż PLN.
- **Konfiguracja**: domyślne środowisko to test, brak wymaganego parametru zgłasza czytelny błąd.

Brak testów na prawdziwym KSeF i SMTP w CI. Testy integracyjne z środowiskiem testowym KSeF wykonywane ręcznie.

## Out of Scope

- Interfejs użytkownika (GUI, web).
- Zapisywanie plików faktur (XML, PDF).
- Ręczne ani automatyczne oznaczanie faktur jako opłacone po imporcie.
- Faktury sprzedażowe.
- Obsługa wielu NIP-ów lub wielu tokenów w jednej instancji.
- Integracja z LastPass.
- Powiadomienia systemowe (dodatkowy kanał, później).
- Instalator natywny (`jpackage`).
- Przypomnienia o zbliżającym się terminie płatności.
- Podpisywanie lub weryfikacja faktur.

## Further Notes

- Tryby powiadomień (`PER_INVOICE`/`DAILY_DIGEST`) zostały porzucone. Obowiązuje jeden zbiorczy mail na przebieg, częstotliwość kontroluje zewnętrzny harmonogram (bez limitu w aplikacji), bez maila przy braku nowych faktur do zapłaty.
- **Zweryfikowano (issue #3, źródła: repozytorium CIRFMF/ksef-api, XSD FA(3) `schemat_FA(3)_v1-0E.xsd`)**:
  - Oznaczenie zapłaty: `Fa/Platnosc/Zaplacono` = `1` (zapłacono). Obok może być `Fa/Platnosc/DataZaplaty`. Blok `Platnosc` jest opcjonalny.
  - Płatność częściowa: `Fa/Platnosc/ZnacznikZaplatyCzesciowej` (`1` = w części, `2` = w całości w ratach), z `ZaplataCzesciowa`. Faktura z płatnością częściową jest traktowana jako nieopłacona.
  - Termin: `Fa/Platnosc/TerminPlatnosci/Termin` (typ data). Metadane API tego nie zawierają.
  - Środowisko TEST: `https://api-test.ksef.mf.gov.pl/v2`. Środowiska DEMO i PROD mają inne hosty (`api-demo`, `api`), tylko zmienia się adres bazowy.
  - Uwierzytelnianie tokenem KSeF:
    1. `POST /auth/challenge` zwraca `challenge` i `timestamp` (ważność 10 minut).
    2. Szyfrowanie: ciąg `{tokenKSeF}|{timestampMs}` szyfrowany RSA-OAEP (SHA-256, MGF1 SHA-256) kluczem publicznym KSeF o `usage` = `KsefTokenEncryption`, wynik Base64.
    3. `POST /auth/ksef-token` z zaszyfrowanym tokenem, challenge i kontekstem (NIP). Odpowiedź: `authenticationToken` i `referenceNumber`.
    4. `GET /auth/{referenceNumber}` z nagłówkiem `Authorization: Bearer <authenticationToken>`, aż status będzie sukcesem.
    5. `POST /auth/token/redeem` (jednorazowo, z `authenticationToken`) zwraca `accessToken` (JWT, krótki) i `refreshToken` (do 7 dni).
    6. Odświeżanie: `POST /auth/token/refresh` z `refreshToken` w nagłówku Bearer.
  - Sesja interaktywna (do wysyłki faktur, poza zakresem MVP): klucz AES-256 i IV 128 bitów, szyfrowanie AES-256-CBC z PKCS#7, klucz AES szyfrowany RSA-OAEP kluczem `SymmetricKeyEncryption`.
- **Założenia**:
  1. Płatność częściowa (`ZnacznikZaplatyCzesciowej` = `1` lub `2`) nie oznacza faktury jako opłaconej. Tylko `Zaplacono` = `1` ją oznacza.
  2. Gdy w fakturze jest wiele `TerminPlatnosci`, parser bierze najwcześniejszy `Termin`.
  3. Ścieżki i schematy są sprawdzone w OpenAPI TEST (v2): `/auth/challenge`, `/auth/ksef-token`, `/auth/{referenceNumber}`, `/auth/token/redeem`, `/auth/token/refresh`, `/tokens/rev2`, `/tokens/rev2/{referenceNumber}`, `/invoices/query/metadata`. Nie zostały sprawdzone na żywym środowisku. Wymaga ręcznego testu z tokenem testowym (open-items).
  4. Zapytanie o faktury zakupowe używa `InvoiceQuerySubjectType` = `Subject2` (podmiot 2, nabywca), zgodnie z definicją Faktury zakupowej.
- **Token testowy**: generuje go osobna aplikacja `ksef-token-generator-csharp` (C# / .NET 10, poza tym repozytorium, [ADR 0003](/home/stevek/Projects/java/ksef-importer/docs/adr/0003-generator-tokenow-testowych-w-javie.md)). Procedura: uwierzytelnienie XAdES certyfikatem testowym na losowym NIP-ie, następnie `POST /tokens/rev2` z uprawnieniami `InvoiceRead` i `InvoiceWrite`. Token jest zwracany tylko raz i nadaje się do użycia dopiero w statusie `Active`.
  - Token wygenerowany po uwierzytelnieniu XAdES nie wymaga zatwierdzenia. Token wygenerowany w sesji tokenem KSeF dostaje status `AwaitingApproval` i trzeba go zatwierdzić w ciągu 24 h (`POST /tokens/{referenceNumber}/approve`). Generator używa XAdES, więc tego nie dotyczy.
  - Ważność: domyślnie 90 dni od `validFrom`, maksymalnie 365 dni. W jednym kontekście można mieć najwyżej 100 tokenów, licząc też nieaktywne w statusach `Active`, `NotYetActive`, `Pending` i `AwaitingApproval`.
- **NIP w konfiguracji**: `POST /auth/ksef-token` wymaga kontekstu NIP, więc NIP podmiotu jest wymaganym kluczem `nip` w pliku konfiguracyjnym (wdrożone w #4). Walidacja jest formalna (10 cyfr), bez sumy kontrolnej: aplikacja nie weryfikuje NIP-ów, a sprawdza tylko, czy nie wpisano go przypadkowo źle.
- **Klient KSeF (#4)**: pobieranie paczki szyfrowane AES-256-CBC, klucz i IV wysyłane w `POST /invoices/exports` zaszyfrowane kluczem `SymmetricKeyEncryption`. Części paczki są scalane przed jednorazowym odszyfrowaniem. Zakres dzielony na okna do 90 dni. Paczka obcięta przez KSeF jest dobierana kolejnymi eksportami od daty kontynuacji. Odpowiedzi części paczki są weryfikowane skrótem SHA-256. Każde żądanie HTTP ma limit czasu (60 s); przerwanie oczekiwania na status eksportu zgłaszane jest jako błąd KSeF.
- Tracker zadań nie jest skonfigurowany w repo. Specyfikacja zapisana lokalnie. Aby publikować do trackera, uruchom `/setup-matt-pocock-skills`.
