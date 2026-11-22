# Generator tokenów testowych jako osobna aplikacja w C# (.NET 10)

Token testowy KSeF generuje osobna konsolowa aplikacja `ksef-token-generator-csharp` w `/home/stevek/Projects/dotnet/ksef-token-generator-csharp`, napisana w C# na .NET 10. Nie jest częścią importera, więc nie zmienia stacku z [ADR 0002](0002-kotlin-spring-boot.md).

Zastępuje wcześniejszy plan generatora w Javie. Java-generator (`/home/stevek/Projects/java/ksef-token-generator`) kończył się błędem 21115 („Nieprawidłowy certyfikat”) przy `POST /auth/xades-signature`, mimo kilku poprawek podpisu i ważności certyfikatu. Referencyjny test E2E z `ksef-client-csharp` przeszedł na TEST, więc aplikacja C# używa tego samego biblioteki klienta (`KSeF.Client`) i tej samej ścieżki XAdES. Narzędzie działa wyłącznie na środowisku TEST.

Konsekwencje:
- Generator zależy od `KSeF.Client` przez `ProjectReference` do `/home/stevek/Projects/dotnet/ksef-client-csharp`. Importer nie zależy od generatora.
- Generator wypisuje token na stdout, a postęp i statusy na stderr. Zapis tokena do magazynu sekretów lub pliku wykonuje użytkownik.
- Tryb `verify <nip> <plik>` uwierzytelnia token KSeF i odczytuje listę tokenów (`QueryKsefTokensRev2Async`). Nie wypisuje wartości tokena.
- Token uzyskany przez XAdES nie wymaga zatwierdzenia. Generator nie korzysta z uwierzytelnienia tokenem KSeF do wygenerowania tokena.
- Losowy NIP: generator losuje NIP z poprawną cyfrą kontrolną i używa go bez rejestracji. Wywołanie `POST /testdata/subject` nie jest potrzebne, bo endpoint przyjmuje tylko `VatGroup`, `JST` i `EnforcementAuthority`.
- Generator działa tylko na TEST. Na produkcji token użytkownik generuje w aplikacji KSeF, a importer loguje się już sam tokenem (zob. `docs/open-items.md`).
