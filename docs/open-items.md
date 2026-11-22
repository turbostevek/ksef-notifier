# Otwarte tematy

Tematy wymagające decyzji albo ręcznego sprawdzenia przez właściciela projektu.
Aktualizuj ten plik zgodnie z zasadami w `AGENTS.md`: dopisuj nowe pozycje, zamykaj rozstrzygnięte.

## Do ręcznego sprawdzenia

- [ ] **Adapter Windows (`WindowsCredentialSecretStore`) na Windows 11.** Kod nie był uruchamiany na Windows.
  - Ryzyko: `Native.getLastError()` może nie zwracać `ERROR_NOT_FOUND` bez opcji last-error w JNA. Wtedy brak sekretu może zostać potraktowany jako błąd zamiast `null`.
  - Sprawdzić: `check-token` bez zapisanego tokenu (oczekiwany kod 1 i komunikat o braku sekretu), a potem z zapisanym tokenem (kod 0).
  - Źródło: PR #11, `src/main/kotlin/com/turbostevek/ksefimporter/secrets/WindowsCredentialSecretStore.kt`.
- [ ] **Pusty zapisany sekret na Windows.** Adapter zwraca `null` dla pustej wartości, więc `check-token` raportuje brak tokenu. Potwierdzić, że to pożądane zachowanie.
- [ ] **Test KSeF API v2 na środowisku TEST (issue #3).** Ścieżki, schematy i zasady zatwierdzania tokenów są potwierdzone w OpenAPI TEST, ale nie na żywym API.

  - Sprawdzić: `POST /auth/challenge` → `POST /auth/ksef-token` → `GET /auth/{ref}` → `POST /auth/token/redeem` z prawdziwym tokenem testowym na `https://api-test.ksef.mf.gov.pl/v2`.
  - Postęp: kolekcja Postman [docs/postman/ksef-test-auth.postman_collection.json](/home/stevek/Projects/java/ksef-importer/docs/postman/ksef-test-auth.postman_collection.json) (environment `KSeF TEST`, sekret `ksefToken` podawany przy uruchomieniu). Przeszła na TEST przez newman: challenge, ksef-token, poll, redeem i `GET /tokens/rev2` zwróciły poprawne statusy (9/9 asercji). Szyfrowanie RSA-OAEP działa synchronicznie w pre-request script. Właściciel potwierdził działanie w Postmanie (GUI). Wartości przejściowe są w `pm.collectionVariables`, bo `pm.variables` nie przechodzi między requestami. Nie zamknięto pozycji: sprawdzenie na Windows przeprowadzi właściciel przy testowaniu zbudowanej aplikacji.
  - Źródło: `docs/spec/ksef-importer-mvp.md`, sekcja „Further Notes”.
- [ ] **Generator tokenów testowych (`ksef-token-generator-csharp`).** Zbudowany w C# (.NET 10) w `/home/stevek/Projects/dotnet/ksef-token-generator-csharp`. Wygenerowano token na TEST (status `Active`) i zweryfikowano go (`verify`: uwierzytelnienie tokenem KSeF + odczyt listy tokenów). Decyzja: [ADR 0003](/home/stevek/Projects/java/ksef-importer/docs/adr/0003-generator-tokenow-testowych-w-javie.md). Pozostaje: commit aplikacji i wybór miejsca przechowywania tokena (obecnie `~/.config/ksef-importer/ksef-test-token`, README mówi o magazynie sekretów).
  - Wariant Java (`/home/stevek/Projects/java/ksef-token-generator`) porzucony po błędzie 21115; nieużywany.
  - Rejestracja losowego NIP-u: najpierw sprawdzić, czy XAdES działa dla zwykłego NIP-u bez rejestracji (tak robi test C# `KsefTokenRev2E2ETests.cs`). Dopiero gdy to nie działa, użyć `POST /testdata/subject`. Endpoint przyjmuje tylko `VatGroup`, `JST` i `EnforcementAuthority` (OpenAPI TEST), więc zwykłej firmy nim nie zarejestrujemy. Decyzja właściciela: wariant „bez rejestracji” jako pierwszy, testdata awaryjnie.
  - Ważność tokena: 90 dni domyślnie, maksimum 365. Generator powinien wypisać `validTo`, żeby użytkownik wiedział, kiedy token wygaśnie.
  - Limit 100 tokenów w kontekście: generator powinien używać świeżego NIP-u albo unieważniać stare tokeny po teście.
  - Źródło: OpenAPI TEST, `POST /tokens/rev2`; `docs/spec/ksef-importer-mvp.md`, sekcja „Further Notes”.
- [x] **NIP podmiotu w konfiguracji.** Decyzja właściciela: `nip` jest wymaganym kluczem pliku konfiguracyjnego. Dopisane w [spec](/home/stevek/Projects/java/ksef-importer/docs/spec/ksef-importer-mvp.md) i [README](/home/stevek/Projects/java/ksef-importer/README.md). Wdrożenie w issue #4.
- [ ] **Faktury testowe dla losowego NIP-u.** Import na TEST pobierze faktury tylko wtedy, gdy ktoś wcześniej wysłał faktury z nabywcą równym NIP-owi tokena. Wysyłka faktur (sesja online) jest poza zakresem MVP. Do ustalenia, skąd przy teście wezmą się faktury: osobnym narzędziem, czy ręcznie.
- [ ] **Token produkcyjny.** Na produkcji generator nie zadziała (samopodpisany certyfikat nie jest honorowany). Importer loguje się tokenem KSeF, który użytkownik wygeneruje w aplikacji KSeF (ksef.podatki.gov.pl) certyfikatem KSeF, kwalifikowanym albo Profilem Zaufanym. Do ustalenia: czy spec ma opisać tę ścieżkę jako jedyną produkcyjną. Źródło: dokumentacja KSeF, „Certyfikaty KSeF” (wyszukiwanie, nie test na żywym API).
- [ ] **Wygaśnięcie tokena.** Token ma ważność 90 dni (maksymalnie 365). Spec nie mówi, co importer robi po wygaśnięciu. Do decyzji: czytelny komunikat w logu i kod wyjścia != 0 (zgodnie z acceptance criteria issue #4), a może też `check-token` sprawdzający datę ważności.
- [ ] **Test portowalności.** `SecretToolSecretStoreTest` działa tylko na Linuksie (`@EnabledOnOs(OS.LINUX)`). Decyzja, czy dla Windows dodać osobny test adaptera (np. na fałszywej implementacji JNA), czy zostawić tylko ręczne sprawdzenie.
