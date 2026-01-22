package com.example.llmnotes.domain.repository

import kotlinx.coroutines.flow.Flow

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val createdTime: Long?
)

interface DriveRepository {
    fun isSignedIn(): Boolean
    suspend fun listFiles(): List<DriveFile>
    suspend fun downloadFile(fileId: String, mimeType: String): String?
    suspend fun uploadFile(name: String, content: String): String?
}
