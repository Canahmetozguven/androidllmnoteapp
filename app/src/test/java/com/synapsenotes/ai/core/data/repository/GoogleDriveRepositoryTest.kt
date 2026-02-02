package com.synapsenotes.ai.core.data.repository

import android.content.Context
import com.synapsenotes.ai.domain.repository.DriveError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private lateinit var repository: GoogleDriveRepository

    @BeforeEach
    fun setup() {
        mockkStatic(GoogleSignIn::class)
        repository = GoogleDriveRepository(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `listFiles throws NotSignedIn when no account found`() = runTest {
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null

        try {
            repository.listFiles()
            org.junit.jupiter.api.Assertions.fail("Expected DriveError.NotSignedIn")
        } catch (e: DriveError.NotSignedIn) {
            // Success
        }
    }

    @Test
    fun `listFiles throws AccountMissing when account has no android account`() = runTest {
        val account = mockk<GoogleSignInAccount>()
        every { account.account } returns null
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns account

        try {
            repository.listFiles()
            org.junit.jupiter.api.Assertions.fail("Expected DriveError.AccountMissing")
        } catch (e: DriveError.AccountMissing) {
            // Success
        }
    }

    @Test
    fun `downloadFile returns failure Result when not signed in`() = runTest {
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null

        val result = repository.downloadFile("file123", "text/plain")
        
        org.junit.jupiter.api.Assertions.assertTrue(result.isFailure, "Expected Result.failure when not signed in")
        val exception = result.exceptionOrNull()
        org.junit.jupiter.api.Assertions.assertNotNull(exception, "Exception should not be null")
        org.junit.jupiter.api.Assertions.assertTrue(
            exception is DriveError.NotSignedIn,
            "Expected DriveError.NotSignedIn but got ${exception?.javaClass?.simpleName}"
        )
    }

    @Test
    fun `downloadFile returns failure Result when account missing`() = runTest {
        val account = mockk<GoogleSignInAccount>()
        every { account.account } returns null
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns account

        val result = repository.downloadFile("file123", "text/plain")
        
        org.junit.jupiter.api.Assertions.assertTrue(result.isFailure, "Expected Result.failure when account missing")
        val exception = result.exceptionOrNull()
        org.junit.jupiter.api.Assertions.assertNotNull(exception, "Exception should not be null")
        org.junit.jupiter.api.Assertions.assertTrue(
            exception is DriveError.AccountMissing,
            "Expected DriveError.AccountMissing but got ${exception?.javaClass?.simpleName}"
        )
    }
}
