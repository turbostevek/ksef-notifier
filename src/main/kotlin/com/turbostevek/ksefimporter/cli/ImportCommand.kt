package com.turbostevek.ksefimporter.cli

import com.turbostevek.ksefimporter.config.ConfigException
import com.turbostevek.ksefimporter.config.ImportConfigLoader
import com.turbostevek.ksefimporter.ksef.KsefClientFactory
import com.turbostevek.ksefimporter.ksef.KsefException
import com.turbostevek.ksefimporter.secrets.SecretName
import com.turbostevek.ksefimporter.secrets.SecretStore
import com.turbostevek.ksefimporter.secrets.SecretStoreException
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId

@Component
class ImportCommand(
    private val secrets: SecretStore,
    private val clientFactory: KsefClientFactory,
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
        val token = try {
            secrets.read(SecretName.KSEF_TOKEN)
        } catch (e: SecretStoreException) {
            System.err.println("Błąd magazynu sekretów: ${e.message}")
            exitCode = EXIT_CONFIG_ERROR
            return
        }
        if (token == null) {
            System.err.println(MISSING_TOKEN_MESSAGE)
            exitCode = EXIT_CONFIG_ERROR
            return
        }
        try {
            val client = clientFactory.create(config.ksefEnvironment.apiBaseUrl)
            val accessToken = client.login(token, config.nip)
            val from = config.startDate.atStartOfDay(POLISH_ZONE).toInstant()
            val invoices = client.downloadInvoices(accessToken, from, Instant.now())
            log.info(
                "Pobrano {} faktur zakupowych z KSeF (środowisko KSeF: {}). Faktury nie zostały zapisane na dysku.",
                invoices.size,
                config.ksefEnvironment.value,
            )
            exitCode = EXIT_OK
        } catch (e: KsefException) {
            log.error("Błąd KSeF: {}", e.message)
            System.err.println("Błąd KSeF: ${e.message}")
            exitCode = EXIT_KSEF_ERROR
        }
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
        val POLISH_ZONE: ZoneId = ZoneId.of("Europe/Warsaw")
        const val EXIT_OK = 0
        const val EXIT_CONFIG_ERROR = 1
        const val EXIT_USAGE = 2
        const val EXIT_KSEF_ERROR = 3
        val MISSING_TOKEN_MESSAGE = "Brak sekretu '${SecretName.KSEF_TOKEN.qualifiedName}' w systemowym magazynie sekretów. " +
            "Zapisz token KSeF pod tą nazwą."
    }
}
