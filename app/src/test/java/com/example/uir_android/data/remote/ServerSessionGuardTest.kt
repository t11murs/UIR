package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSessionGuardTest {
    @Test
    fun `401 expires session`() {
        assertEquals(
            ServerAccessFailure.SESSION_EXPIRED,
            classifyServerAccessFailure(401, "/api/mobile/tests", "{}")
        )
    }

    @Test
    fun `redirected login page expires session`() {
        assertEquals(
            ServerAccessFailure.SESSION_EXPIRED,
            classifyServerAccessFailure(200, "/auth/login", "<form id=\"login-form\"></form>")
        )
    }

    @Test
    fun `authorization no-access page expires session`() {
        val html = """
            <body class="full-no_access">
              <h2>Требуется авторизация</h2>
            </body>
        """.trimIndent()

        assertEquals(
            ServerAccessFailure.SESSION_EXPIRED,
            classifyServerAccessFailure(200, "/no-access", html)
        )
    }

    @Test
    fun `role no-access page does not expire session`() {
        val html = """
            <body class="full-no_access">
              <h2>Доступ разрешён только преподавателю</h2>
            </body>
        """.trimIndent()

        assertEquals(
            ServerAccessFailure.ACCESS_DENIED,
            classifyServerAccessFailure(200, "/no-access", html)
        )
    }

    @Test
    fun `json 403 response is access denied`() {
        assertEquals(
            ServerAccessFailure.ACCESS_DENIED,
            classifyServerAccessFailure(403, "/api/mobile/tests/7", "{\"success\":false}")
        )
    }

    @Test
    fun `test api status codes have stable error types`() {
        assertEquals(AppErrorType.AUTHENTICATION, classifyHttpErrorType(401))
        assertEquals(AppErrorType.AUTHORIZATION, classifyHttpErrorType(403))
        assertEquals(AppErrorType.DATA, classifyHttpErrorType(409))
        assertEquals(AppErrorType.SERVER, classifyHttpErrorType(429))
        assertEquals(AppErrorType.SERVER, classifyHttpErrorType(500))
        assertEquals(AppErrorType.SERVER, classifyHttpErrorType(200))
    }

    @Test
    fun `successful json has no access failure`() {
        assertNull(classifyServerAccessFailure(200, "/api/mobile/tests", "{\"success\":true}"))
    }
}
