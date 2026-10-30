package com.ssoss.ssossbackend.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.springframework.modulith.PackageInfo

private const val KOTLIN_FILE_FACADE_KIND = 2

private val layers = setOf("entrypoint", "application", "domain", "infrastructure")

private val kotlinFileClasses: DescribedPredicate<JavaClass> =
    object : DescribedPredicate<JavaClass>("Kotlin 파일 클래스") {
        override fun test(input: JavaClass): Boolean =
            input
                .tryGetAnnotationOfType(Metadata::class.java.name)
                .map { it.get("k").orElse(null) == KOTLIN_FILE_FACADE_KIND }
                .orElse(false)
    }

internal fun declaredClassesIn(packageIdentifier: String): GivenClassesConjunction =
    classes()
        .that()
        .resideInAPackage(packageIdentifier)
        .and()
        .areTopLevelClasses()
        .and()
        .doNotHaveModifier(JavaModifier.SYNTHETIC)
        .and()
        .areNotAnnotatedWith(PackageInfo::class.java)
        .and(not(kotlinFileClasses))

internal fun layersOf(modulePackage: String): ArchRule =
    layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Entrypoint")
        .definedBy("$modulePackage.entrypoint..")
        .layer("Application")
        .definedBy("$modulePackage.application..")
        .layer("Domain")
        .definedBy("$modulePackage.domain..")
        .layer("Infrastructure")
        .definedBy("$modulePackage.infrastructure..")
        .whereLayer("Entrypoint")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Infrastructure")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Application")
        .mayOnlyBeAccessedByLayers("Entrypoint")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Infrastructure")
        .withOptionalLayers(true)
        .allowEmptyShould(true)

internal fun innerLayersMustNotDependOnOtherModules(basePackage: String): ArchRule =
    noClasses()
        .that()
        .resideInAnyPackage("$basePackage.*.domain..", "$basePackage.*.application..")
        .should(dependOnAnotherModule(basePackage))
        .allowEmptyShould(true)

internal fun contractsReachedOnlyThroughDomainService(basePackage: String): ArchRule =
    noClasses()
        .that()
        .resideInAnyPackage("$basePackage.*.application..", "$basePackage.*.entrypoint..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("$basePackage.*.domain.contract..")
        .allowEmptyShould(true)

internal fun mustNotDependOn(
    fromPackage: String,
    toPackage: String,
): ArchRule =
    noClasses()
        .that()
        .resideInAPackage(fromPackage)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(toPackage)
        .allowEmptyShould(true)

internal fun mustNotBeDependedOnFromOutside(modulePackage: String): ArchRule =
    noClasses()
        .that()
        .resideOutsideOfPackage(modulePackage)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(modulePackage)
        .allowEmptyShould(true)

internal fun freeOfCyclesIn(sliceIdentifier: String): ArchRule =
    slices()
        .matching(sliceIdentifier)
        .should()
        .beFreeOfCycles()

internal fun modulesIn(
    classes: JavaClasses,
    basePackage: String,
): Set<String> = classes.mapNotNull { moduleOf(basePackage, it.packageName) }.toSet()

internal fun moduleOf(
    basePackage: String,
    packageName: String,
): String? {
    if (!packageName.startsWith("$basePackage.")) {
        return null
    }
    val segments = packageName.removePrefix("$basePackage.").split(".")
    if (segments.size < 2 || segments[1] !in layers) {
        return null
    }
    return segments[0]
}

internal fun dependOnAnotherModule(basePackage: String): ArchCondition<JavaClass> =
    object : ArchCondition<JavaClass>("다른 애플리케이션 모듈을 의존한다") {
        override fun check(
            item: JavaClass,
            events: ConditionEvents,
        ) {
            val owner = moduleOf(basePackage, item.packageName)
            item.directDependenciesFromSelf
                .mapNotNull { moduleOf(basePackage, it.targetClass.packageName) }
                .filter { it != owner }
                .distinct()
                .forEach { target ->
                    events.add(SimpleConditionEvent.satisfied(item, "${item.name} 가 $target 모듈을 의존합니다"))
                }
        }
    }
