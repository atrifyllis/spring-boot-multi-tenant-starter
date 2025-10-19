package io.github.atrifyllis.multitenancy.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.*
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView

private val log = KotlinLogging.logger {}

/**
 * Migrated as-is from core: extracts tenantId list claim from JwtAuthenticationToken, optionally
 * checks X-Tenant-ID header, sets TenantContext, and clears after request.
 */
class TenantInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.let {
            jwtAuthenticationToken ->
            log.info { "TenantInterceptor: ${jwtAuthenticationToken.token}" }
            log.info { "TenantInterceptor: ${jwtAuthenticationToken.token.claims["tenantId"]}" }
            (jwtAuthenticationToken.token.claims["tenantId"] as? List<*>)?.let { tenantIds ->
                val headerTenantId = request.getHeader("X-Tenant-ID")
                resolveCurrentTenantId(tenantIds, headerTenantId)?.let {
                    TenantContext.setTenantId(it)
                }
            }
        }
        return true
    }

    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?,
    ) {
        TenantContext.clear()
    }

    private fun resolveCurrentTenantId(tenantIds: List<*>, headerTenantId: String?): UUID? {
        headerTenantId?.let {
            return tenantIds
                .find { it.toString() == headerTenantId }
                ?.let { UUID.fromString(it.toString()) }
                ?: throw TenantIdsDoNotMatchException(
                    "Tenant ID not found in the JWT token: $headerTenantId"
                )
        }
        return tenantIds.firstOrNull()?.let { UUID.fromString(it.toString()) }
    }
}

class TenantIdsDoNotMatchException(message: String, ex: Exception? = null) :
    RuntimeException(message, ex)
