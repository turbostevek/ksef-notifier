package com.turbostevek.ksefimporter.history

internal const val TEST_NIP = "9261051389"

/** Minimal FA(3) invoice; [platnosc] is the inner content of the `Platnosc` element, `null` omits the element. */
internal fun fa3Xml(
    buyerNip: String = TEST_NIP,
    platnosc: String? = "",
    currency: String = "PLN",
    gross: String = "123.45",
): ByteArray = """
    <?xml version="1.0" encoding="UTF-8"?>
    <Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">
      <Podmiot1>
        <DaneIdentyfikacyjne><NIP>1111111111</NIP><Nazwa>Sprzedawca Sp. z o.o.</Nazwa></DaneIdentyfikacyjne>
        <Adres><KodKraju>PL</KodKraju><AdresL1>ul. Długa 1</AdresL1><AdresL2>00-001 Warszawa</AdresL2></Adres>
      </Podmiot1>
      <Podmiot2><DaneIdentyfikacyjne><NIP>$buyerNip</NIP></DaneIdentyfikacyjne></Podmiot2>
      <Fa>
        <KodWaluty>$currency</KodWaluty>
        <P_1>2026-03-10</P_1>
        <P_15>$gross</P_15>
        ${platnosc?.let { "<Platnosc>$it</Platnosc>" } ?: ""}
      </Fa>
    </Faktura>
""".trimIndent().toByteArray(Charsets.UTF_8)
