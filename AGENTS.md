# ksef-importer

Lokalna aplikacja (Kotlin + Spring Boot, JVM 21) do importu faktur zakupowych z KSeF API v2 i wysyłania zbiorczego powiadomienia e-mail o fakturach do zapłaty. Działa na Windows 11 i Ubuntu.

Język domeny jest polski. Używaj terminów z `GLOSSARY.md` w kodzie, nazwach testów i tytułach issue.

Specyfikacja MVP: `docs/spec/ksef-importer-mvp.md`. Decyzje: `docs/adr/`.

## Agent skills

### Issue tracker

GitHub Issues w `turbostevek/ksef-notifier`, przez `gh`. See `docs/agents/issue-tracker.md`.

### Triage labels

Domyślne etykiety: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` i `docs/adr/` w korzeniu repo. See `docs/agents/domain.md`.

### Testy

- Testy dla istniejącej klasy dopisujesz do jej istniejącego pliku testowego.
- Nowa klasa może dostać nowy plik testowy, odpowiadający pakietem i nazwą klasie.
- Klucze testowe (np. `src/test/resources/ksef/test-ksef.p12`) mogą leżeć w repo, ale tylko na potrzeby testów jednostkowych. Dodanie nowego klucza wymaga akceptacji właściciela.

### Otwarte tematy

Tematy wymagające decyzji właściciela albo ręcznego sprawdzenia (np. na Windows) zapisuj w `docs/open-items.md`:
- dopisuj każdą nową pozycję w momencie, gdy ją znajdziesz, z kontekstem i źródłem (plik lub PR),
- aktualizuj istniejące pozycje po każdej zmianie (np. zaznacz `[x]` po rozstrzygnięciu),
- nie zamykaj pozycji bez potwierdzenia właściciela.
