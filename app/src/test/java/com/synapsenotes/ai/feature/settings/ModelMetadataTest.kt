package com.synapsenotes.ai.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD Test for AVAILABLE_MODELS metadata validation.
 * 
 * Ensures that chat models (especially those requiring RAG) are properly
 * configured with correct URLs, filenames, and type information.
 */
class ModelMetadataTest {

    @Test
    fun `AVAILABLE_MODELS contains lfm2-1_2b model with correct URL`() {
        // Arrange
        val modelId = "lfm2-1.2b"
        val expectedUrl = "https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf"
        
        // Act
        val model = AVAILABLE_MODELS.find { it.id == modelId }
        
        // Assert
        assertNotNull(model, "Model with ID '$modelId' should exist in AVAILABLE_MODELS")
        assertEquals(expectedUrl, model!!.url, "Model URL should match HuggingFace download link")
    }

    @Test
    fun `AVAILABLE_MODELS lfm2-1_2b model has correct filename`() {
        // Arrange
        val modelId = "lfm2-1.2b"
        val expectedFilename = "LFM2-1.2B-Q4_K_M.gguf"
        
        // Act
        val model = AVAILABLE_MODELS.find { it.id == modelId }
        
        // Assert
        assertNotNull(model, "Model with ID '$modelId' should exist in AVAILABLE_MODELS")
        assertEquals(expectedFilename, model!!.filename, "Model filename should match expected GGUF format")
    }

    @Test
    fun `AVAILABLE_MODELS lfm2-1_2b is chat model type`() {
        // Arrange
        val modelId = "lfm2-1.2b"
        
        // Act
        val model = AVAILABLE_MODELS.find { it.id == modelId }
        
        // Assert
        assertNotNull(model, "Model with ID '$modelId' should exist in AVAILABLE_MODELS")
        assertEquals(ModelType.CHAT, model!!.type, "Model should be of CHAT type")
    }

    @Test
    fun `AVAILABLE_MODELS Liquid LFM2 variant does NOT require RAG`() {
        // Arrange
        val liquidModelId = "lfm2-1.2b"
        
        // Act
        val liquidModel = AVAILABLE_MODELS.find { it.id == liquidModelId }
        
        // Assert
        assertNotNull(liquidModel, "Liquid LFM2 variant should exist")
        assertEquals(false, liquidModel!!.requiresRag, "Liquid LFM2 is a non-RAG generalist model, should NOT require RAG")
    }

    @Test
    fun `all AVAILABLE_MODELS have valid metadata`() {
        // Arrange
        val requiredFields = listOf("id", "name", "url", "filename")
        
        // Act & Assert
        AVAILABLE_MODELS.forEach { model ->
            assertTrue(model.id.isNotBlank(), "Model ID must not be blank")
            assertTrue(model.name.isNotBlank(), "Model name must not be blank")
            assertTrue(model.url.isNotBlank(), "Model URL must not be blank")
            assertTrue(model.filename.isNotBlank(), "Model filename must not be blank")
            assertTrue(model.url.startsWith("https://"), "Model URL must be HTTPS")
        }
    }

    @Test
    fun `AVAILABLE_MODELS embedding models are distinct from chat models`() {
        // Act
        val chatModels = AVAILABLE_MODELS.filter { it.type == ModelType.CHAT }
        val embeddingModels = AVAILABLE_MODELS.filter { it.type == ModelType.EMBEDDING }
        
        // Assert
        assertTrue(chatModels.isNotEmpty(), "Should have at least one chat model")
        assertTrue(embeddingModels.isNotEmpty(), "Should have at least one embedding model")
        
        chatModels.forEach { chatModel ->
            assertTrue(embeddingModels.none { it.id == chatModel.id },
                "Chat model ${chatModel.id} should not also be an embedding model")
        }
    }
}
