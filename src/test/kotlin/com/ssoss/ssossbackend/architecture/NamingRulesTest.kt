package com.ssoss.ssossbackend.architecture

import com.ssoss.ssossbackend.shared.exception.ErrorCode
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RestController

private val productionClasses: JavaClasses =
    ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.ssoss.ssossbackend")

private val namingViolationFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages("com.ssoss.archfixtures.naming")

private val gatewayNamingFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages("com.ssoss.archfixtures.gatewaynaming")

@DisplayName("네이밍 규칙")
internal class NamingRulesTest {
    @Nested
    @DisplayName("@RestController 클래스 이름은 Controller 로 끝난다")
    inner class Controllers {
        private val rule: ArchRule =
            classes()
                .that()
                .areAnnotatedWith(RestController::class.java)
                .should()
                .haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true)

        @Test
        fun `프로덕션 코드가 규칙을 지키면 통과한다`() {
            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `Controller 로 끝나지 않는 @RestController 가 있으면 실패한다`() {
            assertThatThrownBy { rule.check(namingViolationFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("BadlyNamed")
        }
    }

    @Nested
    @DisplayName("예외 클래스 이름은 Exception 으로 끝난다")
    inner class Exceptions {
        private val rule: ArchRule =
            classes()
                .that()
                .areAssignableTo(Throwable::class.java)
                .should()
                .haveSimpleNameEndingWith("Exception")
                .allowEmptyShould(true)

        @Test
        fun `프로덕션 코드가 규칙을 지키면 통과한다`() {
            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `Exception 으로 끝나지 않는 예외가 있으면 실패한다`() {
            assertThatThrownBy { rule.check(namingViolationFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Boom")
        }
    }

    @Nested
    @DisplayName("ErrorCode 구현 이름은 ErrorCode 로 끝난다")
    inner class ErrorCodes {
        private val rule: ArchRule =
            classes()
                .that()
                .implement(ErrorCode::class.java)
                .should()
                .haveSimpleNameEndingWith("ErrorCode")
                .allowEmptyShould(true)

        @Test
        fun `프로덕션 코드가 규칙을 지키면 통과한다`() {
            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `ErrorCode 로 끝나지 않는 구현이 있으면 실패한다`() {
            assertThatThrownBy { rule.check(namingViolationFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("MemberError")
        }
    }

    @Nested
    @DisplayName("다른 모듈에 여는 지점은 InternalGateway, 그 지점이 내주는 값은 Reply 로 끝난다")
    inner class InternalGateways {
        private val rule: ArchRule =
            declaredClassesIn("..entrypoint.gateway..")
                .should()
                .haveSimpleNameEndingWith("InternalGateway")
                .orShould()
                .haveSimpleNameEndingWith("Reply")
                .allowEmptyShould(true)

        @Test
        fun `프로덕션 코드가 규칙을 지키면 통과한다`() {
            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `InternalGateway 도 Reply 도 아닌 이름이 있으면 실패한다`() {
            assertThatThrownBy { rule.check(gatewayNamingFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("BetaOpening")
        }

        @Test
        fun `companion object 와 최상위 함수 파일과 PackageInfo 클래스는 걸리지 않는다`() {
            assertThatThrownBy { rule.check(gatewayNamingFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageNotContaining("Companion")
                .hasMessageNotContaining("BetaGatewayFunctionsKt")
                .hasMessageNotContaining("ModuleMetadata")
        }
    }

    @Nested
    @DisplayName("교차 모듈 어댑터 이름은 Internal 로 시작해 Client 로 끝난다")
    inner class InternalClients {
        private val rule: ArchRule =
            declaredClassesIn("..infrastructure.gateway..")
                .should()
                .haveSimpleNameStartingWith("Internal")
                .andShould()
                .haveSimpleNameEndingWith("Client")
                .allowEmptyShould(true)

        @Test
        fun `프로덕션 코드가 규칙을 지키면 통과한다`() {
            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `Internal 로 시작하지 않는 어댑터가 있으면 실패한다`() {
            assertThatThrownBy { rule.check(gatewayNamingFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("BetaClientAdapter")
        }

        @Test
        fun `companion object 와 최상위 함수 파일은 걸리지 않는다`() {
            assertThatThrownBy { rule.check(gatewayNamingFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageNotContaining("Companion")
                .hasMessageNotContaining("BetaClientFunctionsKt")
        }
    }
}
