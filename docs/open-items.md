# Otwarte tematy

Tematy wymagające decyzji albo ręcznego sprawdzenia przez właściciela projektu.
Aktualizuj ten plik zgodnie z zasadami w `AGENTS.md`: dopisuj nowe pozycje, zamykaj rozstrzygnięte.

## Do ręcznego sprawdzenia

- [ ] **Adapter Windows (`WindowsCredentialSecretStore`) na Windows 11.** Kod nie był uruchamiany na Windows.
  - Ryzyko: `Native.getLastError()` może nie zwracać `ERROR_NOT_FOUND` bez opcji last-error w JNA. Wtedy brak sekretu może zostać potraktowany jako błąd zamiast `null`.
  - Sprawdzić: `check-token` bez zapisanego tokenu (oczekiwany kod 1 i komunikat o braku sekretu), a potem z zapisanym tokenem (kod 0).
  - Źródło: PR #11, `src/main/kotlin/com/turbostevek/ksefimporter/secrets/WindowsCredentialSecretStore.kt`.
- [ ] **Pusty zapisany sekret na Windows.** Adapter zwraca `null` dla pustej wartości, więc `check-token` raportuje brak tokenu. Potwierdzić, że to pożądane zachowanie.

## Do decyzji

- [ ] **Surowy stderr `secret-tool` w komunikatach.** Polski komunikat o błędzie magazynu sekretów osadza angielski tekst z `secret-tool`. Ustalić, czy zostawić (przydatne do diagnozy), czy pokazywać tylko ogólny komunikat.
  - Źródło: `src/main/kotlin/com/turbostevek/ksefimporter/secrets/SecretToolSecretStore.kt`.
- [ ] **Test portowalności.** `SecretToolSecretStoreTest` działa tylko na Linuksie (`@EnabledOnOs(OS.LINUX)`). Decyzja, czy dla Windows dodać osobny test adaptera (np. na fałszywej implementacji JNA), czy zostawić tylko ręczne sprawdzenie.
