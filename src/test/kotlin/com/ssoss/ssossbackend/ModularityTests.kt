package com.ssoss.ssossbackend

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

@DisplayName("ApplicationModules")
internal class ModularityTests {
    @Test
    fun `애플리케이션 모듈 구조가 Modulith 규칙을 위반하지 않는다`() {
        ApplicationModules.of(SsossBackendApplication::class.java).verify()
    }
}
