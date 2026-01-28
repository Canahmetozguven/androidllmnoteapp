package com.synapsenotes.ai.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.synapsenotes.ai.domain.repository.DriveError
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleDriveRepositoryInstrumentationTest {

    @Test
    fun testContextIsAvailable() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(appContext)
    }

    @Test
    fun testRepositoryInitialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = GoogleDriveRepository(appContext)
        assertNotNull(repository)
    }
}
