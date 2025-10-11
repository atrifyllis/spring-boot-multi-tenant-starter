package io.github.atrifyllis.multitenancy.application.service

import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TenantContextTest {

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `set and get tenant`() {
        val id = UUID.randomUUID()
        TenantContext.setTenantId(id)
        assertThat(TenantContext.getTenantId()).isEqualTo(id)
    }

    @Test
    fun `clear removes tenant`() {
        val id = UUID.randomUUID()
        TenantContext.setTenantId(id)
        TenantContext.clear()
        assertThat(TenantContext.getTenantId()).isNull()
    }

    @Test
    fun `inherits to child thread`() {
        val id = UUID.randomUUID()
        TenantContext.setTenantId(id)
        val pool = Executors.newSingleThreadExecutor()
        val future = pool.submit(Callable { TenantContext.getTenantId() })
        val childVal = future.get()
        pool.shutdown()
        assertThat(childVal).isEqualTo(id)
    }

    @Test
    fun `separate thread gets its own context after clear`() {
        val id = UUID.randomUUID()
        TenantContext.setTenantId(id)
        val pool = Executors.newSingleThreadExecutor()
        val future =
            pool.submit(
                Callable {
                    assertThat(TenantContext.getTenantId()).isEqualTo(id)
                    TenantContext.clear()
                    TenantContext.getTenantId()
                }
            )
        val afterClear = future.get()
        pool.shutdown()
        assertThat(afterClear).isNull()
        assertThat(TenantContext.getTenantId()).isEqualTo(id)
    }
}
