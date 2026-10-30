package com.ssoss.ssossbackend.persistence

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing
import java.time.Clock
import java.util.Optional

@Configuration
@EnableJdbcAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
internal class JdbcAuditingConfig {
    @Bean
    fun auditingDateTimeProvider(clock: Clock): DateTimeProvider = DateTimeProvider { Optional.of(clock.instant()) }
}
