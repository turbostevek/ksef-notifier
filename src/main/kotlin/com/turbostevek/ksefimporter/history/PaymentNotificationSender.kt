package com.turbostevek.ksefimporter.history

import com.turbostevek.ksefimporter.config.ImportConfig

/** Sends one batch notification about invoices to pay. Throws when the notification cannot be delivered. */
fun interface PaymentNotificationSender {
    fun send(config: ImportConfig, invoices: List<PurchaseInvoice>)
}
