package io.github.atrifyllis.multitenancy.web

import io.github.atrifyllis.multitenancy.BasePostgresTest
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

class TenantInterceptorAutoConfigTest : BasePostgresTest() {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `auto-registers interceptor and sets tenant from jwt claim list (defaults to first when no header)`() {
        val tid1 = UUID.randomUUID().toString()
        val tid2 = UUID.randomUUID().toString()

        val result =
            mockMvc
                .get("/tenant") {
                    accept = MediaType.TEXT_PLAIN
                    with(jwt().jwt { jwt -> jwt.claim("tenantId", listOf(tid1, tid2)) })
                }
                .andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.response.contentAsString).isEqualTo(tid1)

        // Next request without JWT should not see leftover context
        val result2 = mockMvc.get("/tenant") { accept = MediaType.TEXT_PLAIN }.andReturn()
        assertThat(result2.response.status).isEqualTo(200)
        assertThat(result2.response.contentAsString).isBlank()
    }

    @Test
    fun `header mismatch leads to 5xx error (as original behavior)`() {
        val tid1 = UUID.randomUUID().toString()
        val mismatch = UUID.randomUUID().toString()

        val result =
            try {
                mockMvc
                    .get("/tenant") {
                        accept = MediaType.TEXT_PLAIN
                        header("X-Tenant-ID", mismatch)
                        with(jwt().jwt { jwt -> jwt.claim("tenantId", listOf(tid1)) })
                    }
                    .andReturn()
            } catch (e: Exception) {
                // The interceptor throws an exception, which results in a 500 error
                // This is the expected behavior
                assertThat(e).isInstanceOf(jakarta.servlet.ServletException::class.java)
                assertThat(e.cause)
                    .isInstanceOf(
                        io.github.atrifyllis.multitenancy.application.service
                                .TenantIdsDoNotMatchException::class
                            .java
                    )
                return
            }

        // If we get here without exception, check the response status is 5xx
        assertThat(result.response.status).isGreaterThanOrEqualTo(500)
    }
}
