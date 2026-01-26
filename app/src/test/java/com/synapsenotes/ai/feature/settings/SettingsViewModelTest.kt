package com.synapsenotes.ai.feature.settings

import android.content.Context
import androidx.work.WorkManager
import com.synapsenotes.ai.core.ai.HardwareCapabilityProvider
import com.synapsenotes.ai.core.ai.LlmEngine
import com.synapsenotes.ai.core.ai.ModelManager
import com.synapsenotes.ai.core.preferences.AppPreferences
import com.synapsenotes.ai.domain.repository.NoteRepository
import com.synapsenotes.ai.test.CoroutineTestExtension
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val coroutineExtension = CoroutineTestExtension()

    private lateinit var viewModel: SettingsViewModel
    private val context: Context = mockk(relaxed = true)
    private val modelManager: ModelManager = mockk(relaxed = true)
    private val llmEngine: LlmEngine = mockk(relaxed = true)
    private val hardwareCapabilityProvider: HardwareCapabilityProvider = mockk(relaxed = true)
    private val googleSignInClient: GoogleSignInClient = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    // Using explicit type or dynamic mock if class not accessible, but it should be
    private val driveRepository: com.synapsenotes.ai.core.data.repository.GoogleDriveRepository = mockk(relaxed = true)
    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(WorkManager::class)
        mockkStatic(GoogleSignIn::class)
        every { WorkManager.getInstance(any()) } returns workManager
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null
        every { GoogleSignIn.hasPermissions(any(), *anyVararg()) } returns false
        
        // Mock preferences
        every { appPreferences.activeChatModelId } returns null
        every { appPreferences.activeEmbeddingModelId } returns null
        every { appPreferences.lastSyncTimestamp } returns 0L

        viewModel = SettingsViewModel(
            context,
            modelManager,
            llmEngine,
            hardwareCapabilityProvider,
            googleSignInClient,
            appPreferences,
            driveRepository,
            noteRepository,
            workManager
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial state contains default models`() = runTest {
        val state = viewModel.uiState.first()
        assertTrue(state.chatModels.isNotEmpty())
        assertTrue(state.embeddingModels.isNotEmpty())
    }
}
