package com.synapsenotes.ai.feature.chat

import com.synapsenotes.ai.core.ai.HardwareInfo
import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.domain.repository.ChatRepository
import com.synapsenotes.ai.domain.repository.NoteRepository
import com.synapsenotes.ai.domain.usecase.VectorSearchUseCase
import com.synapsenotes.ai.test.CoroutineTestExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @JvmField
    @RegisterExtension
    val coroutineExtension = CoroutineTestExtension()

    private lateinit var viewModel: ChatViewModel
    private val vectorSearchUseCase: VectorSearchUseCase = mockk(relaxed = true)
    private val llmEngine: LlmEngine = mockk(relaxed = true)
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val noteRepository: NoteRepository = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        val hwInfo = HardwareInfo(isGpuAccelerationEnabled = true, backendName = "OPENCL", gpuName = "Test GPU")
        every { llmEngine.getHardwareInfo() } returns hwInfo
        every { llmEngine.isGpuEnabled() } returns true

        viewModel = ChatViewModel(
            vectorSearchUseCase,
            llmEngine,
            chatRepository,
            noteRepository
        )
    }

    @Test
    fun `hardwareInfo flow returns correct value`() = runTest {
        val hwInfo = viewModel.hardwareInfo.first()
        assertEquals("OPENCL", hwInfo.backendName)
        assertEquals(true, hwInfo.isGpuAccelerationEnabled)
    }
}
