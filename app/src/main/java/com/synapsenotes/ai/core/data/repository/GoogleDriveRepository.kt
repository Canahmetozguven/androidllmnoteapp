package com.synapsenotes.ai.core.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer
import com.synapsenotes.ai.BuildConfig
import com.synapsenotes.ai.domain.repository.DriveError
import com.synapsenotes.ai.domain.repository.DriveFile
import com.synapsenotes.ai.domain.repository.DriveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GoogleDriveRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : DriveRepository {

    override fun isSignedIn(): Boolean {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        return googleAccount != null && googleAccount.account != null
    }

    private fun getDriveService(): Drive {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw DriveError.NotSignedIn
        
        val androidAccount = googleAccount.account
            ?: throw DriveError.AccountMissing
        
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = androidAccount

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("LLM Notes")
        .build()
    }

    override suspend fun listFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        val service = getDriveService()
        
        try {
            val result = service.files().list()
                .setQ("trashed = false")
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
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            android.util.Log.e("GoogleDriveRepository", "List files failed (JSON): ${e.details}", e)
            throw handleGoogleError(e)
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveRepository", "List files failed: ${e.javaClass.name}: ${e.message}", e)
            throw DriveError.UnknownError(e.message ?: "Unknown error", e)
        }
    }

    private fun handleGoogleError(e: com.google.api.client.googleapis.json.GoogleJsonResponseException): DriveError {
        val details = e.details?.message ?: e.message ?: "No details"
        return when (e.statusCode) {
            403 -> {
                if (details.contains("SERVICE_DISABLED", ignoreCase = true) || 
                    details.contains("API has not been used", ignoreCase = true)) {
                    DriveError.ServiceDisabled(details)
                } else if (details.contains("rateLimitExceeded", ignoreCase = true) || 
                           details.contains("quotaExceeded", ignoreCase = true)) {
                    DriveError.QuotaExceeded(details)
                } else {
                    DriveError.NetworkError("Forbidden: $details")
                }
            }
            else -> DriveError.UnknownError(details, e)
        }
    }

    override suspend fun downloadFile(fileId: String, mimeType: String): String? = withContext(Dispatchers.IO) {
        val service = try { 
            getDriveService() 
        } catch (e: Exception) { 
            android.util.Log.e("GoogleDriveRepository", "Download failed: Auth error", e)
            return@withContext null 
        }
        
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
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            android.util.Log.e("GoogleDriveRepository", "Download failed (JSON): ${e.details}", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveRepository", "Download failed: ${e.message}", e)
            null
        }
    }

    override suspend fun uploadFile(name: String, content: String): String? = withContext(Dispatchers.IO) {
        val service = try { 
            getDriveService() 
        } catch (e: Exception) { 
            android.util.Log.e("GoogleDriveRepository", "Upload failed: Auth error", e)
            return@withContext null 
        }
        
        try {
            // 1. Find or create folder
            val folderId = getOrCreateFolder(service, "LLM Notes Backup") ?: return@withContext null

            // 2. Check if file exists
            val existingFile = service.files().list()
                .setQ("name = '$name' and '$folderId' in parents and trashed = false")
                .setFields("files(id)")
                .execute()
                .files
                .firstOrNull()

            val fileMetadata = File().apply {
                this.name = name
                if (existingFile == null) {
                    parents = listOf(folderId)
                }
            }
            
            val mediaContent = ByteArrayContent.fromString("text/plain", content)

            val file = if (existingFile != null) {
                service.files().update(existingFile.id, fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            } else {
                service.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            }
            file.id
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            android.util.Log.e("GoogleDriveRepository", "Upload failed (JSON): ${e.details}", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveRepository", "Upload failed: ${e.message}", e)
            null
        }
    }

    private fun getOrCreateFolder(service: Drive, folderName: String): String? {
        try {
            val result = service.files().list()
                .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false")
                .setFields("files(id)")
                .execute()
            
            val folder = result.files.firstOrNull()
            if (folder != null) return folder.id

            val fileMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
            }
            
            val newFolder = service.files().create(fileMetadata)
                .setFields("id")
                .execute()
            return newFolder.id
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            android.util.Log.e("GoogleDriveRepository", "Folder creation failed (JSON): ${e.details}", e)
            return null
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveRepository", "Folder creation failed: ${e.message}", e)
            return null
        }
    }
}
