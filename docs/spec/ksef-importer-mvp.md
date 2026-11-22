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
21. Jako użytkownik, chcę, żeby po nadrobieniu wysłano maksymalnie jedno powiadomienie na dobę, żeby nie zalewać skrzynki.
22. Jako użytkownik, chcę, żeby po kilku dniach bez uruchomienia wysłano jedno zbiorcze powiadomienie o wszystkich nowych fakturach od ostatniego sukcesu, żeby dostać pełny obraz.
23. Jako użytkownik, chcę, żeby token KSeF był przechowywany w systemowym magazynie sekretów, żeby nie leżał jawnie w pliku ani w repozytorium.
24. Jako użytkownik, chcę, żeby hasło SMTP było przechowywane w tym samym magazynie sekretów co token, żeby nie było jawne w konfiguracji.
25. Jako użytkownik, chcę, żeby aplikacja działała na Windows 11 i Ubuntu bez zmian w kodzie, żeby używać jej na obu systemach.
26. Jako użytkownik, chcę móc przełączyć środowisko KSeF między testowym a produkcyjnym w konfiguracji, żeby nie pobierać danych z produkcji podczas testów.
27. Jako użytkownik, chcę, żeby domyślnie aplikacja łączyła się ze środowiskiem testowym, żeby przypadkowo nie pracować na produkcji.
28. Jako użytkownik, chcę, żeby błąd połączenia z KSeF lub SMTP był zapisany w logu i nie kończył się cichą porażką, żeby wiedzieć, co się stało.
29. Jako użytkownik, chcę, żeby nieudany import nie aktualizował daty ostatniego sukcesu, żeby następne uruchomienie ponowiło pobieranie od właściwego miejsca.
30. Jako użytkownik, chcę, żeby ponowny import tych samych faktur nie tworzył duplikatów w Historii ani nie wysyłał ponownie powiadomień.
31. Jako użytkownik, chcę, żeby aplikacja uruchamiała się z linii poleceń bez interfejsu graficznego, żeby działała w harmonogramie.
32. Jako przyszły użytkownik, chcę, żeby logika importu i powiadomień nie zależała od sposobu uruchamiania, żeby w przyszłości dodać UI bez przepisywania rdzenia.

## Implementation Decisions

Stack: Kotlin, Spring Boot, JVM 21, budowanie przez Gradle lub Maven (do wyboru przy starcie projektu). Sposób uruchomienia: fat JAR wywoływany z harmonogramu systemowego; bez wbudowanego schedulera. Interfejs użytkownika poza zakresem MVP.

Moduły:
- **Konfiguracja**: wczytuje plik z godziną importu, datą startową, adresem odbiorcy, środowiskiem KSeF (domyślnie test), ustawieniami SMTP. Odwołuje się do sekretów po nazwie, nie zawiera ich wartości.
- **Magazyn sekretów**: abstrakcja nad systemowym magazynem (Windows Credential Manager, libsecret na Ubuntu). Udostępnia token KSeF i hasło SMTP.
- **Klient KSeF**: integracja z KSeF API v2, logowanie tokenem, pobieranie paczek faktur za zakres dat. Przy zmianie środowiska zmienia się tylko adres bazowy.
- **Parser FA(3)**: z XML wyciąga numer KSeF, NIP sprzedawcy, NIP nabywcy, datę wystawienia, termin płatności (opcjonalny), kwotę brutto, walutę oraz oznaczenie zapłaty z bloku `Platnosc` (opcjonalne). Nie zapisuje pliku XML.
- **Historia**: lokalna baza SQLite. Tabela faktur: numer KSeF (unikalny), dane z parsera, znacznik „opłacona przy wystawieniu”, znacznik „powiadomiono”, data importu. Tabela stanu importu: data ostatniego udanego importu.
- **Powiadomienia**: wysyłka zbiorczego maila przez SMTP. Mail zawiera nowe faktury do zapłaty z danego przebiegu. Nie jest wysyłany, gdy takich faktur nie ma.
- **Serwis importu** (główny moduł): dla danego zakresu pobiera paczki z klienta KSeF, parsuje faktury, zapisuje nowe do Historii (pomijając istniejące po numerze KSeF), wysyła jedno zbiorcze powiadomienie o nowych fakturach do zapłaty, a następnie oznacza je jako powiadomione i aktualizuje datę ostatniego sukcesu dopiero po pełnym przebiegu.
- **Punkt wejścia CLI**: jedno polecenie uruchamiające import; kod zwraca niezerowy status przy błędzie.

Zasady:
- Aplikacja odczytuje oznaczenie zapłaty z faktury w chwili importu i nie śledzi późniejszych zmian (ADR 0001).
- Faktura zakupowa to faktura, w której nabywcą jest podmiot.
- Brak bloku płatności lub oznaczenia zapłaty oznacza fakturę do zapłaty.
- Faktura jest zgłaszana w powiadomieniu co najwyżej raz. Znacznik „powiadomiono” ustawiany po udanej wysyłce.
- Nieudany przebieg nie przesuwa daty ostatniego sukcesu.
- Zakres pierwszego importu bierze datę startową z konfiguracji; kolejne przebiegi biorą datę ostatniego sukcesu.
- Nazwa pola z oznaczeniem zapłaty w FA(3) i jego wartości nie są jeszcze potwierdzone. Należy je sprawdzić w XSD KSeF przed implementacją parsera.

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

- Tryby powiadomień (`PER_INVOICE`/`DAILY_DIGEST`) zostały porzucone. Obowiązuje jeden zbiorczy mail na przebieg, co najwyżej jeden na dobę, bez maila przy braku nowych faktur do zapłaty.
- **Fakt do zweryfikowania**: nazwa pola oznaczenia zapłaty w bloku `Platnosc` w FA(3) i jego wartości (XSD KSeF).
- **Fakt do zweryfikowania**: szczegóły uwierzytelniania tokenem w KSeF API v2 (sesja, szyfrowanie). Nie były badane w rozmowie.
- Tracker zadań nie jest skonfigurowany w repo. Specyfikacja zapisana lokalnie. Aby publikować do trackera, uruchom `/setup-matt-pocock-skills`.
