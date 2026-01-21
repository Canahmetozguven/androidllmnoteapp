package com.example.llmnotes.core.data.repository

import android.content.Context
import com.example.llmnotes.domain.repository.DriveFile
import com.example.llmnotes.domain.repository.DriveRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GoogleDriveRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : DriveRepository {

    override fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("LLM Notes")
        .build()
    }

    override suspend fun listFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext emptyList()
        
        try {
            val result = service.files().list()
                .setPageSize(100)
                .setFields("nextPageToken, files(id, name, mimeType, size, createdTime)")
                .execute()
                
            result.files.map { file ->
                DriveFile(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType,
                    size = file.getSize(),
                    createdTime = file.createdTime?.value
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun downloadFile(fileId: String, mimeType: String): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext null
        try {
            val outputStream = java.io.ByteArrayOutputStream()
            if (mimeType.startsWith("application/vnd.google-apps.")) {
                // Export Google Docs to plain text
                service.files().export(fileId, "text/plain")
                    .executeMediaAndDownloadTo(outputStream)
            } else {
                // Download binary files (txt, md, etc.)
                service.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream)
            }
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
