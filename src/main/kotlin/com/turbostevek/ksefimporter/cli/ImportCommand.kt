package com.turbostevek.ksefimporter.cli

import com.turbostevek.ksefimporter.config.ConfigException
import com.turbostevek.ksefimporter.config.ImportConfigLoader
import com.turbostevek.ksefimporter.history.PurchaseInvoiceImporter
import com.turbostevek.ksefimporter.ksef.KsefException
import com.turbostevek.ksefimporter.ksef.KsefToken
import com.turbostevek.ksefimporter.secrets.SecretName
import com.turbostevek.ksefimporter.secrets.SecretStore
import com.turbostevek.ksefimporter.secrets.SecretStoreException
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class ImportCommand(
    private val secrets: SecretStore,
    private val importer: PurchaseInvoiceImporter,
) : ApplicationRunner, ExitCodeGenerator {

    private val loader = ImportConfigLoader()
    private var exitCode = EXIT_OK

    override fun getExitCode(): Int = exitCode

    override fun run(args: ApplicationArguments) {
        when (args.nonOptionArgs.firstOrNull()) {
            COMMAND -> runImport(args)
            CHECK_TOKEN_COMMAND -> runCheckToken()
            else -> {
                System.err.println("Użycie: $COMMAND [--config=<plik>] | $CHECK_TOKEN_COMMAND")
                exitCode = EXIT_USAGE
            }
        }
    }

    private fun runImport(args: ApplicationArguments) {
        val configValues = args.getOptionValues(CONFIG_OPTION)
        val configWithoutValue = args.optionNames.contains(CONFIG_OPTION) &&
            configValues?.firstOrNull().isNullOrBlank()
        if (args.nonOptionArgs != listOf(COMMAND) || configWithoutValue) {
            System.err.println("Użycie: $COMMAND [--config=<plik>]")
            exitCode = EXIT_USAGE
            return
        }
        val path = Path.of(configValues?.firstOrNull() ?: DEFAULT_CONFIG_FILE)
        val config = try {
            loader.load(path)
        } catch (e: ConfigException) {
            System.err.println("Błąd konfiguracji: ${e.message}")
            exitCode = EXIT_CONFIG_ERROR
            return
        }
        // Catch Throwable, not RuntimeException, so any unhandled failure, including Error, maps to exit code 4.
        val token = try {
            secrets.read(SecretName.KSEF_TOKEN)
        } catch (e: SecretStoreException) {
            System.err.println("Błąd magazynu sekretów: ${e.message}")
            exitCode = EXIT_CONFIG_ERROR
            return
        } catch (e: Throwable) {
            reportUnexpectedError(e)
            return
        }
        if (token == null) {
            System.err.println(MISSING_TOKEN_MESSAGE)
            exitCode = EXIT_CONFIG_ERROR
            return
        }
        try {
            val result = importer.importInvoices(config, KsefToken(token), path.resolveSibling(HISTORY_DB_FILE))
            log.info(
                "Pobrano {} faktur zakupowych z KSeF (środowisko KSeF: {}), dodano do historii: {}. Faktury nie zostały zapisane na dysku.",
                result.purchases,
                config.ksefEnvironment.value,
                result.added,
            )
            exitCode = EXIT_OK
        } catch (e: KsefException) {
            log.error("Błąd KSeF: {}", e.message)
            System.err.println("Błąd KSeF: ${e.message}")
            exitCode = EXIT_KSEF_ERROR
        } catch (e: Throwable) {
            reportUnexpectedError(e)
        }
    }

    private fun reportUnexpectedError(e: Throwable) {
        log.error("Nieoczekiwany błąd importu", e)
        System.err.println("Nieoczekiwany błąd importu: ${e.message}")
        exitCode = EXIT_UNEXPECTED_ERROR
    }

    private fun runCheckToken() {
        try {
            if (secrets.read(SecretName.KSEF_TOKEN) == null) {
                System.err.println(MISSING_TOKEN_MESSAGE)
                exitCode = EXIT_CONFIG_ERROR
            } else {
                System.out.println("Token KSeF jest zapisany w magazynie sekretów.")
                exitCode = EXIT_OK
            }
        } catch (e: SecretStoreException) {
            System.err.println("Błąd magazynu sekretów: ${e.message}")
            exitCode = EXIT_CONFIG_ERROR
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ImportCommand::class.java)
        const val COMMAND = "import"
        const val CHECK_TOKEN_COMMAND = "check-token"
        const val CONFIG_OPTION = "config"
        const val DEFAULT_CONFIG_FILE = "ksef-importer.yml"
        const val HISTORY_DB_FILE = "historia.db"
        const val EXIT_OK = 0
        const val EXIT_CONFIG_ERROR = 1
        const val EXIT_USAGE = 2
        const val EXIT_KSEF_ERROR = 3
        const val EXIT_UNEXPECTED_ERROR = 4
        val MISSING_TOKEN_MESSAGE = "Brak sekretu '${SecretName.KSEF_TOKEN.qualifiedName}' w systemowym magazynie sekretów. " +
            "Zapisz token KSeF pod tą nazwą."
    }
}
