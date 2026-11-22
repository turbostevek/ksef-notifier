package com.turbostevek.ksefimporter.cli

import com.turbostevek.ksefimporter.config.ConfigException
import com.turbostevek.ksefimporter.config.ImportConfigLoader
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class ImportCommand : ApplicationRunner, ExitCodeGenerator {

    private val loader = ImportConfigLoader()
    private var exitCode = EXIT_OK

    override fun getExitCode(): Int = exitCode

    override fun run(args: ApplicationArguments) {
        if (args.nonOptionArgs.firstOrNull() != COMMAND) {
            System.err.println("Użycie: $COMMAND [--config=<plik>]")
            exitCode = EXIT_USAGE
            return
        }

        val path = Path.of(args.getOptionValues(CONFIG_OPTION)?.firstOrNull() ?: DEFAULT_CONFIG_FILE)
        try {
            val config = loader.load(path)
            log.info("Konfiguracja poprawna (środowisko KSeF: {}). Pobieranie faktur nie jest jeszcze zaimplementowane.", config.ksefEnvironment.value)
            exitCode = EXIT_OK
        } catch (e: ConfigException) {
            System.err.println("Błąd konfiguracji: ${e.message}")
            exitCode = EXIT_CONFIG_ERROR
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ImportCommand::class.java)
        const val COMMAND = "import"
        const val CONFIG_OPTION = "config"
        const val DEFAULT_CONFIG_FILE = "ksef-importer.yml"
        const val EXIT_OK = 0
        const val EXIT_CONFIG_ERROR = 1
        const val EXIT_USAGE = 2
    }
}
