package com.example.uir_android.domain.usecase

import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseProgramUseCaseTest {
    @Test
    fun `parsing is dispatched to computation dispatcher`() = runBlocking {
        val computationDispatcher = RecordingDispatcher()
        val useCase = ParseProgramUseCase(
            AppDispatchers(
                io = Dispatchers.Unconfined,
                default = computationDispatcher,
                main = Dispatchers.Unconfined
            )
        )

        val result = useCase("S0 a -> b R HALT")

        assertTrue(result is AppResult.Success)
        assertEquals(1, computationDispatcher.dispatchCount.get())
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        val dispatchCount = AtomicInteger()

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount.incrementAndGet()
            block.run()
        }
    }
}
