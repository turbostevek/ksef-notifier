package com.turbostevek.ksefimporter.ksef

/** Step of the KSeF workflow; [label] is the user-facing prefix of error messages. */
enum class OperationDescription(val label: String) {
    CHALLENGE("Pobranie challenge"),
    LOGIN("Logowanie tokenem KSeF"),
    REDEEM("Odbiór tokenów sesji"),
    CERTIFICATES("Pobranie certyfikatów KSeF"),
    EXPORT("Eksport faktur"),
    PART_DOWNLOAD("Pobranie części paczki"),
    DECRYPT_PACKAGE("Odszyfrowanie paczki faktur"),
    UNZIP_PACKAGE("Rozpakowanie paczki faktur"),
}
