package com.turbostevek.ksefimporter.history

import java.math.BigDecimal
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate

/**
 * Local SQLite history of purchase invoices and the state of the last successful import.
 * Saving an import is one transaction, so a failed import leaves both the invoices and the state unchanged.
 */
class InvoiceHistory(path: Path) : AutoCloseable {

    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")

    init {
        connection.createStatement().use { statement ->
            statement.executeUpdate(CREATE_INVOICES)
            statement.executeUpdate(CREATE_IMPORT_STATE)
        }
    }

    /** Returns the end of the window of the last successful import, or null if no import has run yet. */
    fun lastSuccessfulImport(): Instant? =
        connection.prepareStatement("SELECT last_imported_to FROM import_state WHERE id = 1").use { statement ->
            statement.executeQuery().use { rows ->
                if (rows.next()) Instant.parse(rows.getString(1)) else null
            }
        }

    /** Stores the invoices that are not in the history yet and moves the import state to [importedUpTo]. Returns the number of added invoices. */
    fun save(invoices: List<PurchaseInvoice>, importedUpTo: Instant, importedAt: Instant): Int {
        connection.autoCommit = false
        try {
            var added = 0
            connection.prepareStatement(INSERT_INVOICE).use { statement ->
                invoices.forEach { invoice ->
                    statement.setString(1, invoice.ksefNumber)
                    statement.setString(2, invoice.sellerNip)
                    statement.setString(3, invoice.buyerNip)
                    statement.setString(4, invoice.issueDate.toString())
                    statement.setString(5, invoice.dueDate?.toString())
                    statement.setString(6, invoice.grossAmount.toPlainString())
                    statement.setString(7, invoice.currency)
                    statement.setInt(8, if (invoice.paidAtIssue) 1 else 0)
                    statement.setString(9, importedAt.toString())
                    added += statement.executeUpdate()
                }
            }
            connection.prepareStatement(UPSERT_IMPORT_STATE).use { statement ->
                statement.setString(1, importedUpTo.toString())
                statement.executeUpdate()
            }
            connection.commit()
            return added
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun findAll(): List<PurchaseInvoice> =
        connection.prepareStatement(SELECT_INVOICES).use { statement ->
            statement.executeQuery().use { rows ->
                generateSequence { if (rows.next()) rows.toInvoice() else null }.toList()
            }
        }

    override fun close() {
        connection.close()
    }

    private fun java.sql.ResultSet.toInvoice() = PurchaseInvoice(
        ksefNumber = getString("ksef_number"),
        sellerNip = getString("seller_nip"),
        buyerNip = getString("buyer_nip"),
        issueDate = LocalDate.parse(getString("issue_date")),
        dueDate = getString("due_date")?.let(LocalDate::parse),
        grossAmount = BigDecimal(getString("gross_amount")),
        currency = getString("currency"),
        paidAtIssue = getInt("paid_at_issue") == 1,
    )

    private companion object {
        const val CREATE_INVOICES = """
            CREATE TABLE IF NOT EXISTS invoice_history (
                ksef_number TEXT PRIMARY KEY,
                seller_nip TEXT,
                buyer_nip TEXT,
                issue_date TEXT NOT NULL,
                due_date TEXT,
                gross_amount TEXT NOT NULL,
                currency TEXT NOT NULL,
                paid_at_issue INTEGER NOT NULL,
                notified INTEGER NOT NULL DEFAULT 0,
                imported_at TEXT NOT NULL
            )
        """
        const val CREATE_IMPORT_STATE = """
            CREATE TABLE IF NOT EXISTS import_state (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                last_imported_to TEXT NOT NULL
            )
        """
        const val INSERT_INVOICE = """
            INSERT INTO invoice_history
                (ksef_number, seller_nip, buyer_nip, issue_date, due_date, gross_amount, currency, paid_at_issue, imported_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (ksef_number) DO NOTHING
        """
        const val UPSERT_IMPORT_STATE = """
            INSERT INTO import_state (id, last_imported_to) VALUES (1, ?)
            ON CONFLICT (id) DO UPDATE SET last_imported_to = excluded.last_imported_to
        """
        const val SELECT_INVOICES = """
            SELECT ksef_number, seller_nip, buyer_nip, issue_date, due_date, gross_amount, currency, paid_at_issue
            FROM invoice_history
            ORDER BY issue_date, ksef_number
        """
    }
}
