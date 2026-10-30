package com.ssoss.ssossbackend.persistence

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.core.dialect.JdbcDialect
import org.springframework.data.jdbc.repository.config.JdbcConfiguration

@Configuration
internal class JdbcConversionsConfig {
    @Bean
    fun jdbcCustomConversions(
        dialect: JdbcDialect,
        converters: List<JdbcConverters>,
    ): JdbcCustomConversions = JdbcConfiguration.createCustomConversions(dialect, converters.flatMap { it.values })
}
