package com.ssoss.ssossbackend.observability

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.zalando.logbook.BodyFilter
import org.zalando.logbook.HeaderFilter
import org.zalando.logbook.core.HeaderFilters
import org.zalando.logbook.json.JsonBodyFilters
import java.util.Locale

private val LOGGED_HEADERS = setOf("content-type", "authorization")
private val MASKED_BODY_PROPERTIES = setOf("accessToken", "refreshToken")

@Configuration
internal class LogbookConfig {
    @Bean
    fun loggedHeaderFilter(): HeaderFilter {
        val allowlist = HeaderFilters.removeHeaders { name -> name.lowercase(Locale.ROOT) !in LOGGED_HEADERS }
        return HeaderFilter.merge(allowlist, HeaderFilters.authorization())
    }

    @Bean
    fun credentialMaskingBodyFilter(): BodyFilter = JsonBodyFilters.replaceJsonStringProperty(MASKED_BODY_PROPERTIES, "***")
}
