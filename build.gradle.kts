import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    java
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.ssoss"
version = "0.0.1-SNAPSHOT"
description = "ssoss-backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "2.1.0"
extra["springAiVersion"] = "2.0.0"
extra["archunitVersion"] = "1.4.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-scalar:3.0.3")
    implementation("org.springframework.modulith:spring-modulith-observability-api")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.zalando:logbook-spring-boot-starter:4.0.4")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.tngtech.archunit:archunit:${property("archunitVersion")}")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("live")
    }
}

tasks.register<Test>("liveTest") {
    description = "실제 외부 API 를 호출하는 라이브 테스트 (Gemini — GEMINI_API_KEY 를 환경변수나 .env 에서 읽는다)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("live")
    }
    val dotEnvFile = layout.projectDirectory.file(".env").asFile
    doFirst {
        if (System.getenv("GEMINI_API_KEY").isNullOrBlank()) {
            val fromDotEnv =
                dotEnvFile
                    .takeIf { it.exists() }
                    ?.readLines()
                    ?.firstOrNull { it.startsWith("GEMINI_API_KEY=") }
                    ?.substringAfter("=")
                    ?.trim()
                    ?.trim('"')
            require(!fromDotEnv.isNullOrBlank()) {
                "GEMINI_API_KEY 가 환경변수에도 .env 에도 없습니다"
            }
            environment("GEMINI_API_KEY", fromDotEnv)
        }
    }
    systemProperty("live.channel", providers.systemProperty("live.channel").getOrElse("BLOG"))
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    enabled = false
}
