package com.example.jarvis

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val PROGRESS_PERCENT = "progress_percent"
        const val PROGRESS_BYTES = "progress_bytes"
        const val TOTAL_BYTES = "total_bytes"
        const val STATUS_MSG = "status_msg"

        // Example Hugging Face URL. We use a placeholder here, but it should point to a raw file.
        // For actual production use, ensure this points to the right `.task` or `.bin` quantized format.
        private const val MODEL_URL = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it-cpu-int4.bin"
        private const val FILE_NAME = "gemma.bin"
        private const val TAG = "ModelDownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetFile = File(applicationContext.filesDir, FILE_NAME)

        try {
            reportProgress(0, 0, 0, "Starting download...")
            Log.d(TAG, "Connecting to $MODEL_URL")

            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = "Server returned HTTP ${connection.responseCode} ${connection.responseMessage}"
                Log.e(TAG, errorMsg)
                reportProgress(0, 0, 0, "Error: $errorMsg")
                return@withContext Result.failure(workDataOf(STATUS_MSG to errorMsg))
            }

            val fileLength = connection.contentLengthLong

            // Storage check
            if (!hasEnoughSpace(fileLength)) {
                val errorMsg = "Not enough storage space. Required: ${formatBytes(fileLength)}"
                Log.e(TAG, errorMsg)
                reportProgress(0, 0, 0, errorMsg)
                return@withContext Result.failure(workDataOf(STATUS_MSG to errorMsg))
            }

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(targetFile)

            val data = ByteArray(8192) // 8KB buffer
            var totalBytesRead: Long = 0
            var bytesRead: Int
            var lastUpdatePercent = 0

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(data).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            Log.w(TAG, "Download stopped by user/system")
                            targetFile.delete() // Cleanup partial download
                            return@withContext Result.failure()
                        }

                        totalBytesRead += bytesRead
                        output.write(data, 0, bytesRead)

                        // Update progress UI (throttle to avoid overwhelming the system)
                        if (fileLength > 0) {
                            val percent = ((totalBytesRead * 100) / fileLength).toInt()
                            if (percent > lastUpdatePercent) { // update every 1%
                                lastUpdatePercent = percent
                                reportProgress(percent, totalBytesRead, fileLength, "Downloading...")
                            }
                        }
                    }
                }
            }

            // Integrity Check
            if (fileLength > 0 && targetFile.length() != fileLength) {
                Log.e(TAG, "File size mismatch. Expected $fileLength but got ${targetFile.length()}")
                targetFile.delete()
                return@withContext Result.failure(workDataOf(STATUS_MSG to "File download corrupted."))
            }

            Log.i(TAG, "Download complete!")
            reportProgress(100, fileLength, fileLength, "Download Complete")
            return@withContext Result.success(workDataOf("file_path" to targetFile.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            targetFile.delete() // Clean up on failure
            val errorMsg = e.localizedMessage ?: "Unknown error"
            reportProgress(0, 0, 0, "Error: $errorMsg")
            return@withContext Result.failure(workDataOf(STATUS_MSG to errorMsg))
        }
    }

    private suspend fun reportProgress(percent: Int, bytesRead: Long, totalBytes: Long, status: String) {
        val progressData = workDataOf(
            PROGRESS_PERCENT to percent,
            PROGRESS_BYTES to bytesRead,
            TOTAL_BYTES to totalBytes,
            STATUS_MSG to status
        )
        setProgress(progressData)
    }

    private fun hasEnoughSpace(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) return true // Unknown size, skip check
        val statFs = StatFs(applicationContext.filesDir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        // Keep a small buffer (e.g., 50MB)
        return availableBytes > (requiredBytes + 50 * 1024 * 1024)
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024.0)
        return String.format("%.2f MB", mb)
    }
}
