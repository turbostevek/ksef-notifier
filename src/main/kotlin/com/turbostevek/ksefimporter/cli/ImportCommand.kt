package com.turbostevek.ksefimporter.cli

import com.turbostevek.ksefimporter.config.ConfigException
import com.turbostevek.ksefimporter.config.ImportConfigLoader
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
class ImportCommand(private val secrets: SecretStore) : ApplicationRunner, ExitCodeGenerator {

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
        try {
            val config = loader.load(path)
            log.info(
                "Konfiguracja poprawna (środowisko KSeF: {}). Import nie jest jeszcze zaimplementowany.",
                config.ksefEnvironment.value,
            )
            exitCode = EXIT_OK
        } catch (e: ConfigException) {
            System.err.println("Błąd konfiguracji: ${e.message}")
            exitCode = EXIT_CONFIG_ERROR
        }
    }

    private fun runCheckToken() {
        try {
            if (secrets.read(SecretName.KSEF_TOKEN) == null) {
                System.err.println(
                    "Brak sekretu '${SecretName.KSEF_TOKEN.qualifiedName}' w systemowym magazynie sekretów. " +
                        "Zapisz token KSeF pod tą nazwą.",
                )
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
        const val EXIT_OK = 0
        const val EXIT_CONFIG_ERROR = 1
        const val EXIT_USAGE = 2
    }
}
