package com.ssoss.ssossbackend.architecture

import com.ssoss.ssossbackend.SsossBackendApplication
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

private val productionClasses: JavaClasses =
    ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.ssoss.ssossbackend")

private val cycleFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages("com.ssoss.archfixtures.cycle")

@DisplayName("모듈 구조 규칙 (Modulith 명문화)")
internal class ModuleStructureRulesTest {
    @Nested
    @DisplayName("애플리케이션 모듈 사이에 순환 의존이 없다")
    inner class Cycles {
        @Test
        fun `모듈이 서로 순환하지 않으면 통과한다`() {
            val rule = freeOfCyclesIn("com.ssoss.ssossbackend.(*)..")

            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `모듈이 서로를 의존해 순환이 생기면 실패한다`() {
            val rule = freeOfCyclesIn("com.ssoss.archfixtures.cycle.(*)..")

            assertThatThrownBy { rule.check(cycleFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Cycle")
        }
    }

    @Nested
    @DisplayName("모듈 모델 명문화")
    inner class ModuleModel {
        @Test
        fun `shared 커널이 애플리케이션 모듈로 인식된다`() {
            val modules = ApplicationModules.of(SsossBackendApplication::class.java)

            assertThat(modules.getModuleByName("shared")).isPresent
        }
    }
}
