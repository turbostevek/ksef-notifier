package com.turbostevek.ksefimporter.history

import java.math.BigDecimal
import java.time.LocalDate

data class PurchaseInvoice(
    val ksefNumber: String,
    val sellerNip: String?,
    val buyerNip: String?,
    val issueDate: LocalDate,
    val dueDate: LocalDate?,
    val grossAmount: BigDecimal,
    val currency: String,
    val paidAtIssue: Boolean,
)
