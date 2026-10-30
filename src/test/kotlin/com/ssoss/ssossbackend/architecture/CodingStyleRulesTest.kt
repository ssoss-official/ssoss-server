package com.ssoss.ssossbackend.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController

private val productionClasses: JavaClasses =
    ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.ssoss.ssossbackend")

private val codingViolationFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages("com.ssoss.archfixtures.coding")

@DisplayName("어노테이션/코딩 스타일 규칙")
internal class CodingStyleRulesTest {
    @Nested
    @DisplayName("필드 주입을 사용하지 않는다")
    inner class FieldInjection {
        private val rule: ArchRule = NO_CLASSES_SHOULD_USE_FIELD_INJECTION

        @Test
        fun `프로덕션 코드가 생성자 주입만 쓰면 통과한다`() {
            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `@Autowired 필드가 있으면 실패한다`() {
            assertThatThrownBy { rule.check(codingViolationFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("BadStyleController")
        }
    }

    @Nested
    @DisplayName("컨트롤러에 @Transactional 을 붙이지 않는다")
    inner class ControllerTransactional {
        private val rule: ArchRule =
            noClasses()
                .that()
                .areAnnotatedWith(RestController::class.java)
                .should()
                .beAnnotatedWith(Transactional::class.java)
                .allowEmptyShould(true)

        @Test
        fun `컨트롤러가 없거나 트랜잭션을 안 붙이면 통과한다`() {
            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `@RestController 에 @Transactional 이 붙으면 실패한다`() {
            assertThatThrownBy { rule.check(codingViolationFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("BadStyleController")
        }
    }
}
