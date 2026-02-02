package com.synapsenotes.ai.core.network

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val urlString = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val rawFileName = inputData.getString(KEY_FILENAME) ?: return@withContext Result.failure()
        
        // Sanitize filename to prevent path traversal
        val fileName = File(rawFileName).name

        setForeground(createForegroundInfo(fileName, 0))
        
        val modelsDir = File(applicationContext.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        val file = File(modelsDir, fileName)

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure()
            }
            
            val length = connection.contentLength
            
            connection.inputStream.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgress = 0
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (length > 0) {
                            val progress = (totalBytesRead * 100 / length).toInt()
                            // Update progress only if changed to reduce overhead
                            if (progress != lastProgress) {
                                lastProgress = progress
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                                setForeground(createForegroundInfo(fileName, progress))
                            }
                        }
                    }
                    outputStream.flush()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun createForegroundInfo(fileName: String, progress: Int): ForegroundInfo {
        val id = "model_download_channel"
        val title = "Downloading $fileName"
        val cancel = "Cancel"
        
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(id)
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                fileName.hashCode(), 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(fileName.hashCode(), notification)
        }
    }

    private fun createChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Model Downloads"
            val descriptionText = "Notifications for model download progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILENAME = "filename"
        const val KEY_PROGRESS = "progress"
    }
}
