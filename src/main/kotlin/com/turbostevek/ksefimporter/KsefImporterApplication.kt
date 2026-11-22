package com.turbostevek.ksefimporter

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication
class KsefImporterApplication

fun main(args: Array<String>) {
	val context = runApplication<KsefImporterApplication>(*args)
	exitProcess(SpringApplication.exit(context))
}
