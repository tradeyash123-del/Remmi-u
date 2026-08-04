package com.example.jarvis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import com.example.jarvis.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip setup if model is already downloaded
        if (viewModel.isModelAlreadyDownloaded()) {
            navigateToMain()
            return
        }

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDownload.setOnClickListener {
            startDownload()
        }
    }

    private fun startDownload() {
        binding.btnDownload.isEnabled = false
        binding.llProgressContainer.visibility = View.VISIBLE

        viewModel.startModelDownload().observe(this, Observer { workInfo ->
            if (workInfo != null) {
                val progress = workInfo.progress
                val percent = progress.getInt(ModelDownloadWorker.PROGRESS_PERCENT, 0)
                val bytesRead = progress.getLong(ModelDownloadWorker.PROGRESS_BYTES, 0)
                val totalBytes = progress.getLong(ModelDownloadWorker.TOTAL_BYTES, 0)
                val statusMsg = progress.getString(ModelDownloadWorker.STATUS_MSG) ?: "Downloading..."

                binding.progressBar.progress = percent
                binding.tvPercentage.text = "$percent%"
                binding.tvBytes.text = "${formatBytes(bytesRead)} / ${formatBytes(totalBytes)}"
                binding.tvStatus.text = statusMsg

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        Toast.makeText(this, "Model downloaded successfully!", Toast.LENGTH_LONG).show()
                        navigateToMain()
                    }
                    WorkInfo.State.FAILED -> {
                        binding.btnDownload.isEnabled = true
                        val error = workInfo.outputData.getString(ModelDownloadWorker.STATUS_MSG) ?: "Download failed."
                        binding.tvStatus.text = error
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                    WorkInfo.State.CANCELLED -> {
                        binding.btnDownload.isEnabled = true
                        binding.tvStatus.text = "Download cancelled."
                    }
                    else -> {
                        // Running, Enqueued, etc.
                    }
                }
            }
        })
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes == 0L) return "0 MB"
        val mb = bytes / (1024 * 1024.0)
        return String.format("%.2f MB", mb)
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish() // Prevent returning to setup
    }
}
