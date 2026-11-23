package com.turbostevek.ksefimporter.history

/** Forma płatności z FA(3) `FormaPlatnosci`. Kod spoza słownika trafia do [Unknown] i nie udaje etykiety. */
sealed interface PaymentForm {
    val code: String
    val label: String

    data class Known(val method: PaymentMethod) : PaymentForm {
        override val code: String get() = method.code
        override val label: String get() = method.label
    }

    data class Unknown(override val code: String) : PaymentForm {
        override val label: String get() = "nieznana (kod $code)"
    }

    companion object {
        fun fromCode(code: String): PaymentForm =
            PaymentMethod.entries.firstOrNull { it.code == code }?.let(::Known) ?: Unknown(code)
    }
}

enum class PaymentMethod(val code: String, val label: String) {
    CASH("1", "gotówka"),
    CARD("2", "karta"),
    VOUCHER("3", "bon"),
    CHEQUE("4", "czek"),
    CREDIT("5", "kredyt"),
    TRANSFER("6", "przelew"),
    MOBILE("7", "mobilna"),
}
