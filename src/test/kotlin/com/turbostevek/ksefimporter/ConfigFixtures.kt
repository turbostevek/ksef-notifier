package com.turbostevek.ksefimporter

internal val validConfigYaml = """
    importTime: "07:30"
    startDate: 2026-01-01
    nip: "9261051389"
    recipientEmail: zona@example.com
    smtp:
      host: smtp.example.com
      from: ksef@example.com
""".trimIndent()
