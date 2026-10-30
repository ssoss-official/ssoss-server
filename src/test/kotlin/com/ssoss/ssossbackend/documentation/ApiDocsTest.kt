package com.ssoss.ssossbackend.documentation

import com.ssoss.ssossbackend.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

@DisplayName("ApiDocs")
internal class ApiDocsTest : IntegrationTest() {
    @Nested
    @DisplayName("GET /v3/api-docs")
    inner class GetApiDocs {
        @Test
        fun `openapi 필드를 포함한 문서가 반환된다`() {
            client()
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String::class.java)
                .value { body -> assertThat(body).contains("\"openapi\"") }
        }
    }

    @Nested
    @DisplayName("GET /scalar")
    inner class GetScalarUi {
        @Test
        fun `api-docs 를 가리키는 Scalar UI HTML 이 반환된다`() {
            client()
                .get()
                .uri("/scalar")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String::class.java)
                .value { body ->
                    assertThat(body)
                        .containsIgnoringCase("scalar")
                        .contains("/v3/api-docs")
                }
        }
    }
}
