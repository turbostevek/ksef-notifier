package com.turbostevek.ksefimporter.secrets

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SecretStoreConfiguration {

    @Bean
    fun secretStore(): SecretStore =
        if (System.getProperty("os.name").startsWith("Windows")) {
            WindowsCredentialSecretStore()
        } else {
            SecretToolSecretStore()
        }
}
