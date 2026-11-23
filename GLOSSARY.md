# Ksef Importer

Aplikacja lokalna, która raz dziennie pobiera z KSeF faktury zakupowe podmiotu i informuje użytkownika o nowych fakturach do zapłaty. Nie przechowuje plików faktur ani nie śledzi stanu płatności.

## Język

**Podmiot**:
Firma identyfikowana przez NIP, w imieniu której działa aplikacja. Jedna instancja aplikacji obsługuje jeden podmiot.
_Avoid_: Firma, klient, tenant

**Token KSeF**:
Poświadczenie służące do połączenia z KSeF w imieniu podmiotu. Przechowywany w systemowym magazynie sekretów, nigdy w pliku konfiguracyjnym.
_Avoid_: Klucz API, hasło

**Faktura KSeF**:
Faktura zarejestrowana w KSeF i identyfikowana numerem KSeF.
_Avoid_: E-faktura, dokument

**Faktura zakupowa**:
Faktura KSeF, w której nabywcą jest podmiot.
_Avoid_: Faktura kosztowa, faktura przychodząca

**Faktura do zapłaty**:
Faktura zakupowa zaimportowana przez aplikację, która nie jest oznaczona na fakturze jako już opłacona. Oznaczenie odczytywane jest z XML w chwili importu; późniejszych zmian aplikacja nie śledzi.
_Avoid_: Nieopłacona, zaległa, należność

**Faktura opłacona przy wystawieniu**:
Faktura zakupowa oznaczona na fakturze jako już opłacona (np. zakup w sklepie internetowym z przedpłatą). Trafia do Historii, ale nie generuje Powiadomienia.
_Avoid_: Opłacona, zamknięta

**Challenge**:
Jednorazowy znacznik wydawany przez KSeF na potrzeby logowania tokenem. Termin zostaje w oryginalnej, angielskiej postaci, także w komunikatach dla użytkownika (nie tłumaczyć na „wyzwanie”).
_Avoid_: Wyzwanie

**Import**:
Pobranie z KSeF paczki faktur za wskazany zakres dat i zapisanie informacji o nich w historii. Pliki faktur nie są zapisywane.
_Avoid_: Synchronizacja, pobranie

**Historia**:
Lokalny zapis zaimportowanych faktur wraz z informacją, czy dla każdej z nich wysłano powiadomienie.
_Avoid_: Archiwum, baza faktur

**Powiadomienie**:
Zbiorcza wiadomość email wysyłana po Imporcie, zawierająca nowe Faktury do zapłaty. Aplikacja wysyła jedno Powiadomienie na przebieg, jeśli są faktury do zapłaty, które nie zostały jeszcze powiadomione. Częstotliwość wysyłki kontroluje zewnętrzny harmonogram, a nie aplikacja; ręczne uruchomienie też może wysłać Powiadomienie. Faktury już oznaczone jako powiadomione nigdy nie są wysyłane ponownie. Brak nowych faktur do zapłaty oznacza brak Powiadomienia.
_Avoid_: Alert, przypomnienie, mail na fakturę

## Relacje

- Podmiot ma jeden Token KSeF.
- Import tworzy wpisy w Historii dla Faktur zakupowych.
- Nowe Faktury do zapłaty są zgłaszane w jednym Powiadomieniu. Faktura opłacona przy wystawieniu nie jest zgłaszana.

## Język w kodzie

Kod źródłowy, identyfikatory (klasy, metody, zmienne, nazwy poleceń CLI), komentarze techniczne i nazwy plików są po angielsku. Nazwy testów, komunikaty dla użytkownika i README są po polsku, a terminy z tego glosariusza używane w tych tekstach mają polską formę.
