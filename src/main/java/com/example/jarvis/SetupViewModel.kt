package com.example.jarvis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import java.util.UUID

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    var currentWorkId: UUID? = null
        private set

    fun isModelAlreadyDownloaded(): Boolean {
        val modelFile = File(getApplication<Application>().filesDir, "gemma.bin")
        return modelFile.exists() && modelFile.length() > 0
    }

    fun startModelDownload(): LiveData<WorkInfo> {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .build()

        currentWorkId = downloadRequest.id
        workManager.enqueue(downloadRequest)

        return workManager.getWorkInfoByIdLiveData(downloadRequest.id)
    }
}
