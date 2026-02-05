package com.synapsenotes.ai.core.data.repository

import android.content.Context
import com.synapsenotes.ai.domain.repository.DriveError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.drive.Drive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

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

    @Test
    fun `downloadFile returns failure Result with error details when Drive API throws exception`() = runTest {
        // Setup: Valid account that will pass auth check
        val account = mockk<GoogleSignInAccount>()
        val androidAccount = mockk<android.accounts.Account>()
        every { account.account } returns androidAccount
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns account
        
        // Mock Drive service chain to throw IOException
        val mockDrive = mockk<Drive>(relaxed = true)
        val mockFiles = mockk<Drive.Files>(relaxed = true)
        val mockGet = mockk<Drive.Files.Get>(relaxed = true)
        
        every { mockDrive.files() } returns mockFiles
        every { mockFiles.get(any()) } returns mockGet
        every { mockGet.executeMediaAndDownloadTo(any()) } throws IOException("Network unreachable")
        
        // Spy on repository and mock getDriveService to return mock Drive
        val spyRepository = spyk(repository)
        every { spyRepository["getDriveService"]() } returns mockDrive
        
        val result = spyRepository.downloadFile("file123", "text/plain")
        
        // Verify: Should be failure with error details, NOT null
        org.junit.jupiter.api.Assertions.assertTrue(result.isFailure, "Expected Result.failure when API throws exception")
        val exception = result.exceptionOrNull()
        org.junit.jupiter.api.Assertions.assertNotNull(exception, "Exception should not be null - error must be surfaced")
        org.junit.jupiter.api.Assertions.assertTrue(
            exception is DriveError.UnknownError,
            "Expected DriveError.UnknownError but got ${exception?.javaClass?.simpleName}"
        )
        org.junit.jupiter.api.Assertions.assertTrue(
            exception?.message?.contains("Network unreachable") == true,
            "Error message should preserve original exception details, got: ${exception?.message}"
        )
    }
}
