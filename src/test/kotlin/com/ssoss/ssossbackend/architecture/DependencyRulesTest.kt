package com.ssoss.ssossbackend.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val PRODUCTION_BASE = "com.ssoss.ssossbackend"
private const val LAYERING_FIXTURE_BASE = "com.ssoss.archfixtures.layering"
private const val MODULE_BOUNDARY_FIXTURE_BASE = "com.ssoss.archfixtures.moduleboundary"
private const val CONTRACT_ACCESS_FIXTURE_BASE = "com.ssoss.archfixtures.contractaccess"

private val productionClasses: JavaClasses =
    ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(PRODUCTION_BASE)

private val directionFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages("com.ssoss.archfixtures.direction")

private val layeringFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages(LAYERING_FIXTURE_BASE)

private val moduleBoundaryFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages(MODULE_BOUNDARY_FIXTURE_BASE)

private val contractAccessFixtures: JavaClasses =
    ClassFileImporter()
        .importPackages(CONTRACT_ACCESS_FIXTURE_BASE)

@DisplayName("레이어/의존 방향 규칙")
internal class DependencyRulesTest {
    @Nested
    @DisplayName("shared 커널은 다른 모듈을 의존하지 않는다")
    inner class SharedKernel {
        @Test
        fun `shared 가 observability 를 의존하지 않으면 통과한다`() {
            val rule =
                mustNotDependOn(
                    "com.ssoss.ssossbackend.shared..",
                    "com.ssoss.ssossbackend.observability..",
                )

            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `커널 패키지가 다른 모듈을 의존하면 실패한다`() {
            val rule =
                mustNotDependOn(
                    "com.ssoss.archfixtures.direction.consumer..",
                    "com.ssoss.archfixtures.direction.supplier..",
                )

            assertThatThrownBy { rule.check(directionFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Consumer")
        }
    }

    @Nested
    @DisplayName("observability 모듈은 외부에서 의존하지 않는다")
    inner class Observability {
        @Test
        fun `어떤 모듈도 observability 를 의존하지 않으면 통과한다`() {
            val rule = mustNotBeDependedOnFromOutside("com.ssoss.ssossbackend.observability..")

            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `외부 패키지가 기술 모듈을 의존하면 실패한다`() {
            val rule = mustNotBeDependedOnFromOutside("com.ssoss.archfixtures.direction.supplier..")

            assertThatThrownBy { rule.check(directionFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("Consumer")
        }
    }

    @Nested
    @DisplayName("모듈 내부 레이어는 안쪽으로만 의존한다 (entrypoint→application→domain, infrastructure→domain)")
    inner class Layers {
        @Test
        fun `프로덕션 레이어 코드가 규칙을 지키면 통과한다`() {
            for (module in modulesIn(productionClasses, PRODUCTION_BASE)) {
                val rule = layersOf("$PRODUCTION_BASE.$module")

                assertThatCode { rule.check(productionClasses) }
                    .describedAs("%s 모듈", module)
                    .doesNotThrowAnyException()
            }
        }

        @Test
        fun `entrypoint 가 domain 을 직접 의존하면 실패한다`() {
            val rule = layersOf(LAYERING_FIXTURE_BASE)

            assertThatThrownBy { rule.check(layeringFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("MemberController")
        }

        @Test
        fun `domain 이 infrastructure 를 의존하면 실패한다`() {
            val rule = layersOf(LAYERING_FIXTURE_BASE)

            assertThatThrownBy { rule.check(layeringFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("MemberJdbcTemplate")
        }

        @Test
        fun `다른 모듈의 entrypoint 를 의존하는 것은 모듈 내부 레이어 위반이 아니다`() {
            val rule = layersOf("$MODULE_BOUNDARY_FIXTURE_BASE.alpha")

            assertThatCode { rule.check(moduleBoundaryFixtures) }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("domain 과 application 은 다른 모듈을 의존하지 않는다 (infrastructure 만 의존할 수 있다)")
    inner class ModuleBoundary {
        @Test
        fun `프로덕션 코드가 규칙을 지키면 통과한다`() {
            val rule = innerLayersMustNotDependOnOtherModules(PRODUCTION_BASE)

            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `application 이 다른 모듈을 의존하면 실패한다`() {
            val rule = innerLayersMustNotDependOnOtherModules(MODULE_BOUNDARY_FIXTURE_BASE)

            assertThatThrownBy { rule.check(moduleBoundaryFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("AlphaService")
        }

        @Test
        fun `domain 이 다른 모듈을 의존하면 실패한다`() {
            val rule = innerLayersMustNotDependOnOtherModules(MODULE_BOUNDARY_FIXTURE_BASE)

            assertThatThrownBy { rule.check(moduleBoundaryFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("AlphaEntity")
        }

        @Test
        fun `infrastructure 가 다른 모듈을 의존하면 걸리지 않는다`() {
            val rule = innerLayersMustNotDependOnOtherModules(MODULE_BOUNDARY_FIXTURE_BASE)

            assertThatThrownBy { rule.check(moduleBoundaryFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageNotContaining("InternalBetaClient")
        }
    }

    @Nested
    @DisplayName("domain/contract 는 domain/service 를 거쳐서만 닿는다")
    inner class ContractAccess {
        @Test
        fun `프로덕션 코드가 규칙을 지키면 통과한다`() {
            val rule = contractsReachedOnlyThroughDomainService(PRODUCTION_BASE)

            assertThatCode { rule.check(productionClasses) }.doesNotThrowAnyException()
        }

        @Test
        fun `application 이 contract 를 직접 의존하면 실패한다`() {
            val rule = contractsReachedOnlyThroughDomainService(CONTRACT_ACCESS_FIXTURE_BASE)

            assertThatThrownBy { rule.check(contractAccessFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageContaining("AlphaService")
        }

        @Test
        fun `domain 의 service 가 contract 를 의존하는 것은 걸리지 않는다`() {
            val rule = contractsReachedOnlyThroughDomainService(CONTRACT_ACCESS_FIXTURE_BASE)

            assertThatThrownBy { rule.check(contractAccessFixtures) }
                .isInstanceOf(AssertionError::class.java)
                .hasMessageNotContaining("AlphaFinder")
        }
    }
}
