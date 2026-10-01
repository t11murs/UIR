package com.example.uir_android.data.repository.impl

import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedReviewCachePolicyTest {
    @Test
    fun `completed review cache is used only for network errors`() {
        assertTrue(
            AppResult.Error("Нет сети", type = AppErrorType.NETWORK)
                .canUseCompletedReviewCache()
        )
        assertFalse(
            AppResult.Error("Сессия истекла", type = AppErrorType.AUTHENTICATION)
                .canUseCompletedReviewCache()
        )
        assertFalse(
            AppResult.Error("Нет доступа", type = AppErrorType.AUTHORIZATION)
                .canUseCompletedReviewCache()
        )
        assertFalse(
            AppResult.Error("Ошибка сервера", type = AppErrorType.SERVER)
                .canUseCompletedReviewCache()
        )
        assertFalse(AppResult.Error("Неизвестная ошибка").canUseCompletedReviewCache())
    }
}
