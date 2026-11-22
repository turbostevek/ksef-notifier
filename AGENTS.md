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
